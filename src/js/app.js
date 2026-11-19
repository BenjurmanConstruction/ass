/* Sable — app shell: transport, keyboard, files, export, tabs */
(function () {
  'use strict';
  const DAW = window.DAW, E = DAW.engine, S = DAW.state, $ = (id) => document.getElementById(id);
  const native = window.native || null;

  /* ---------- transport ---------- */
  const T = (DAW.transport = {
    pos: 0,
    beat() { return E.playing ? E.currentBeat() : this.pos; },
    play() { E.play(this.pos); S.playing = true; syncTop(); },
    stop() { if (E.playing) { E.stop(); S.playing = false; } else this.pos = 0; syncTop(); },
    toggle() { if (E.playing) this.stop(); else this.play(); },
    seek(b) { this.pos = Math.max(0, b); if (E.playing) E.play(this.pos); },
  });

  let lastBeat = -1, nowSig = '', nowDirty = 1;
  DAW.on('change', () => { nowDirty = Math.random(); });
  function frame() {
    const b = T.beat();
    if (b !== lastBeat) {
      lastBeat = b;
      const bar = Math.floor(b / 4) + 1, beat = Math.floor(b % 4) + 1, six = Math.floor((b % 1) * 4) + 1, sec = (b * 60) / S.project.bpm;
      $('posBars').textContent = bar + '.' + beat + '.' + six;
      $('posTime').textContent = Math.floor(sec / 60) + ':' + (sec % 60).toFixed(1).padStart(4, '0');
      DAW.arrange.setPlayhead(b, E.playing);
      if (S.tab === 'piano') DAW.pianoroll.draw();
    }
    // now-playing readout: what you are holding down, otherwise what the selected track plays at the playhead
    const heldP = []; for (const hnd of held.values()) if (hnd.pitches && !hnd.drum) heldP.push(...hnd.pitches);
    const sig = b + '|' + heldP.join(',') + '|' + nowDirty;
    if (sig !== nowSig) {
      nowSig = sig; nowDirty = 0;
      const key = S.project.key, sel = DAW.selTrack();
      document.querySelectorAll('.tnow').forEach((el) => { const t = DAW.track(el.dataset.id), txt = t ? DAW.describe(DAW.notesAt(t, b), key).split('  ·  ')[0] : ''; if (el.textContent !== txt) el.textContent = txt; });
      const txt = heldP.length ? DAW.describe(heldP, key) : sel && sel.type === 'synth' ? DAW.describe(DAW.notesAt(sel, b), key) : '';
      const now = $('kbdNow'); if (now) { const parts = txt.split('  ·  '); now.firstChild.textContent = parts[0] || '—'; now.lastChild.textContent = parts[1] || ''; }
    }
    DAW.mixer.tick();
    requestAnimationFrame(frame);
  }

  /* ---------- top bar ---------- */
  function displayName() { return S.filePath ? S.filePath.split(/[\\/]/).pop().replace(/\.sable$/i, '') : S.project.name || 'Untitled'; }
  function syncTop() {
    const p = S.project;
    $('btnPlay').classList.toggle('on', S.playing); $('btnRec').classList.toggle('on', S.recording);
    $('btnLoop').classList.toggle('on', p.loop.on); $('btnMetro').classList.toggle('on', S.metronome);
    if (document.activeElement !== $('bpm')) $('bpm').value = p.bpm;
    $('swing').value = p.swing; $('swingVal').textContent = Math.round(p.swing * 100) + '%';
    $('btnUndo').disabled = !DAW.history.canUndo(); $('btnRedo').disabled = !DAW.history.canRedo();
    const title = displayName() + (S.dirty ? ' •' : '');
    $('projName').textContent = title; document.title = title + ' — Sable';
    if (native) native.setState({ dirty: S.dirty, title: document.title });
  }
  function initTop() {
    $('btnPlay').onclick = () => T.toggle();
    $('btnStop').onclick = () => T.stop();
    $('btnRec').onclick = () => { S.recording = !S.recording; syncTop(); if (S.recording) DAW.toast('Record armed: press Play, then play notes on your keyboard'); };
    $('btnLoop').onclick = () => { S.project.loop.on = !S.project.loop.on; DAW.commit(); };
    $('btnMetro').onclick = () => { S.metronome = !S.metronome; syncTop(); };
    $('bpm').addEventListener('change', () => { S.project.bpm = DAW.clamp(Math.round(+$('bpm').value * 10) / 10 || 120, 40, 260); $('bpm').blur(); DAW.commit(); });
    $('bpm').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('bpm').blur(); });
    $('swing').addEventListener('input', () => { S.project.swing = +$('swing').value; $('swingVal').textContent = Math.round(S.project.swing * 100) + '%'; });
    $('swing').addEventListener('change', () => DAW.commit());
    const snap = $('snap');
    DAW.SNAPS.forEach(([label, v]) => snap.append(DAW.h('option', { value: v }, label)));
    snap.value = S.snap;
    snap.addEventListener('change', () => { S.snap = +snap.value; snap.blur(); DAW.pianoroll.refresh(); });
    $('btnUndo').onclick = () => DAW.history.undo(); $('btnRedo').onclick = () => DAW.history.redo();
    $('btnNew').onclick = newProject; $('btnOpen').onclick = openProject; $('btnSave').onclick = () => save(false); $('btnExport').onclick = openExport;
    DAW.mixer.addFixedMeter($('masterFill'), () => E.graph && E.graph.analyser);
    DAW.on('change', syncTop);
  }

  /* ---------- tabs + splitter ---------- */
  DAW.setTab = function (name) {
    S.tab = name;
    document.querySelectorAll('#tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === name));
    for (const [k, id] of [['piano', 'tabPiano'], ['inst', 'tabInst'], ['mixer', 'tabMixer']]) $(id).classList.toggle('hidden', k !== name);
    if (name === 'piano') DAW.pianoroll.refresh();
  };
  function initLayout() {
    document.querySelectorAll('#tabs button').forEach((b) => (b.onclick = () => DAW.setTab(b.dataset.tab)));
    $('splitter').addEventListener('pointerdown', (e) => {
      e.preventDefault();
      const start = e.clientY, h0 = $('bottom').getBoundingClientRect().height;
      DAW.drag(e, (ev) => { $('bottom').style.height = DAW.clamp(h0 + start - ev.clientY, 180, window.innerHeight - 220) + 'px'; });
    });
    // keep keyboard shortcuts working after clicking a button or slider
    document.addEventListener('pointerup', () => setTimeout(() => { const a = document.activeElement; if (a && (a.tagName === 'BUTTON' || a.type === 'range')) a.blur(); }, 0));
  }

  /* ---------- files ---------- */
  function download(blob, name) {
    const a = DAW.h('a', { href: URL.createObjectURL(blob), download: name });
    document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }
  function pickFile() {
    return new Promise((res) => {
      const inp = DAW.h('input', { type: 'file', accept: '.sable,application/json' });
      inp.onchange = () => { const f = inp.files[0]; if (!f) return res(null); f.text().then((data) => res({ path: null, data, name: f.name })); };
      inp.click();
    });
  }
  const okToDiscard = () => !S.dirty || confirm('You have unsaved changes. Discard them?');
  async function save(saveAs) {
    const data = JSON.stringify(S.project);
    try {
      if (native) {
        const path = await native.save({ path: S.filePath, saveAs: !!saveAs, data, name: displayName() });
        if (!path) return false;
        S.filePath = path;
      } else download(new Blob([data], { type: 'application/json' }), displayName() + '.sable');
    } catch (err) { alert('Could not save the project:\n' + err.message); return false; }
    S.dirty = false; syncTop(); DAW.toast('Project saved');
    return true;
  }
  function load(p, path) {
    if (E.playing) T.stop();
    T.pos = 0; lastBeat = -1;
    DAW.setProject(p, path);
  }
  async function openProject() {
    if (!okToDiscard()) return;
    try {
      const res = native ? await native.open() : await pickFile();
      if (!res) return;
      load(DAW.normalizeProject(JSON.parse(res.data)), res.path);
      if (!res.path && res.name) S.project.name = res.name.replace(/\.sable$/i, '');
      syncTop();
    } catch (err) { alert('Could not open that project:\n' + err.message); }
  }
  function newProject() {
    if (!okToDiscard()) return;
    const p = DAW.emptyProject();
    p.tracks = [DAW.makeTrack('drums', 'Drums', 'Rock Kit', 0), DAW.makeTrack('synth', 'Grand Piano', 'Grand Piano', 1)];
    load(p, null);
  }

  /* ---------- export ---------- */
  function openExport() {
    const p = S.project, hasLoop = p.loop.end > p.loop.start;
    $('exLoop').disabled = !hasLoop; if (!hasLoop) $('exSong').checked = true;
    $('exStatus').textContent = ''; $('exGo').disabled = false;
    $('modal').classList.remove('hidden');
  }
  const closeExport = () => $('modal').classList.add('hidden');
  async function doExport() {
    const p = S.project, loop = $('exLoop').checked;
    const startBeat = loop ? p.loop.start : 0, endBeat = loop ? p.loop.end : DAW.songEnd();
    if (endBeat <= startBeat) { $('exStatus').textContent = 'Nothing to export: the song has no clips yet.'; return; }
    $('exGo').disabled = true; $('exStatus').textContent = 'Rendering…';
    await new Promise((r) => setTimeout(r, 40));
    try {
      const buf = await E.render({ startBeat, endBeat, sampleRate: +$('exRate').value, tail: $('exTail').checked ? 3 : 0 });
      const bytes = DAW.encodeWav(buf, +$('exBits').value), name = displayName() + '.wav';
      if (native) {
        const path = await native.exportWav({ name, bytes });
        if (path) DAW.toast('Exported to ' + path, 5000);
      } else { download(new Blob([bytes], { type: 'audio/wav' }), name); DAW.toast('Exported ' + name); }
      closeExport();
    } catch (err) { $('exStatus').textContent = 'Export failed: ' + err.message; $('exGo').disabled = false; }
  }
  DAW.exportBuffer = (o) => E.render(o); // used by tests

  /* ---------- computer keyboard: shortcuts + musical typing ---------- */
  const KEYMAP = { a: 0, w: 1, s: 2, e: 3, d: 4, f: 5, t: 6, g: 7, y: 8, h: 9, u: 10, j: 11, k: 12, o: 13, l: 14, p: 15, ';': 16, "'": 17, ']': 18, '\\': 19 };
  const ROW = ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';', "'", '\\']; // home row left to right = white keys / drum pads
  const BLACK = { w: 0, e: 1, t: 3, y: 4, u: 5, o: 7, p: 8, ']': 10 };        // black key -> white key it sits to the right of
  const held = new Map();
  let octave = 4, stepAt = 0, stepHeld = 0, keyEls = {};
  const stepLen = () => S.snap || 0.25;
  function recordNote(track, pitch, b0, b1, vel, quiet, select) {
    const snap = S.snap || 0;
    const start = snap ? Math.round(b0 / snap) * snap : b0;
    let len = b1 > b0 ? Math.max(snap || 0.125, b1 - b0) : snap || 0.25; // b1 < b0 means the loop wrapped mid-note
    let clip = track.clips.find((c) => start >= c.start && start < c.start + c.length);
    if (!clip) { // just past the end of a clip: grow that clip instead of starting lots of little ones
      const prev = track.clips.filter((c) => c.start + c.length <= start + 1e-6).sort((a, b) => b.start + b.length - (a.start + a.length))[0];
      if (prev && start - (prev.start + prev.length) < 4) {
        const next = Math.min(Infinity, ...track.clips.filter((c) => c.start > start).map((c) => c.start));
        const want = Math.min(Math.ceil((start + 0.001 - prev.start) / 4) * 4, next - prev.start);
        if (want > start - prev.start && DAW.clipFits(track, prev.start, want, prev)) { prev.length = want; clip = prev; }
      }
    }
    if (!clip) {
      let cs = Math.floor(start / 4) * 4, ce = cs + 4;
      for (const o of track.clips) { if (o.start + o.length <= start) cs = Math.max(cs, o.start + o.length); if (o.start > start) ce = Math.min(ce, o.start); }
      clip = DAW.makeClip(cs, ce - cs); track.clips.push(clip);
    }
    len = Math.min(len, clip.length - (start - clip.start));
    clip.notes.push(DAW.note(pitch, start - clip.start, len, vel));
    if (select || !S.selClipId) { S.selClipId = clip.id; S.selTrackId = track.id; }
    if (!quiet) DAW.commit();
  }
  /* which notes a computer key plays on the selected track (null = not a note key) */
  function keyPitches(track, k) {
    const i = ROW.indexOf(k);
    if (track.type === 'drums') return i < 0 ? null : [DAW.DRUM_BASE + i];
    if (S.chordKeys) return i < 0 || i > 7 ? null : DAW.chordNotes(S.project.key, i).map((p) => p + 12 * (octave - 4));
    return k in KEYMAP ? [12 * (octave + 1) + KEYMAP[k]] : null;
  }
  const light = (k, on, pitches) => { if (keyEls[k]) keyEls[k].classList.toggle('down', on); for (const p of pitches || []) if (keyEls['p' + p]) keyEls['p' + p].classList.toggle('down', on); };
  function pressKey(k, direct) { // direct: pitches from a mouse click on the on-screen keyboard
    if (held.has(k)) return false;
    const track = DAW.selTrack(); if (!track) return false;
    const pitches = direct || keyPitches(track, k); if (!pitches) return false;
    const drum = track.type === 'drums', rec = S.recording && E.playing, step = S.stepInput && !E.playing;
    const hnd = { drum, voices: pitches.map((p) => E.noteOn(track, p, drum ? 0.85 : 0.8)), pitches, trackId: track.id, b0: 0, rec: rec && !drum, step };
    held.set(k, hnd);
    if (rec) { hnd.b0 = E.currentBeat(); if (drum) recordNote(track, pitches[0], hnd.b0, hnd.b0 + stepLen(), 0.85); }
    if (step) { // write the note at the playhead; keys held together stack into a chord
      if (stepHeld === 0) stepAt = Math.max(0, DAW.snapRound(T.pos));
      stepHeld++;
      pitches.forEach((p) => recordNote(track, p, stepAt, stepAt + stepLen(), 0.8, true, true));
      DAW.commit(); DAW.pianoroll.reveal(pitches[0]);
    }
    light(k, true, pitches);
    return true;
  }
  function releaseKey(k) {
    const hnd = held.get(k); if (!hnd) return;
    held.delete(k); hnd.voices.forEach((v) => E.noteOff(v)); light(k, false, hnd.pitches);
    if (hnd.rec && E.playing) {
      const t = DAW.track(hnd.trackId), b1 = E.currentBeat();
      if (t) { hnd.pitches.forEach((p) => recordNote(t, p, hnd.b0, b1, 0.8, true)); DAW.commit(); }
    }
    if (hnd.step) { stepHeld = Math.max(0, stepHeld - 1); if (stepHeld === 0) { T.pos = stepAt + stepLen(); DAW.pianoroll.reveal(); } }
  }
  /* on-screen keyboard: shows which computer key plays what, and is clickable */
  let kbZoom = 1, kbScroll = 0, kbReveal = false; const kbChord = { root: -1, type: 0 };
  const CHORD_TYPES = [['major', [0, 4, 7]], ['minor', [0, 3, 7]], ['7', [0, 4, 7, 10]], ['major 7', [0, 4, 7, 11]], ['minor 7', [0, 3, 7, 10]], ['6', [0, 4, 7, 9]], ['minor 6', [0, 3, 7, 9]], ['9', [0, 4, 7, 10, 14]], ['add 9', [0, 4, 7, 14]], ['sus 2', [0, 2, 7]], ['sus 4', [0, 5, 7]], ['dim', [0, 3, 6]], ['dim 7', [0, 3, 6, 9]], ['aug', [0, 4, 8]], ['power (5)', [0, 7]]];
  function renderKbd() {
    const el = $('kbd'), track = DAW.selTrack(), h = DAW.h;
    el.innerHTML = ''; keyEls = {}; nowSig = '';
    if (!track) return;
    const drum = track.type === 'drums', cap = (k) => (k === '\\' ? '\\' : k.toUpperCase());
    const bind = (node, k) => { keyEls[k] = node; if (held.has(k)) node.classList.add('down'); node.addEventListener('pointerdown', (e) => { e.preventDefault(); if (pressKey(k)) DAW.drag(e, () => {}, () => releaseKey(k)); }); return node; };
    const write = h('button', { class: 'kbd-write' + (S.stepInput ? ' on' : ''), title: 'When on (and the song is stopped), every key you press is written into the clip at the playhead, which then steps forward by the Snap value. Hold keys together for a chord. Turn off to just play.', onclick: () => { S.stepInput = !S.stepInput; renderKbd(); } }, S.stepInput ? '✎ Writing notes: ON' : '✎ Writing notes: OFF');
    let keys, chordUi = null;
    if (drum || S.chordKeys) {
      const n = drum ? 12 : 8;
      keys = h('div', { class: 'kbd-pads' }, ROW.slice(0, n).map((k, i) => bind(h('div', { class: 'kbd-pad' }, h('b', {}, drum ? DAW.padName(track.instrument, i) : DAW.chordName(S.project.key, i % 7)), h('i', {}, cap(k))), k)));
    } else {
      // full-range keyboard (C1 to G7). Every key is labelled and clickable; the twelve white keys under the
      // orange bar are the ones the computer keyboard plays right now, and Z / X slide that window by an octave.
      // The strip keeps its width: the mouse wheel zooms the keys inside it, Shift + wheel slides sideways.
      const W = Math.round(18 * kbZoom), BW = Math.round(W * 0.66), LO = 24, HI = 103, base = 12 * (octave + 1), byPitch = {};
      for (const k in KEYMAP) byPitch[base + KEYMAP[k]] = k;
      const heldP = new Set(); held.forEach((hd) => hd.pitches.forEach((p) => heldP.add(p)));
      const tones = kbChord.root < 0 ? null : CHORD_TYPES[kbChord.type][1].map((i) => (kbChord.root + i) % 12);
      const tint = (p) => (!tones ? '' : p % 12 === kbChord.root ? ' ch chr' : tones.includes(p % 12) ? ' ch' : '') + (heldP.has(p) ? ' down' : '');
      const mouse = (node, pitch) => { keyEls['p' + pitch] = node; node.addEventListener('pointerdown', (e) => { e.preventDefault(); const id = 'm' + pitch; if (pressKey(id, [pitch])) DAW.drag(e, () => {}, () => releaseKey(id)); }); return node; };
      const whites = [], blacks = []; let wi = 0, winL = null, winR = null;
      for (let p = LO; p <= HI; p++) {
        const k = byPitch[p], name = DAW.noteName(p);
        if (DAW.isBlack(p)) blacks.push(mouse(h('div', { class: 'kbd-black' + (k ? ' win' : '') + tint(p), title: name, style: { left: wi * W - BW / 2 + 'px', width: BW + 'px' } }, h('u', {}, name), k ? h('i', {}, cap(k)) : null), p));
        else { if (k) { if (winL == null) winL = wi; winR = wi; } whites.push(mouse(h('div', { class: 'kbd-white' + (k ? ' win' : '') + (p % 12 === 0 ? ' c' : '') + tint(p), title: name, style: { left: wi * W + 'px', width: W - 1 + 'px' } }, h('u', {}, name), k ? h('i', {}, cap(k)) : null), p)); wi++; }
      }
      const total = wi * W, viewW = wi * 18, fz = Math.pow(kbZoom, 0.75);
      const piano = h('div', { class: 'kbd-piano', style: { width: total + 'px' } }, winL != null ? h('div', { class: 'kbd-winbar', style: { left: winL * W + 'px', width: (winR - winL + 1) * W - 1 + 'px' } }) : null, whites, blacks);
      for (const [n, v] of [['--fu', 8 * fz], ['--fb', 7 * fz], ['--fi', Math.min(20, 11 * fz)], ['--fib', Math.min(15, 9 * fz)]]) piano.style.setProperty(n, v.toFixed(1) + 'px');
      keys = h('div', { class: 'kbd-view', title: 'Mouse wheel: zoom in and out.  Shift + wheel: slide left and right.', style: { width: viewW + 'px' } }, piano);
      if (kbReveal && winL != null) { const l = winL * W, r = (winR + 1) * W; if (l < kbScroll || r > kbScroll + viewW) kbScroll = (l + r) / 2 - viewW / 2; }
      kbReveal = false; kbScroll = DAW.clamp(kbScroll, 0, total - viewW);
      keys.addEventListener('wheel', (e) => {
        e.preventDefault();
        if (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) { kbScroll = DAW.clamp(keys.scrollLeft + (e.deltaX || e.deltaY), 0, total - viewW); keys.scrollLeft = kbScroll; return; }
        const x = e.clientX - keys.getBoundingClientRect().left, frac = (keys.scrollLeft + x) / total;
        const z = DAW.clamp(kbZoom * (e.deltaY < 0 ? 1.2 : 1 / 1.2), 1, 4); if (z === kbZoom) return;
        kbZoom = z; kbScroll = frac * wi * Math.round(18 * z) - x; renderKbd();
      }, { passive: false });
      const sel = (opts, val, fn, title) => { const n = h('select', { class: 'kbd-sel', title }, opts.map(([v, t]) => h('option', { value: v, text: t }))); n.value = val; n.addEventListener('change', () => { fn(n.value); n.blur(); renderKbd(); }); return n; };
      const NN = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
      const cp = tones ? CHORD_TYPES[kbChord.type][1].map((i) => base + kbChord.root + i) : null;
      const hear = cp ? h('button', { class: 'mini', title: 'Hold to hear this chord (it is written into the clip too when Writing notes is on)' }, '▶ ' + cp.map((p) => NN[p % 12]).join(' ')) : h('span', { class: 'kbd-chint', text: 'lights up its keys' });
      if (cp) hear.addEventListener('pointerdown', (e) => { e.preventDefault(); if (pressKey('mchord', cp)) DAW.drag(e, () => {}, () => releaseKey('mchord')); });
      chordUi = h('div', { class: 'kbd-chord' }, h('span', { class: 'kbd-now-lab', text: 'SHOW CHORD' }),
        h('div', {}, sel([[-1, 'Off']].concat(NN.map((n, i) => [i, n])), kbChord.root, (v) => { kbChord.root = +v; }, 'Pick a chord to see which keys make it'),
          sel(CHORD_TYPES.map((c, i) => [i, c[0]]), kbChord.type, (v) => { kbChord.type = +v; if (kbChord.root < 0) kbChord.root = 0; }, 'Chord type')), hear);
    }
    const oct = (d) => () => { octave = DAW.clamp(octave + d, 1, 6); kbReveal = true; renderKbd(); };
    el.append(h('div', { class: 'kbd-now', title: 'The note or chord being played right now' }, h('span', { class: 'kbd-now-lab', text: 'NOW PLAYING' }), h('div', { id: 'kbdNow' }, h('b', {}, '—'), h('i', {}, ''))), write, keys, drum ? h('span', { class: 'kbd-note', text: 'Home row A to \\ plays the 12 drums' })
      : h('span', { class: 'kbd-oct' }, h('button', { class: 'mini', title: 'Octave down (Z)', onclick: oct(-1) }, '◀'), h('span', { title: 'Which part of the keyboard your computer keys play' }, 'Keys at C' + octave), h('button', { class: 'mini', title: 'Octave up (X)', onclick: oct(1) }, '▶')));
    if (chordUi) { el.append(chordUi); keys.scrollLeft = kbScroll; }
  }
  function onKeyDown(e) {
    const el = e.target, tag = el.tagName, k = e.key.toLowerCase(), mod = e.ctrlKey || e.metaKey;
    const typing = tag === 'TEXTAREA' || tag === 'SELECT' || (tag === 'INPUT' && !['range', 'checkbox', 'radio', 'button'].includes(el.type));
    if (!$('modal').classList.contains('hidden')) { if (e.key === 'Escape') closeExport(); return; }
    if (mod) {
      const file = { s: () => save(e.shiftKey), o: openProject, n: newProject, e: openExport }[k];
      if (file) { e.preventDefault(); file(); return; }
      if (typing) return;
      if (k === 'z') { e.preventDefault(); if (e.shiftKey) DAW.history.redo(); else DAW.history.undo(); return; }
      if (k === 'y') { e.preventDefault(); DAW.history.redo(); return; }
      if (S.focus === 'piano' ? DAW.pianoroll.key(e) : DAW.arrange.key(e)) e.preventDefault();
      return;
    }
    if (typing) return;
    if (e.code === 'Space') { e.preventDefault(); T.toggle(); return; }
    if (e.key === 'Home') { e.preventDefault(); T.seek(0); return; }
    if (['Delete', 'Backspace', 'Escape', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
      if (S.focus === 'piano' ? DAW.pianoroll.key(e) : DAW.arrange.key(e)) e.preventDefault();
      return;
    }
    if (e.repeat || e.altKey) return;
    if (k === '1') return DAW.setTab('piano'); if (k === '2') return DAW.setTab('inst'); if (k === '3') return DAW.setTab('mixer');
    if (k === 'z' || k === 'x') { octave = DAW.clamp(octave + (k === 'z' ? -1 : 1), 1, 6); kbReveal = true; renderKbd(); return; }
    if (k === 'm') { $('btnMetro').click(); return; }
    if (k === 'r') { $('btnRec').click(); return; }
    if (pressKey(k)) e.preventDefault();
  }

  /* ---------- boot ---------- */
  window.addEventListener('DOMContentLoaded', () => {
    S.project = DAW.demoProject();
    DAW.arrange.init(); DAW.pianoroll.init(); DAW.instrument.init(); DAW.mixer.init(); initTop(); initLayout();
    $('exGo').onclick = doExport; $('exCancel').onclick = closeExport;
    $('modal').addEventListener('pointerdown', (e) => { if (e.target === $('modal')) closeExport(); });
    let lastTrack = null;
    DAW.on('change', () => { const t = DAW.selTrack(), sig = t ? t.id + '|' + (t.instrument.source || '') : ''; if (sig !== lastTrack) { lastTrack = sig; octave = DAW.trackOctave(t); } renderKbd(); });
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', (e) => releaseKey(e.key.toLowerCase()));
    window.addEventListener('blur', () => [...held.keys()].forEach(releaseKey));
    if (native) native.onMenu((a) => {
      const acts = { new: newProject, open: openProject, save: () => save(false), saveAs: () => save(true), export: openExport, undo: () => DAW.history.undo(), redo: () => DAW.history.redo(), play: () => T.toggle() };
      if (acts[a]) acts[a]();
    });
    DAW.setProject(DAW.demoProject(), null);
    DAW.setTab('piano');
    requestAnimationFrame(frame);
  });
})();

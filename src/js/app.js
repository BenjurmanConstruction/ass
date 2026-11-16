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

  let lastBeat = -1;
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
  const light = (k, on) => { if (keyEls[k]) keyEls[k].classList.toggle('down', on); };
  function pressKey(k) {
    if (held.has(k)) return false;
    const track = DAW.selTrack(); if (!track) return false;
    const pitches = keyPitches(track, k); if (!pitches) return false;
    const drum = track.type === 'drums', rec = S.recording && E.playing, step = S.stepInput && !E.playing;
    const hnd = { voices: pitches.map((p) => E.noteOn(track, p, drum ? 0.85 : 0.8)), pitches, trackId: track.id, b0: 0, rec: rec && !drum, step };
    held.set(k, hnd);
    if (rec) { hnd.b0 = E.currentBeat(); if (drum) recordNote(track, pitches[0], hnd.b0, hnd.b0 + stepLen(), 0.85); }
    if (step) { // write the note at the playhead; keys held together stack into a chord
      if (stepHeld === 0) stepAt = Math.max(0, DAW.snapRound(T.pos));
      stepHeld++;
      pitches.forEach((p) => recordNote(track, p, stepAt, stepAt + stepLen(), 0.8, true, true));
      DAW.commit(); DAW.pianoroll.reveal(pitches[0]);
    }
    light(k, true);
    return true;
  }
  function releaseKey(k) {
    const hnd = held.get(k); if (!hnd) return;
    held.delete(k); hnd.voices.forEach((v) => E.noteOff(v)); light(k, false);
    if (hnd.rec && E.playing) {
      const t = DAW.track(hnd.trackId), b1 = E.currentBeat();
      if (t) { hnd.pitches.forEach((p) => recordNote(t, p, hnd.b0, b1, 0.8, true)); DAW.commit(); }
    }
    if (hnd.step) { stepHeld = Math.max(0, stepHeld - 1); if (stepHeld === 0) { T.pos = stepAt + stepLen(); DAW.pianoroll.reveal(); } }
  }
  /* on-screen keyboard: shows which computer key plays what, and is clickable */
  function renderKbd() {
    const el = $('kbd'), track = DAW.selTrack(), h = DAW.h;
    el.innerHTML = ''; keyEls = {};
    if (!track) return;
    const drum = track.type === 'drums', cap = (k) => (k === '\\' ? '\\' : k.toUpperCase());
    const bind = (node, k) => { keyEls[k] = node; if (held.has(k)) node.classList.add('down'); node.addEventListener('pointerdown', (e) => { e.preventDefault(); if (pressKey(k)) DAW.drag(e, () => {}, () => releaseKey(k)); }); return node; };
    const write = h('button', { class: 'kbd-write' + (S.stepInput ? ' on' : ''), title: 'When on (and the song is stopped), every key you press is written into the clip at the playhead, which then steps forward by the Snap value. Hold keys together for a chord. Turn off to just play.', onclick: () => { S.stepInput = !S.stepInput; renderKbd(); } }, S.stepInput ? '✎ Writing notes: ON' : '✎ Writing notes: OFF');
    let keys;
    if (drum || S.chordKeys) {
      const n = drum ? 12 : 8;
      keys = h('div', { class: 'kbd-pads' }, ROW.slice(0, n).map((k, i) => bind(h('div', { class: 'kbd-pad' }, h('b', {}, drum ? DAW.padName(track.instrument, i) : DAW.chordName(S.project.key, i % 7)), h('i', {}, cap(k))), k)));
    } else {
      const W = 46;
      keys = h('div', { class: 'kbd-piano', style: { width: W * 12 + 'px' } },
        ROW.map((k, i) => bind(h('div', { class: 'kbd-white', style: { left: i * W + 'px', width: W - 2 + 'px' } }, i % 7 === 0 ? h('u', {}, 'C' + (octave + (i ? 1 : 0))) : null, h('i', {}, cap(k))), k)),
        Object.keys(BLACK).map((k) => bind(h('div', { class: 'kbd-black', style: { left: (BLACK[k] + 1) * W - 15 + 'px' } }, h('i', {}, cap(k))), k)));
    }
    const oct = (d) => () => { octave = DAW.clamp(octave + d, 0, 7); renderKbd(); };
    el.append(write, keys, drum ? h('span', { class: 'kbd-note', text: 'Home row A to \\ plays the 12 drums' })
      : h('span', { class: 'kbd-oct' }, h('button', { class: 'mini', title: 'Octave down (Z)', onclick: oct(-1) }, '◀'), h('span', {}, 'Octave ' + octave), h('button', { class: 'mini', title: 'Octave up (X)', onclick: oct(1) }, '▶')));
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
    if (k === 'z' || k === 'x') { octave = DAW.clamp(octave + (k === 'z' ? -1 : 1), 0, 7); renderKbd(); return; }
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
    DAW.on('change', renderKbd);
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

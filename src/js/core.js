/* Sable — core: namespace, project model, presets, history, small UI helpers */
(function () {
  'use strict';
  const DAW = (window.DAW = {});

  DAW.uid = () => Math.random().toString(36).slice(2, 10);
  DAW.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  DAW.clone = (o) => JSON.parse(JSON.stringify(o));
  const NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
  DAW.noteName = (p) => NAMES[((p % 12) + 12) % 12] + (Math.floor(p / 12) - 1);
  DAW.isBlack = (p) => [1, 3, 6, 8, 10].includes(((p % 12) + 12) % 12);
  DAW.COLORS = ['#e8a33d', '#5fb3a1', '#d96c5f', '#7f9fe0', '#b58ad6', '#9bbf5a', '#e07fb0', '#58b8d8'];
  DAW.volGain = (v) => v * v * 1.5;
  DAW.volDb = (v) => (v <= 0.001 ? '-inf' : (20 * Math.log10(DAW.volGain(v))).toFixed(1)) + ' dB';

  DAW.DRUM_BASE = 36;
  DAW.DRUM_PADS = [
    { id: 'kick', name: 'Kick' }, { id: 'snare', name: 'Snare' }, { id: 'clap', name: 'Clap' },
    { id: 'rim', name: 'Rim' }, { id: 'chat', name: 'Closed Hat' }, { id: 'ohat', name: 'Open Hat' },
    { id: 'ltom', name: 'Low Tom' }, { id: 'mtom', name: 'Mid Tom' }, { id: 'htom', name: 'High Tom' },
    { id: 'cow', name: 'Cowbell' }, { id: 'crash', name: 'Crash' }, { id: 'ride', name: 'Ride' },
  ];
  DAW.KITS = ['Rock Kit', '808 Deep', '909 Punch', 'Lo-Fi Dust'];
  DAW.SAMPLE_KITS = {}; // sampled kits register themselves here (see kit-rock.js)
  DAW.padName = (inst, i) => { const k = DAW.SAMPLE_KITS[inst.kit], p = DAW.DRUM_PADS[i]; return k && k.pads[p.id] ? k.pads[p.id].name : p.name; };
  DAW.DELAY_TIMES = { '1/4': 1, '1/8d': 0.75, '1/8': 0.5, '1/8t': 1 / 3, '1/16': 0.25 };
  DAW.SNAPS = [['1 bar', 4], ['1/2', 2], ['1/4', 1], ['1/8', 0.5], ['1/16', 0.25], ['1/32', 0.125], ['1/8T', 1 / 3], ['1/16T', 1 / 6], ['Off', 0]];

  /* ---------- keys, scales, chords ---------- */
  DAW.SCALES = { major: [0, 2, 4, 5, 7, 9, 11], minor: [0, 2, 3, 5, 7, 8, 10] };
  DAW.KEY_NAMES = ['C', 'C# / Db', 'D', 'Eb', 'E', 'F', 'F# / Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
  DAW.ROMAN = { major: ['I', 'ii', 'iii', 'IV', 'V', 'vi', 'vii°'], minor: ['i', 'ii°', 'III', 'iv', 'v', 'VI', 'VII'] };
  DAW.PROGRESSIONS = [
    ['Pop: I V vi IV', 'major', [0, 4, 5, 3]], ['Ballad: vi IV I V', 'major', [5, 3, 0, 4]], ['Rock: I IV V IV', 'major', [0, 3, 4, 3]],
    ['50s: I vi IV V', 'major', [0, 5, 3, 4]], ['Jazzy: ii V I I', 'major', [1, 4, 0, 0]], ['12-bar blues', 'major', [0, 0, 0, 0, 3, 3, 0, 0, 4, 3, 0, 4]],
    ['Minor rock: i VII VI VII', 'minor', [0, 6, 5, 6]], ['Epic: i VI III VII', 'minor', [0, 5, 2, 6]], ['Sad: i iv v i', 'minor', [0, 3, 4, 0]], ['Dark: i VI iv v', 'minor', [0, 5, 3, 4]],
  ];
  const FLAT_KEYS = { major: [5, 10, 3, 8, 1], minor: [2, 7, 0, 5, 10, 3] };
  const SHARP = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'], FLAT = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
  const degSemi = (key, i) => DAW.SCALES[key.scale][((i % 7) + 7) % 7] + 12 * Math.floor(i / 7);
  /** The three notes of the chord built on scale degree `deg` (0-6) of a key, voiced around middle C. */
  DAW.chordNotes = (key, deg) => {
    let root = 48 + key.root + degSemi(key, deg);
    while (root > 59) root -= 12;
    const base = key.root + degSemi(key, deg);
    return [0, 2, 4].map((k) => root + (key.root + degSemi(key, deg + k)) - base);
  };
  DAW.chordName = (key, deg) => {
    const n = DAW.chordNotes(key, deg), names = FLAT_KEYS[key.scale].includes(key.root) ? FLAT : SHARP;
    return names[n[0] % 12] + (n[1] - n[0] === 3 ? (n[2] - n[0] === 6 ? '°' : 'm') : '');
  };
  DAW.inScale = (key, pitch) => DAW.SCALES[key.scale].includes((((pitch - key.root) % 12) + 12) % 12);

  /* ---------- instruments ---------- */
  const baseSynth = () => ({
    preset: 'Init', source: 'synth', // source: 'synth' or the name of a sampled instrument
    osc1: { wave: 'sawtooth', oct: 0, level: 0.8 },
    osc2: { wave: 'sawtooth', oct: 0, detune: 8, level: 0.5 },
    noise: 0,
    filter: { type: 'lowpass', cutoff: 2400, q: 1, env: 1.5 },
    amp: { a: 0.005, d: 0.2, s: 0.7, r: 0.2 },
    fenv: { a: 0.005, d: 0.25, s: 0.3, r: 0.2 },
    lfo: { rate: 5, pitch: 0, filter: 0 },
    unison: { voices: 1, detune: 18, spread: 0.6 },
    gain: 0.7,
  });
  function merge(base, over) {
    for (const k in over) {
      if (over[k] && typeof over[k] === 'object' && base[k] && typeof base[k] === 'object') merge(base[k], over[k]);
      else base[k] = over[k];
    }
    return base;
  }
  const PRESETS = {
    'Saw Lead': { osc2: { detune: 12, level: 0.6 }, filter: { cutoff: 3200, q: 2 }, amp: { r: 0.18 }, lfo: { rate: 5.5, pitch: 6 } },
    'Square Lead': { osc1: { wave: 'square' }, osc2: { wave: 'square', oct: 1, detune: 4, level: 0.25 }, filter: { cutoff: 4200, q: 1.2, env: 0.8 }, lfo: { rate: 6, pitch: 9 } },
    'Super Saw': { osc2: { oct: 1, detune: 6, level: 0.35 }, unison: { voices: 5, detune: 24, spread: 0.85 }, filter: { cutoff: 5200, q: 0.8, env: 0.6 }, amp: { a: 0.01, d: 0.3, s: 0.8, r: 0.35 }, gain: 0.6 },
    'Brass': { osc2: { detune: 5, level: 0.6 }, unison: { voices: 3, detune: 10, spread: 0.4 }, filter: { cutoff: 900, q: 1.2, env: 2.2 }, fenv: { a: 0.06, d: 0.3, s: 0.55, r: 0.2 }, amp: { a: 0.03, d: 0.2, s: 0.85, r: 0.18 }, gain: 0.6 },
    'Reese Bass': { osc1: { oct: -1, level: 0.9 }, osc2: { oct: -1, detune: 22, level: 0.9 }, unison: { voices: 3, detune: 14, spread: 0.5 }, filter: { cutoff: 520, q: 1.5, env: 0.8 }, amp: { a: 0.006, d: 0.2, s: 0.9, r: 0.12 }, gain: 0.6 },
    'Sub Bass': { osc1: { wave: 'sine', oct: -1, level: 1 }, osc2: { wave: 'triangle', oct: -1, detune: 0, level: 0.3 }, filter: { cutoff: 900, q: 0.7, env: 0.5 }, amp: { a: 0.004, d: 0.15, s: 0.85, r: 0.08 }, gain: 0.9 },
    'Acid Bass': { osc1: { oct: -1, level: 1 }, osc2: { wave: 'square', oct: -1, detune: 0, level: 0.25 }, filter: { cutoff: 380, q: 9, env: 3.2 }, fenv: { a: 0.003, d: 0.18, s: 0.05, r: 0.1 }, amp: { a: 0.003, d: 0.2, s: 0.6, r: 0.06 }, gain: 0.6 },
    'Warm Pad': { osc2: { detune: 14, level: 0.7 }, unison: { voices: 3, detune: 16, spread: 0.8 }, filter: { cutoff: 1400, q: 0.8, env: 1 }, amp: { a: 0.5, d: 0.8, s: 0.8, r: 1.2 }, fenv: { a: 0.8, d: 1, s: 0.6, r: 1 }, lfo: { rate: 0.4, filter: 200 }, gain: 0.5 },
    'Strings': { osc2: { oct: 1, detune: 9, level: 0.4 }, filter: { cutoff: 3000, q: 0.6, env: 0.4 }, amp: { a: 0.25, d: 0.4, s: 0.85, r: 0.6 }, lfo: { rate: 5, pitch: 4 }, gain: 0.5 },
    'Pluck': { osc2: { wave: 'square', detune: 5, level: 0.4 }, filter: { cutoff: 700, q: 2, env: 3.5 }, fenv: { a: 0.002, d: 0.16, s: 0, r: 0.15 }, amp: { a: 0.002, d: 0.3, s: 0, r: 0.25 } },
    'Keys': { osc1: { wave: 'triangle', level: 1 }, osc2: { wave: 'sine', oct: 1, detune: 0, level: 0.35 }, filter: { cutoff: 2600, q: 0.5, env: 1.2 }, amp: { a: 0.004, d: 0.9, s: 0.15, r: 0.3 }, fenv: { a: 0.002, d: 0.5, s: 0.1, r: 0.3 } },
    'Bell': { osc1: { wave: 'sine', level: 1 }, osc2: { wave: 'sine', oct: 2, detune: 14, level: 0.45 }, filter: { cutoff: 9000, q: 0.5, env: 0 }, amp: { a: 0.002, d: 1.6, s: 0, r: 1.2 } },
    'Organ': { osc1: { wave: 'sine', level: 1 }, osc2: { wave: 'sine', oct: 1, detune: 0, level: 0.6 }, filter: { cutoff: 5000, q: 0.5, env: 0 }, amp: { a: 0.01, d: 0.1, s: 1, r: 0.08 } },
  };
  DAW.SYNTH_PRESETS = Object.keys(PRESETS);
  DAW.makeSynth = (name) => {
    const s = merge(baseSynth(), PRESETS[name] || {});
    s.preset = PRESETS[name] ? name : 'Init';
    return s;
  };
  DAW.SAMPLE_INSTS = {}; // sampled instruments register themselves here (see instruments.js)
  DAW.makeSampled = (name) => {
    const s = baseSynth(), si = DAW.SAMPLE_INSTS[name];
    s.source = name; s.preset = name; s.gain = 0.8; s.filter.cutoff = 18000;
    s.amp = { a: 0.002, d: 0.2, s: 1, r: si ? si.release : 0.2 }; s.lfo = { rate: 5, pitch: 0, filter: 0 };
    return s;
  };
  DAW.makeDrums = (kit) => {
    const pads = {};
    for (const p of DAW.DRUM_PADS) pads[p.id] = { level: 0.8, tune: 0, decay: 1 };
    return { kit: kit || 'Rock Kit', pads };
  };

  /* ---------- project ---------- */
  DAW.makeTrack = (type, name, preset, i) => ({
    id: DAW.uid(), name, type, color: DAW.COLORS[(i || 0) % DAW.COLORS.length],
    instrument: type === 'drums' ? DAW.makeDrums(preset) : DAW.SAMPLE_INSTS[preset] ? DAW.makeSampled(preset) : DAW.makeSynth(preset),
    volume: 0.75, pan: 0, mute: false, solo: false,
    eq: { low: 0, mid: 0, high: 0 }, drive: 0, comp: 0, sendA: 0, sendB: 0, clips: [],
  });
  DAW.makeClip = (start, length, notes) => ({ id: DAW.uid(), start, length, notes: notes || [] });
  DAW.note = (pitch, start, length, vel) => ({ id: DAW.uid(), pitch, start, length, vel: vel == null ? 0.8 : vel });
  DAW.emptyProject = () => ({
    format: 'sable-project', version: 1, name: 'Untitled', bpm: 120, swing: 0,
    loop: { on: false, start: 0, end: 16 },
    master: { volume: 0.8, limiter: true },
    reverb: { decay: 2.2, level: 0.8 },
    delay: { time: '1/8d', feedback: 0.35, level: 0.8 },
    key: { root: 0, scale: 'major' },
    tracks: [],
  });
  DAW.normalizeProject = (raw) => {
    if (!raw || raw.format !== 'sable-project' || !Array.isArray(raw.tracks)) throw new Error('Not a Sable project file');
    const p = merge(DAW.emptyProject(), raw);
    p.tracks = raw.tracks.map((t, i) => {
      const type = t.type === 'drums' ? 'drums' : 'synth';
      const base = DAW.makeTrack(type, t.name || 'Track', null, i);
      const inst = type === 'drums' ? merge(DAW.makeDrums(), t.instrument || {}) : merge(DAW.makeSynth(), t.instrument || {});
      const out = merge(base, t);
      out.type = type; out.instrument = inst; out.id = t.id || DAW.uid();
      out.clips = (t.clips || []).map((c) => ({
        id: c.id || DAW.uid(), start: +c.start || 0, length: Math.max(0.25, +c.length || 4),
        notes: (c.notes || []).map((n) => ({ id: n.id || DAW.uid(), pitch: n.pitch | 0, start: +n.start || 0, length: Math.max(0.01, +n.length || 0.25), vel: DAW.clamp(n.vel == null ? 0.8 : +n.vel, 0.01, 1) })),
      }));
      return out;
    });
    return p;
  };

  DAW.demoProject = () => {
    const p = DAW.emptyProject();
    p.name = 'Demo'; p.key = { root: 9, scale: 'minor' }; p.bpm = 112; p.loop = { on: true, start: 0, end: 32 };
    const N = DAW.note;
    const drums = DAW.makeTrack('drums', 'Drums', 'Rock Kit', 0);
    const B = DAW.DRUM_BASE, KICK = B, SNARE = B + 1, HAT = B + 4, OHAT = B + 5, FLOOR = B + 6, MID = B + 7, HIGH = B + 8, CRASH = B + 10;
    const bar = (first, fill) => {
      const n = [];
      for (const k of [0, 2.5, 4, 4.75, 6.5]) n.push(N(KICK, k, 0.25, k % 1 ? 0.8 : 1));
      for (const sn of [1, 3, 5, 7]) if (!(fill && sn === 7)) n.push(N(SNARE, sn, 0.25, 0.95));
      n.push(N(SNARE, 3.75, 0.25, 0.3));
      for (let h = 0; h < (fill ? 12 : 15); h++) n.push(N(HAT, h * 0.5, 0.25, h % 2 ? 0.5 : 0.8));
      if (first) n.push(N(CRASH, 0, 0.25, 0.9));
      if (fill) [[SNARE, 6, 0.8], [SNARE, 6.25, 0.9], [HIGH, 6.5, 0.85], [HIGH, 6.75, 0.9], [MID, 7, 0.9], [MID, 7.25, 0.95], [FLOOR, 7.5, 0.95], [FLOOR, 7.75, 1]].forEach(([p, s, v]) => n.push(N(p, s, 0.25, v)));
      else n.push(N(OHAT, 7.5, 0.25, 0.75));
      return n;
    };
    for (let i = 0; i < 4; i++) drums.clips.push(DAW.makeClip(i * 8, 8, bar(i === 0, i === 3)));
    drums.volume = 0.84; drums.sendA = 0.12; drums.comp = 0.5;

    const chords = [[57, 60, 64], [53, 57, 60], [55, 60, 64], [55, 59, 62]];
    const roots = [33, 29, 36, 31];
    const bass = DAW.makeTrack('synth', 'Bass', 'Electric Bass', 1);
    const bn = [];
    roots.forEach((r, i) => {
      for (let b = 0; b < 2; b++) {
        const o = i * 8 + b * 4;
        bn.push(N(r, o, 0.7, 0.9), N(r, o + 0.75, 0.2, 0.7), N(r, o + 1.5, 0.45, 0.85), N(r + 12, o + 2.5, 0.2, 0.7), N(r, o + 3, 0.45, 0.85), N(r + 7, o + 3.5, 0.4, 0.7));
      }
    });
    bass.clips.push(DAW.makeClip(0, 32, bn)); bass.volume = 0.86; bass.comp = 0.3;

    const pad = DAW.makeTrack('synth', 'Keys', 'Electric Piano', 2);
    const pn = [];
    chords.forEach((c, i) => [0, 2.5, 4, 6.5].forEach((o, k) => c.forEach((n) => pn.push(N(n, i * 8 + o, k % 2 ? 1.4 : 2.4, k % 2 ? 0.55 : 0.75)))));
    pad.clips.push(DAW.makeClip(0, 32, pn)); pad.sendA = 0.25; pad.volume = 0.7; pad.pan = -0.2;

    const lead = DAW.makeTrack('synth', 'Piano', 'Grand Piano', 3);
    const ln = [];
    const pat = [0, 2, 1, 2, 0, 2, 1, 3];
    chords.forEach((c, i) => {
      for (let s = 0; s < 16; s++) {
        const k = pat[s % 8];
        const pitch = k === 3 ? c[0] + 24 : c[k] + 12;
        ln.push(N(pitch, i * 8 + s * 0.5, 0.4, s % 4 === 0 ? 0.9 : 0.6));
      }
    });
    lead.clips.push(DAW.makeClip(0, 32, ln)); lead.sendB = 0.12; lead.sendA = 0.3; lead.volume = 0.7; lead.pan = 0.2;

    p.tracks = [drums, bass, pad, lead];
    return p;
  };

  /* ---------- state, events, history ---------- */
  DAW.state = { stepInput: true, chordLen: 4, scaleHint: true, chordKeys: false, project: null, selTrackId: null, selClipId: null, snap: 0.25, metronome: false, recording: false, playing: false, filePath: null, dirty: false, focus: 'arrange', tab: 'piano' };
  const L = {};
  DAW.on = (e, f) => (L[e] || (L[e] = [])).push(f);
  DAW.emit = (e, a) => (L[e] || []).forEach((f) => f(a));

  DAW.track = (id) => DAW.state.project.tracks.find((t) => t.id === id) || null;
  DAW.selTrack = () => DAW.track(DAW.state.selTrackId);
  DAW.findClip = (id) => {
    if (!id) return null;
    for (const track of DAW.state.project.tracks) {
      const clip = track.clips.find((c) => c.id === id);
      if (clip) return { track, clip };
    }
    return null;
  };
  DAW.songEnd = () => {
    let end = 0;
    for (const t of DAW.state.project.tracks) for (const c of t.clips) end = Math.max(end, c.start + c.length);
    return end;
  };
  /** True when [start, start+length) on a track doesn't collide with another clip. */
  DAW.clipFits = (track, start, length, ignore) => track.clips.every((c) => c === ignore || start >= c.start + c.length - 1e-6 || start + length <= c.start + 1e-6);
  DAW.snapFloor = (b) => (DAW.state.snap ? Math.floor(b / DAW.state.snap + 1e-6) * DAW.state.snap : b);
  DAW.snapRound = (b) => (DAW.state.snap ? Math.round(b / DAW.state.snap) * DAW.state.snap : b);

  function fixSelection() {
    const s = DAW.state;
    if (!DAW.findClip(s.selClipId)) s.selClipId = null;
    if (!DAW.track(s.selTrackId)) s.selTrackId = s.project.tracks[0] ? s.project.tracks[0].id : null;
  }
  DAW.select = (trackId, clipId) => {
    const s = DAW.state;
    if (s.selTrackId === trackId && s.selClipId === (clipId || null)) return;
    s.selTrackId = trackId; s.selClipId = clipId || null;
    DAW.emit('change');
  };

  const H = { past: [], future: [], present: '' };
  function restore() {
    DAW.state.project = JSON.parse(H.present);
    fixSelection(); DAW.state.dirty = true;
    DAW.engine.sync(); DAW.emit('change');
  }
  DAW.history = {
    reset() { H.past = []; H.future = []; H.present = JSON.stringify(DAW.state.project); },
    canUndo: () => H.past.length > 0, canRedo: () => H.future.length > 0,
    undo() { if (!H.past.length) return; H.future.push(H.present); H.present = H.past.pop(); restore(); },
    redo() { if (!H.future.length) return; H.past.push(H.present); H.present = H.future.pop(); restore(); },
  };
  /** Call after any finished edit: records an undo step, syncs audio, redraws. */
  DAW.commit = () => {
    const s = JSON.stringify(DAW.state.project);
    if (s !== H.present) {
      H.past.push(H.present); if (H.past.length > 300) H.past.shift();
      H.present = s; H.future = []; DAW.state.dirty = true;
    }
    fixSelection(); DAW.engine.sync(); DAW.emit('change');
  };
  DAW.setProject = (p, filePath) => {
    const s = DAW.state;
    s.project = p; s.filePath = filePath || null; s.dirty = false;
    s.selTrackId = p.tracks[0] ? p.tracks[0].id : null;
    s.selClipId = p.tracks[0] && p.tracks[0].clips[0] ? p.tracks[0].clips[0].id : null;
    DAW.history.reset(); DAW.engine.sync(); DAW.emit('change');
  };

  /* ---------- DOM helpers ---------- */
  DAW.h = (tag, props, ...kids) => {
    const el = document.createElement(tag);
    for (const c of kids.flat(Infinity)) { if (c == null || c === false) continue; el.append(c.nodeType ? c : document.createTextNode(c)); }
    if (props) for (const k in props) {
      const v = props[k];
      if (k === 'class') el.className = v;
      else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (k === 'text') el.textContent = v;
      else if (k === 'value') el.value = v;
      else if (v !== false && v != null) el.setAttribute(k, v === true ? '' : v);
    }
    return el;
  };
  DAW.drag = (e, onMove, onUp) => {
    const mv = (ev) => onMove(ev);
    const up = (ev) => {
      window.removeEventListener('pointermove', mv); window.removeEventListener('pointerup', up); window.removeEventListener('pointercancel', up);
      if (onUp) onUp(ev);
    };
    window.addEventListener('pointermove', mv); window.addEventListener('pointerup', up); window.addEventListener('pointercancel', up);
  };
  /** Small right-click menu. items: [[label, fn], ...] */
  DAW.menu = (x, y, items) => {
    const old = document.getElementById('ctx'); if (old) old.remove();
    const el = DAW.h('div', { id: 'ctx' }, items.map(([label, fn]) => DAW.h('button', { onclick: () => { close(); fn(); } }, label)));
    const close = () => { el.remove(); window.removeEventListener('pointerdown', away, true); window.removeEventListener('keydown', esc, true); };
    const away = (e) => { if (!el.contains(e.target)) close(); };
    const esc = (e) => { if (e.key === 'Escape') { e.stopPropagation(); close(); } };
    document.body.append(el);
    el.style.left = Math.min(x, window.innerWidth - el.offsetWidth - 6) + 'px'; el.style.top = Math.min(y, window.innerHeight - el.offsetHeight - 6) + 'px';
    setTimeout(() => { window.addEventListener('pointerdown', away, true); window.addEventListener('keydown', esc, true); }, 0);
  };
  DAW.toast = (msg, ms) => {
    const el = document.getElementById('toast');
    el.textContent = msg; el.classList.add('show');
    clearTimeout(DAW._tt); DAW._tt = setTimeout(() => el.classList.remove('show'), ms || 2600);
  };

  /** Rotary knob. opts: {label,min,max,value,def,log,step,fmt,onInput,onChange} */
  DAW.knob = (o) => {
    const h = DAW.h;
    const toN = (v) => (o.log ? Math.log(v / o.min) / Math.log(o.max / o.min) : (v - o.min) / (o.max - o.min));
    const fromN = (n) => {
      let v = o.log ? o.min * Math.pow(o.max / o.min, n) : o.min + n * (o.max - o.min);
      if (o.step) v = Math.round(v / o.step) * o.step;
      return DAW.clamp(v, o.min, o.max);
    };
    const fmt = o.fmt || ((v) => (Math.abs(v) >= 100 ? v.toFixed(0) : Math.abs(v) >= 10 ? v.toFixed(1) : v.toFixed(2)));
    let value = o.value;
    const NS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(NS, 'svg'); svg.setAttribute('viewBox', '0 0 40 40'); svg.setAttribute('class', 'knob-svg');
    const mk = (tag, attrs) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); svg.append(e); return e; };
    const arc = (n0, n1) => {
      if (n1 < n0) { const t = n0; n0 = n1; n1 = t; }
      if (n1 - n0 < 0.004) n1 = n0 + 0.004;
      const a0 = Math.PI * 0.75 + n0 * Math.PI * 1.5, a1 = Math.PI * 0.75 + n1 * Math.PI * 1.5, r = 16;
      const p = (a) => (20 + r * Math.cos(a)).toFixed(2) + ' ' + (20 + r * Math.sin(a)).toFixed(2);
      return `M ${p(a0)} A ${r} ${r} 0 ${n1 - n0 > 2 / 3 ? 1 : 0} 1 ${p(a1)}`;
    };
    const origin = !o.log && o.min < 0 && o.max > 0 ? toN(0) : 0; // bipolar knobs fill from centre
    mk('path', { d: arc(0, 1), class: 'knob-track' });
    const val = mk('path', { d: arc(origin, origin), class: 'knob-val' });
    mk('circle', { cx: 20, cy: 20, r: 11, class: 'knob-cap' });
    const ptr = mk('line', { x1: 20, y1: 20, x2: 20, y2: 11, class: 'knob-ptr' });
    const read = h('div', { class: 'knob-read' });
    const el = h('div', { class: 'knob', title: o.label + ' — drag up/down, Shift for fine, double-click to reset' }, svg, read, h('div', { class: 'knob-label', text: o.label }));
    const paint = () => {
      const n = DAW.clamp(toN(value), 0, 1);
      val.setAttribute('d', arc(origin, n));
      ptr.setAttribute('transform', `rotate(${-135 + n * 270} 20 20)`);
      read.textContent = fmt(value);
    };
    const set = (v, done) => {
      v = DAW.clamp(v, o.min, o.max);
      if (v !== value) { value = v; paint(); if (o.onInput) o.onInput(value); }
      if (done && o.onChange) o.onChange(value);
    };
    svg.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      let n = toN(value), y = e.clientY;
      el.classList.add('active');
      DAW.drag(e, (ev) => {
        n = DAW.clamp(n + (y - ev.clientY) / (ev.shiftKey ? 900 : 160), 0, 1); y = ev.clientY;
        set(fromN(n));
      }, () => { el.classList.remove('active'); set(value, true); });
    });
    svg.addEventListener('dblclick', () => { if (o.def != null) set(o.def, true); });
    svg.addEventListener('wheel', (e) => {
      e.preventDefault();
      set(fromN(DAW.clamp(toN(value) - Math.sign(e.deltaY) * 0.03, 0, 1)));
      clearTimeout(el._wt); el._wt = setTimeout(() => set(value, true), 350);
    }, { passive: false });
    paint();
    return el;
  };
})();

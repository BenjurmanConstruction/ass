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
  DAW.KITS = ['808 Deep', '909 Punch', 'Lo-Fi Dust'];
  DAW.DELAY_TIMES = { '1/4': 1, '1/8d': 0.75, '1/8': 0.5, '1/8t': 1 / 3, '1/16': 0.25 };
  DAW.SNAPS = [['1 bar', 4], ['1/2', 2], ['1/4', 1], ['1/8', 0.5], ['1/16', 0.25], ['1/32', 0.125], ['1/8T', 1 / 3], ['1/16T', 1 / 6], ['Off', 0]];

  /* ---------- instruments ---------- */
  const baseSynth = () => ({
    preset: 'Init',
    osc1: { wave: 'sawtooth', oct: 0, level: 0.8 },
    osc2: { wave: 'sawtooth', oct: 0, detune: 8, level: 0.5 },
    noise: 0,
    filter: { type: 'lowpass', cutoff: 2400, q: 1, env: 1.5 },
    amp: { a: 0.005, d: 0.2, s: 0.7, r: 0.2 },
    fenv: { a: 0.005, d: 0.25, s: 0.3, r: 0.2 },
    lfo: { rate: 5, pitch: 0, filter: 0 },
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
    'Sub Bass': { osc1: { wave: 'sine', oct: -1, level: 1 }, osc2: { wave: 'triangle', oct: -1, detune: 0, level: 0.3 }, filter: { cutoff: 900, q: 0.7, env: 0.5 }, amp: { a: 0.004, d: 0.15, s: 0.85, r: 0.08 }, gain: 0.9 },
    'Acid Bass': { osc1: { oct: -1, level: 1 }, osc2: { wave: 'square', oct: -1, detune: 0, level: 0.25 }, filter: { cutoff: 380, q: 9, env: 3.2 }, fenv: { a: 0.003, d: 0.18, s: 0.05, r: 0.1 }, amp: { a: 0.003, d: 0.2, s: 0.6, r: 0.06 }, gain: 0.6 },
    'Warm Pad': { osc2: { detune: 14, level: 0.7 }, filter: { cutoff: 1400, q: 0.8, env: 1 }, amp: { a: 0.5, d: 0.8, s: 0.8, r: 1.2 }, fenv: { a: 0.8, d: 1, s: 0.6, r: 1 }, lfo: { rate: 0.4, filter: 200 }, gain: 0.5 },
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
  DAW.makeDrums = (kit) => {
    const pads = {};
    for (const p of DAW.DRUM_PADS) pads[p.id] = { level: 0.8, tune: 0, decay: 1 };
    return { kit: kit || '808 Deep', pads };
  };

  /* ---------- project ---------- */
  DAW.makeTrack = (type, name, preset, i) => ({
    id: DAW.uid(), name, type, color: DAW.COLORS[(i || 0) % DAW.COLORS.length],
    instrument: type === 'drums' ? DAW.makeDrums(preset) : DAW.makeSynth(preset),
    volume: 0.75, pan: 0, mute: false, solo: false,
    eq: { low: 0, mid: 0, high: 0 }, drive: 0, sendA: 0, sendB: 0, clips: [],
  });
  DAW.makeClip = (start, length, notes) => ({ id: DAW.uid(), start, length, notes: notes || [] });
  DAW.note = (pitch, start, length, vel) => ({ id: DAW.uid(), pitch, start, length, vel: vel == null ? 0.8 : vel });
  DAW.emptyProject = () => ({
    format: 'sable-project', version: 1, name: 'Untitled', bpm: 120, swing: 0,
    loop: { on: false, start: 0, end: 16 },
    master: { volume: 0.8, limiter: true },
    reverb: { decay: 2.2, level: 0.8 },
    delay: { time: '1/8d', feedback: 0.35, level: 0.8 },
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
    p.name = 'Demo'; p.bpm = 112; p.loop = { on: true, start: 0, end: 32 };
    const N = DAW.note;
    const drums = DAW.makeTrack('drums', 'Drums', '909 Punch', 0);
    const B = DAW.DRUM_BASE;
    const bar = (fill) => {
      const n = [];
      for (let b = 0; b < 8; b++) {
        n.push(N(B, b, 0.25, 0.95));
        n.push(N(B + 4, b + 0.5, 0.25, 0.7));
        if (b % 2 === 1) n.push(N(B + 2, b, 0.25, 0.85));
        n.push(N(B + 4, b + 0.25, 0.25, 0.3), N(B + 4, b + 0.75, 0.25, 0.35));
      }
      n.push(N(B + 5, 7.5, 0.25, 0.7));
      if (fill) n.push(N(B + 1, 7, 0.25, 0.6), N(B + 1, 7.25, 0.25, 0.7), N(B + 1, 7.75, 0.25, 0.9), N(B + 8, 6.5, 0.25, 0.7), N(B + 6, 6.75, 0.25, 0.7));
      return n;
    };
    for (let i = 0; i < 4; i++) drums.clips.push(DAW.makeClip(i * 8, 8, bar(i === 3)));
    drums.volume = 0.8; drums.sendA = 0.06;

    const chords = [[57, 60, 64], [53, 57, 60], [55, 60, 64], [55, 59, 62]];
    const roots = [45, 41, 48, 43];
    const bass = DAW.makeTrack('synth', 'Bass', 'Sub Bass', 1);
    const bn = [];
    roots.forEach((r, i) => {
      for (let b = 0; b < 2; b++) {
        const o = i * 8 + b * 4;
        bn.push(N(r, o, 0.7, 0.9), N(r, o + 0.75, 0.2, 0.7), N(r, o + 1.5, 0.45, 0.85), N(r + 12, o + 2.5, 0.2, 0.7), N(r, o + 3, 0.45, 0.85), N(r + 7, o + 3.5, 0.4, 0.7));
      }
    });
    bass.clips.push(DAW.makeClip(0, 32, bn)); bass.volume = 0.78;

    const pad = DAW.makeTrack('synth', 'Pad', 'Warm Pad', 2);
    const pn = [];
    chords.forEach((c, i) => c.forEach((n) => pn.push(N(n, i * 8, 8, 0.65))));
    pad.clips.push(DAW.makeClip(0, 32, pn)); pad.sendA = 0.45; pad.volume = 0.68;

    const lead = DAW.makeTrack('synth', 'Pluck', 'Pluck', 3);
    const ln = [];
    const pat = [0, 2, 1, 2, 0, 2, 1, 3];
    chords.forEach((c, i) => {
      for (let s = 0; s < 16; s++) {
        const k = pat[s % 8];
        const pitch = k === 3 ? c[0] + 24 : c[k] + 12;
        ln.push(N(pitch, i * 8 + s * 0.5, 0.4, s % 4 === 0 ? 0.9 : 0.6));
      }
    });
    lead.clips.push(DAW.makeClip(0, 32, ln)); lead.sendB = 0.35; lead.sendA = 0.2; lead.volume = 0.66; lead.pan = 0.15;

    p.tracks = [drums, bass, pad, lead];
    return p;
  };

  /* ---------- state, events, history ---------- */
  DAW.state = { project: null, selTrackId: null, selClipId: null, snap: 0.25, metronome: false, recording: false, playing: false, filePath: null, dirty: false, focus: 'arrange', tab: 'piano' };
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

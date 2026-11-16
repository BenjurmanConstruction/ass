/* Sable — instrument editor and mixer panels */
(function () {
  'use strict';
  const DAW = window.DAW, h = DAW.h;
  const live = () => DAW.engine.sync();
  const F = {
    hz: (v) => (v >= 1000 ? (v / 1000).toFixed(1) + 'k' : v.toFixed(0)),
    sec: (v) => (v < 1 ? (v * 1000).toFixed(0) + 'ms' : v.toFixed(2) + 's'),
    pct: (v) => (v * 100).toFixed(0) + '%',
    db: (v) => (v > 0 ? '+' : '') + v.toFixed(1),
    int: (v) => (v > 0 ? '+' : '') + v.toFixed(0),
    pan: (v) => (Math.abs(v) < 0.01 ? 'C' : (v < 0 ? 'L' : 'R') + Math.round(Math.abs(v) * 100)),
  };
  const K = (label, obj, key, min, max, o) => DAW.knob(Object.assign({ label, min, max, value: obj[key], onInput: (v) => { obj[key] = v; live(); }, onChange: () => DAW.commit() }, o || {}));
  const sel = (obj, key, options, title, after) => {
    const s = h('select', { title }, options.map((o) => h('option', { value: Array.isArray(o) ? o[0] : o }, Array.isArray(o) ? o[1] : o)));
    s.value = obj[key];
    s.addEventListener('change', () => { obj[key] = s.value; s.blur(); if (after) after(s.value); DAW.commit(); });
    return s;
  };
  const group = (title, ...kids) => h('div', { class: 'group' }, h('div', { class: 'group-title', text: title }), h('div', { class: 'group-body' }, kids));
  const WAVES = [['sine', 'Sine'], ['triangle', 'Triangle'], ['sawtooth', 'Saw'], ['square', 'Square']];

  /* ---------- instrument ---------- */
  const I = (DAW.instrument = {});
  I.init = () => { I.root = document.getElementById('tabInst'); DAW.on('change', I.render); };
  I.render = function () {
    const root = I.root, t = DAW.selTrack(), keep = root.scrollLeft;
    root.innerHTML = '';
    if (!t) { root.append(h('div', { class: 'pr-empty' }, 'Select a track to edit its instrument.')); return; }
    const title = h('div', { class: 'inst-title' }, h('span', { class: 'dot', style: { background: t.color } }), t.name);
    if (t.type === 'synth') {
      const s = t.instrument;
      const sampled = s.source !== 'synth' && DAW.SAMPLE_INSTS[s.source];
      const current = sampled ? s.source : s.preset;
      const preset = h('select', { title: 'Choose a real instrument or a synth preset' },
        DAW.SYNTH_PRESETS.includes(current) || DAW.SAMPLE_INSTS[current] ? null : h('option', { value: current }, current + ' (custom)'),
        h('optgroup', { label: 'Real instruments' }, Object.keys(DAW.SAMPLE_INSTS).map((n) => h('option', { value: n }, n))),
        h('optgroup', { label: 'Synth presets' }, DAW.SYNTH_PRESETS.map((n) => h('option', { value: n }, n))));
      preset.value = current;
      preset.addEventListener('change', () => { t.instrument = DAW.SAMPLE_INSTS[preset.value] ? DAW.makeSampled(preset.value) : DAW.makeSynth(preset.value); preset.blur(); DAW.commit(); });
      if (sampled) {
        root.append(h('div', { class: 'inst-head' }, title, h('label', {}, 'Sound ', preset), h('span', { class: 'hint', text: 'Play with your computer keyboard: see the on-screen keys above' })),
          h('div', { class: 'groups' },
            group('SOUND', K('Attack', s.amp, 'a', 0.002, 1, { log: true, fmt: F.sec, def: 0.002 }), K('Release', s.amp, 'r', 0.02, 3, { log: true, fmt: F.sec, def: sampled.release }), K('Tone', s.filter, 'cutoff', 200, 18000, { log: true, fmt: F.hz, def: 18000 }), K('Gain', s, 'gain', 0, 1.5, { fmt: F.pct, def: 0.8 })),
            group('VIBRATO', K('Rate', s.lfo, 'rate', 0.05, 20, { log: true, fmt: (v) => v.toFixed(2) + 'Hz', def: 5 }), K('Depth', s.lfo, 'pitch', 0, 100, { fmt: (v) => v.toFixed(0) + 'c', def: 0 }))),
          h('div', { class: 'hint credit', text: 'Recorded instrument: ' + sampled.credit + '. Use the mixer for EQ, drive, compression and effects.' }));
        root.scrollLeft = keep; return;
      }
      const adsr = (e) => [K('A', e, 'a', 0.001, 3, { log: true, fmt: F.sec, def: 0.005 }), K('D', e, 'd', 0.01, 4, { log: true, fmt: F.sec, def: 0.2 }), K('S', e, 's', 0, 1, { fmt: F.pct, def: 0.7 }), K('R', e, 'r', 0.01, 5, { log: true, fmt: F.sec, def: 0.2 })];
      root.append(h('div', { class: 'inst-head' }, title, h('label', {}, 'Sound ', preset), h('span', { class: 'hint', text: 'Play with your computer keyboard: see the on-screen keys above' })),
        h('div', { class: 'groups' },
          group('OSC 1', sel(s.osc1, 'wave', WAVES, 'Waveform'), K('Octave', s.osc1, 'oct', -2, 2, { step: 1, fmt: F.int, def: 0 }), K('Level', s.osc1, 'level', 0, 1, { fmt: F.pct, def: 0.8 })),
          group('OSC 2', sel(s.osc2, 'wave', WAVES, 'Waveform'), K('Octave', s.osc2, 'oct', -2, 2, { step: 1, fmt: F.int, def: 0 }), K('Detune', s.osc2, 'detune', -50, 50, { step: 1, fmt: F.int, def: 0 }), K('Level', s.osc2, 'level', 0, 1, { fmt: F.pct, def: 0.5 }), K('Noise', s, 'noise', 0, 1, { fmt: F.pct, def: 0 })),
          group('FILTER', sel(s.filter, 'type', [['lowpass', 'Low-pass'], ['highpass', 'High-pass'], ['bandpass', 'Band-pass']], 'Filter type'), K('Cutoff', s.filter, 'cutoff', 40, 18000, { log: true, fmt: F.hz, def: 2400 }), K('Reso', s.filter, 'q', 0.1, 20, { def: 1 }), K('Env', s.filter, 'env', -3, 5, { fmt: (v) => F.db(v).replace('.0', '') + ' oct', def: 0 })),
          group('AMP ENVELOPE', adsr(s.amp)),
          group('FILTER ENVELOPE', adsr(s.fenv)),
          group('UNISON', K('Voices', s.unison, 'voices', 1, 5, { step: 1, fmt: (v) => v.toFixed(0), def: 1 }), K('Detune', s.unison, 'detune', 0, 50, { fmt: (v) => v.toFixed(0) + 'c', def: 18 }), K('Spread', s.unison, 'spread', 0, 1, { fmt: F.pct, def: 0.6 })),
          group('LFO', K('Rate', s.lfo, 'rate', 0.05, 20, { log: true, fmt: (v) => v.toFixed(2) + 'Hz', def: 5 }), K('Vibrato', s.lfo, 'pitch', 0, 100, { fmt: (v) => v.toFixed(0) + 'c', def: 0 }), K('Filter', s.lfo, 'filter', 0, 2400, { fmt: (v) => v.toFixed(0) + 'c', def: 0 })),
          group('OUTPUT', K('Gain', s, 'gain', 0, 1.2, { fmt: F.pct, def: 0.7 }))));
    } else {
      const d = t.instrument;
      root.append(h('div', { class: 'inst-head' }, title, h('label', {}, 'Kit ', sel(d, 'kit', DAW.KITS, 'Drum kit')), h('span', { class: 'hint', text: 'Click a pad to hear it. Keyboard: the home row from A to \\ plays the 12 pads' })),
        h('div', { class: 'groups' }, DAW.DRUM_PADS.map((p0, i) => {
          const pp = d.pads[p0.id], pad = { name: DAW.padName(d, i) };
          const hit = h('button', { class: 'pad', title: 'Audition ' + pad.name, onpointerdown: () => DAW.engine.noteOn(t, DAW.DRUM_BASE + i, 0.9) }, pad.name);
          return h('div', { class: 'group pad-group' }, hit, h('div', { class: 'group-body' }, K('Level', pp, 'level', 0, 1.2, { fmt: F.pct, def: 0.8 }), K('Tune', pp, 'tune', -12, 12, { step: 1, fmt: F.int, def: 0 }), K('Decay', pp, 'decay', 0.3, 2.5, { fmt: (v) => v.toFixed(2) + 'x', def: 1 })));
        })));
    }
    root.scrollLeft = keep;
  };

  /* ---------- mixer ---------- */
  const M = (DAW.mixer = { meters: [] });
  M.init = () => { M.root = document.getElementById('tabMixer'); DAW.on('change', M.render); };
  function fader(obj, key, title) {
    const f = h('input', { type: 'range', class: 'vfader', min: 0, max: 1, step: 0.005, value: obj[key], title });
    const read = h('div', { class: 'fader-read', text: DAW.volDb(obj[key]) });
    f.addEventListener('input', () => { obj[key] = +f.value; read.textContent = DAW.volDb(obj[key]); live(); });
    f.addEventListener('change', () => DAW.commit());
    f.addEventListener('dblclick', () => { obj[key] = 0.8; DAW.commit(); });
    return { f, read };
  }
  function meter(get) {
    const fill = h('div', { class: 'meter-fill' }), el = h('div', { class: 'meter' }, fill);
    M.meters.push({ fill, get, level: 0, buf: null });
    return el;
  }
  M.render = function () {
    const root = M.root, p = DAW.state.project, keep = root.scrollLeft;
    root.innerHTML = ''; M.meters = M.meters.filter((m) => m.keep);
    const strips = p.tracks.map((t) => {
      const fd = fader(t, 'volume', 'Volume (double-click to reset)');
      const tb = (cls, txt, key) => h('button', { class: 'tbtn ' + cls + (t[key] ? ' on' : ''), onclick: () => { t[key] = !t[key]; DAW.commit(); } }, txt);
      return h('div', { class: 'strip' + (DAW.state.selTrackId === t.id ? ' sel' : ''), onpointerdown: (e) => { if (!e.target.closest('button, input, svg')) DAW.select(t.id, DAW.state.selTrackId === t.id ? DAW.state.selClipId : null); } },
        h('div', { class: 'strip-name', style: { borderColor: t.color }, text: t.name }),
        h('div', { class: 'strip-body' },
          h('div', { class: 'strip-knobs' },
            K('High', t.eq, 'high', -15, 15, { fmt: F.db, def: 0 }), K('Mid', t.eq, 'mid', -15, 15, { fmt: F.db, def: 0 }), K('Low', t.eq, 'low', -15, 15, { fmt: F.db, def: 0 }), K('Drive', t, 'drive', 0, 1, { fmt: F.pct, def: 0 }), K('Comp', t, 'comp', 0, 1, { fmt: F.pct, def: 0 }),
            K('Reverb', t, 'sendA', 0, 1, { fmt: F.pct, def: 0 }), K('Delay', t, 'sendB', 0, 1, { fmt: F.pct, def: 0 }), K('Pan', t, 'pan', -1, 1, { fmt: F.pan, def: 0 }),
            h('div', { class: 'strip-ms' }, tb('m', 'M', 'mute'), tb('s', 'S', 'solo'))),
          h('div', { class: 'strip-fader' }, h('div', { class: 'fader-row' }, fd.f, meter(() => { const g = DAW.engine.graph, c = g && g.channels.get(t.id); return c && c.analyser; })), fd.read)));
    });
    const mf = fader(p.master, 'volume', 'Master volume (double-click to reset)');
    const lim = h('button', { class: 'tbtn wide' + (p.master.limiter ? ' on' : ''), title: 'Master limiter', onclick: () => { p.master.limiter = !p.master.limiter; DAW.commit(); } }, 'Limiter');
    root.append(h('div', { class: 'strips' }, strips,
      h('div', { class: 'strip fx' }, h('div', { class: 'strip-name', text: 'REVERB' }), h('div', { class: 'strip-body' }, h('div', { class: 'strip-knobs two' }, K('Decay', p.reverb, 'decay', 0.3, 6, { fmt: F.sec, def: 2.2 }), K('Return', p.reverb, 'level', 0, 1.5, { fmt: F.pct, def: 0.8 })))),
      h('div', { class: 'strip fx' }, h('div', { class: 'strip-name', text: 'DELAY' }), h('div', { class: 'strip-body' }, h('div', { class: 'strip-knobs two' }, K('Feedback', p.delay, 'feedback', 0, 0.9, { fmt: F.pct, def: 0.35 }), K('Return', p.delay, 'level', 0, 1.5, { fmt: F.pct, def: 0.8 }), sel(p.delay, 'time', Object.keys(DAW.DELAY_TIMES), 'Delay time (tempo-synced)')))),
      h('div', { class: 'strip master' }, h('div', { class: 'strip-name', text: 'MASTER' }), h('div', { class: 'strip-body' }, h('div', { class: 'strip-fader' }, h('div', { class: 'fader-row' }, mf.f, meter(() => DAW.engine.graph && DAW.engine.graph.analyser)), mf.read), lim))));
    root.scrollLeft = keep;
  };
  M.addFixedMeter = (fill, get) => M.meters.push({ fill, get, level: 0, buf: null, keep: true, horizontal: true });
  M.tick = function () {
    for (const m of M.meters) {
      const an = m.get();
      let peak = 0;
      if (an) {
        if (!m.buf || m.buf.length !== an.fftSize) m.buf = new Float32Array(an.fftSize);
        an.getFloatTimeDomainData(m.buf);
        for (let i = 0; i < m.buf.length; i++) { const a = Math.abs(m.buf[i]); if (a > peak) peak = a; }
      }
      const db = peak > 0.00001 ? 20 * Math.log10(peak) : -100, n = DAW.clamp((db + 54) / 60, 0, 1);
      m.level = Math.max(n, m.level - 0.02);
      const pct = (m.level * 100).toFixed(1) + '%';
      if (m.horizontal) m.fill.style.width = pct; else m.fill.style.height = pct;
      m.fill.classList.toggle('hot', db > -0.5);
    }
  };
})();

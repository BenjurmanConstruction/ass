/* Sable — audio engine. One Graph class builds the whole signal path on any
   BaseAudioContext, so live playback and offline WAV export share the same code. */
(function () {
  'use strict';
  const DAW = window.DAW;
  const freq = (p) => 440 * Math.pow(2, (p - 69) / 12);

  const METAL = [205.3, 304.4, 369.6, 522.7, 540, 800]; // classic six-square cymbal cluster
  function makeNoise(ctx) {
    const len = ctx.sampleRate * 2, buf = ctx.createBuffer(1, len, ctx.sampleRate), d = buf.getChannelData(0);
    let s = 22222;
    for (let i = 0; i < len; i++) { s = (s * 16807) % 2147483647; d[i] = s / 1073741823.5 - 1; }
    return buf;
  }
  /* Cymbal/hat source: six detuned squares pre-mixed into one loop (one voice instead of six oscillators). */
  function makeMetal(ctx) {
    const sr = ctx.sampleRate, len = sr * 2, buf = ctx.createBuffer(1, len, sr), d = buf.getChannelData(0);
    for (const f of METAL) for (let i = 0; i < len; i++) d[i] += ((i * f) / sr) % 1 < 0.5 ? 1 / 6 : -1 / 6;
    return buf;
  }
  function makeIR(ctx, decay) {
    const sr = ctx.sampleRate, len = Math.floor(sr * Math.max(0.3, decay)), buf = ctx.createBuffer(2, len, sr);
    let s = 1234567;
    const fadeIn = sr * 0.004;
    for (let c = 0; c < 2; c++) {
      const d = buf.getChannelData(c);
      let lp = 0;
      for (let i = 0; i < len; i++) {
        s = (s * 16807) % 2147483647;
        const n = s / 1073741823.5 - 1, t = i / len;
        lp += (n - lp) * (0.9 - 0.65 * t); // highs die faster than lows
        d[i] = lp * Math.pow(1 - t, 3.2) * (i < fadeIn ? i / fadeIn : 1) * 0.5;
      }
    }
    return buf;
  }
  function driveCurve(k) {
    const n = 2048, c = new Float32Array(n), norm = Math.tanh(1 + k);
    for (let i = 0; i < n; i++) { const x = (i * 2) / (n - 1) - 1; c[i] = Math.tanh(x * (1 + k)) / norm; }
    return c;
  }
  /* ADSR value at dt seconds into a held note */
  function envAt(e, peak, dt) {
    const a = Math.max(e.a, 0.002);
    if (dt < a) return (peak * dt) / a;
    const tau = Math.max(e.d, 0.005) / 3, s = peak * e.s;
    return s + (peak - s) * Math.exp(-(dt - a) / tau);
  }
  /* Schedule an ADSR on a param. dur == null means "held until release()". */
  function schedEnv(param, e, peak, t, dur) {
    const a = Math.max(e.a, 0.002), tau = Math.max(e.d, 0.005) / 3, rel = Math.max(e.r, 0.005);
    param.setValueAtTime(0, t);
    if (dur != null && dur < a) {
      param.linearRampToValueAtTime((peak * dur) / a, t + dur);
    } else {
      param.linearRampToValueAtTime(peak, t + a);
      param.setTargetAtTime(peak * e.s, t + a, tau);
      if (dur != null) param.setValueAtTime(envAt(e, peak, dur), t + dur);
    }
    if (dur != null) param.setTargetAtTime(0, t + dur, rel / 3);
  }
  const KIT = {
    '808 Deep': { kStart: 150, kFreq: 46, kSweep: 0.1, kDecay: 0.65, click: 0.25, sTone: 175, sNoise: 0.75, sDecay: 0.2, hatHp: 7000, tone: 20000 },
    '909 Punch': { kStart: 280, kFreq: 54, kSweep: 0.045, kDecay: 0.34, click: 0.8, sTone: 200, sNoise: 1, sDecay: 0.16, hatHp: 8000, tone: 20000 },
    'Lo-Fi Dust': { kStart: 190, kFreq: 58, kSweep: 0.07, kDecay: 0.3, click: 0.4, sTone: 160, sNoise: 0.8, sDecay: 0.13, hatHp: 5000, tone: 5200 },
  };

  class Graph {
    constructor(ctx, live) {
      this.ctx = ctx; this.live = live; this.noise = makeNoise(ctx); this.metal = makeMetal(ctx); this.channels = new Map();
      const c = ctx;
      this.sum = c.createGain(); this.master = c.createGain(); this.limiter = c.createDynamicsCompressor(); this.post = c.createGain();
      this.limiter.knee.value = 0; this.limiter.attack.value = 0.002; this.limiter.release.value = 0.12;
      this.sum.connect(this.master).connect(this.limiter).connect(this.post).connect(c.destination);
      if (live) { this.analyser = c.createAnalyser(); this.analyser.fftSize = 1024; this.post.connect(this.analyser); }
      // reverb bus
      this.revIn = c.createGain(); this.conv = c.createConvolver(); this.revRet = c.createGain();
      this.revIn.connect(this.conv).connect(this.revRet).connect(this.sum);
      // ping-pong delay bus
      this.dlyIn = c.createGain(); this.dL = c.createDelay(2.5); this.dR = c.createDelay(2.5);
      this.fb = c.createGain(); this.fbLp = c.createBiquadFilter(); this.fbLp.type = 'lowpass'; this.fbLp.frequency.value = 4200;
      const pL = c.createStereoPanner(), pR = c.createStereoPanner(); pL.pan.value = -0.65; pR.pan.value = 0.65;
      this.dlyRet = c.createGain();
      this.dlyIn.connect(this.dL); this.dL.connect(this.dR); this.dR.connect(this.fbLp).connect(this.fb).connect(this.dL);
      this.dL.connect(pL).connect(this.dlyRet); this.dR.connect(pR).connect(this.dlyRet); this.dlyRet.connect(this.sum);
    }
    set(param, v) {
      if (this.live) param.setTargetAtTime(v, this.ctx.currentTime, 0.012); else param.value = v;
    }
    makeChannel() {
      const c = this.ctx, ch = {};
      ch.input = c.createGain(); ch.shaper = c.createWaveShaper(); ch.shaper.oversample = '2x'; ch.driveComp = c.createGain();
      ch.low = c.createBiquadFilter(); ch.low.type = 'lowshelf'; ch.low.frequency.value = 180;
      ch.mid = c.createBiquadFilter(); ch.mid.type = 'peaking'; ch.mid.frequency.value = 1100; ch.mid.Q.value = 0.8;
      ch.high = c.createBiquadFilter(); ch.high.type = 'highshelf'; ch.high.frequency.value = 5000;
      ch.fader = c.createGain(); ch.pan = c.createStereoPanner(); ch.mute = c.createGain();
      ch.sendA = c.createGain(); ch.sendB = c.createGain();
      ch.input.connect(ch.shaper).connect(ch.driveComp).connect(ch.low).connect(ch.mid).connect(ch.high).connect(ch.fader).connect(ch.pan).connect(ch.mute);
      ch.mute.connect(this.sum); ch.mute.connect(ch.sendA).connect(this.revIn); ch.mute.connect(ch.sendB).connect(this.dlyIn);
      if (this.live) { ch.analyser = c.createAnalyser(); ch.analyser.fftSize = 1024; ch.mute.connect(ch.analyser); }
      ch.lfo = c.createOscillator(); ch.lfoPitch = c.createGain(); ch.lfoFilter = c.createGain();
      ch.lfoPitch.gain.value = 0; ch.lfoFilter.gain.value = 0;
      ch.lfo.connect(ch.lfoPitch); ch.lfo.connect(ch.lfoFilter); ch.lfo.start();
      ch.openHats = [];
      return ch;
    }
    sync(p) {
      const ids = new Set(p.tracks.map((t) => t.id));
      for (const [id, ch] of this.channels) if (!ids.has(id)) {
        ch.mute.disconnect(); try { ch.lfo.stop(); } catch (e) { /* already stopped */ }
        this.channels.delete(id);
      }
      const anySolo = p.tracks.some((t) => t.solo);
      for (const t of p.tracks) {
        if (!this.channels.has(t.id)) this.channels.set(t.id, this.makeChannel());
        this.applyTrack(t, anySolo);
      }
      this.set(this.master.gain, DAW.volGain(p.master.volume));
      // The Web Audio compressor adds automatic make-up gain; `post` cancels it so the limiter only limits.
      const on = !!p.master.limiter;
      this.limiter.threshold.value = on ? -3 : 0; this.limiter.ratio.value = on ? 20 : 1;
      this.post.gain.value = on ? Math.pow(10, (-0.6 * (3 - 3 / 20) - 0.3) / 20) : 1;
      if (this._decay !== p.reverb.decay) { this.conv.buffer = makeIR(this.ctx, p.reverb.decay); this._decay = p.reverb.decay; }
      this.set(this.revRet.gain, p.reverb.level);
      const T = Math.min(2.4, (DAW.DELAY_TIMES[p.delay.time] || 0.5) * 60 / p.bpm);
      this.set(this.dL.delayTime, T); this.set(this.dR.delayTime, T);
      this.set(this.fb.gain, Math.min(0.92, p.delay.feedback)); this.set(this.dlyRet.gain, p.delay.level);
    }
    applyTrack(t, anySolo) {
      const ch = this.channels.get(t.id);
      if (ch._drive !== t.drive) {
        const k = t.drive * t.drive * 30;
        ch.shaper.curve = t.drive > 0.001 ? driveCurve(k) : null; // null = clean bypass
        ch.driveComp.gain.value = 1 / Math.sqrt(1 + k * 0.6);
        ch._drive = t.drive;
      }
      this.set(ch.low.gain, t.eq.low); this.set(ch.mid.gain, t.eq.mid); this.set(ch.high.gain, t.eq.high);
      this.set(ch.fader.gain, DAW.volGain(t.volume)); this.set(ch.pan.pan, t.pan);
      this.set(ch.mute.gain, t.mute || (anySolo && !t.solo) ? 0 : 1);
      this.set(ch.sendA.gain, t.sendA); this.set(ch.sendB.gain, t.sendB);
      if (t.type === 'synth') {
        const l = t.instrument.lfo;
        this.set(ch.lfo.frequency, l.rate); this.set(ch.lfoPitch.gain, l.pitch); this.set(ch.lfoFilter.gain, l.filter);
      }
    }
    /* Silence everything already scheduled on every channel (used by Stop). */
    panic() {
      const now = this.ctx.currentTime;
      for (const ch of this.channels.values()) {
        const old = ch.input;
        old.gain.setTargetAtTime(0, now, 0.008);
        setTimeout(() => old.disconnect(), 80);
        ch.input = this.ctx.createGain(); ch.input.connect(ch.shaper); ch.openHats = [];
      }
    }

    /* ---- subtractive synth voice ---- */
    synthNote(ch, p, pitch, vel, t, dur) {
      const ctx = this.ctx, f = freq(pitch), srcs = [], oscs = [];
      const filt = ctx.createBiquadFilter(), vca = ctx.createGain();
      filt.type = p.filter.type; filt.frequency.value = DAW.clamp(p.filter.cutoff, 20, 20000); filt.Q.value = p.filter.q;
      vca.gain.value = 0;
      const mkOsc = (o, det) => {
        if (o.level <= 0.001) return;
        const osc = ctx.createOscillator(), g = ctx.createGain();
        osc.type = o.wave; osc.frequency.value = f * Math.pow(2, o.oct); osc.detune.value = det || 0;
        g.gain.value = o.level * 0.5;
        osc.connect(g).connect(filt); ch.lfoPitch.connect(osc.detune);
        osc.start(t); srcs.push(osc); oscs.push(osc);
      };
      mkOsc(p.osc1, 0); mkOsc(p.osc2, p.osc2.detune);
      if (p.noise > 0.001) {
        const n = ctx.createBufferSource(), g = ctx.createGain();
        n.buffer = this.noise; n.loop = true; g.gain.value = p.noise * 0.5;
        n.connect(g).connect(filt); n.start(t, (pitch * 0.137) % 1.5); srcs.push(n);
      }
      if (!srcs.length) return null;
      ch.lfoFilter.connect(filt.detune);
      filt.connect(vca).connect(ch.input);
      const peak = Math.pow(DAW.clamp(vel, 0.01, 1), 1.5) * p.gain;
      const fpeak = p.filter.env * 1200;
      schedEnv(vca.gain, p.amp, peak, t, dur);
      schedEnv(filt.detune, p.fenv, fpeak, t, dur);
      const rel = Math.max(p.amp.r, 0.005);
      const cleanup = () => {
        vca.disconnect();
        try { ch.lfoFilter.disconnect(filt.detune); } catch (e) { /* gone */ }
        for (const o of oscs) try { ch.lfoPitch.disconnect(o.detune); } catch (e) { /* gone */ }
      };
      srcs[0].onended = cleanup;
      const stopAt = (when) => srcs.forEach((s) => { try { s.stop(when); } catch (e) { /* already stopped */ } });
      if (dur != null) { stopAt(t + dur + rel * 2.2 + 0.05); return null; }
      let released = false;
      return {
        release() {
          if (released) return; released = true;
          const now = Math.max(ctx.currentTime, t);
          for (const [prm, r] of [[vca.gain, rel], [filt.detune, Math.max(p.fenv.r, 0.005)]]) {
            if (prm.cancelAndHoldAtTime) prm.cancelAndHoldAtTime(now);
            else { const v = prm.value; prm.cancelScheduledValues(now); prm.setValueAtTime(v, now); }
            prm.setTargetAtTime(0, now, r / 3);
          }
          stopAt(now + rel * 2.2 + 0.05);
        },
      };
    }

    /* ---- synthesized drum machine ---- */
    drumHit(ch, inst, idx, vel, t) {
      const pad = DAW.DRUM_PADS[idx];
      if (!pad) return;
      const ctx = this.ctx, pp = inst.pads[pad.id] || { level: 0.8, tune: 0, decay: 1 }, kit = KIT[inst.kit] || KIT['808 Deep'];
      const tune = Math.pow(2, pp.tune / 12), dec = pp.decay;
      const out = ctx.createGain(); out.gain.value = pp.level * (0.25 + 0.75 * vel * vel) * 1.1;
      if (kit.tone < 19000) { const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = kit.tone; out.connect(lp).connect(ch.input); out._tail = lp; }
      else out.connect(ch.input);
      const srcs = [];
      let last = t;
      const run = (src, d) => { src.start(t); src.stop(t + d + 0.05); srcs.push(src); last = Math.max(last, t + d); return src; };
      const osc = (type, f, d) => { const o = ctx.createOscillator(); o.type = type; o.frequency.value = f; return run(o, d); };
      const noise = (d) => { const n = ctx.createBufferSource(); n.buffer = this.noise; n.loop = true; n.start(t, (t * 7.31) % 1.7); n.stop(t + d + 0.05); srcs.push(n); last = Math.max(last, t + d); return n; };
      const env = (peak, d, t0) => { const g = ctx.createGain(); t0 = t0 == null ? t : t0; g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(peak, t0 + 0.001); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.001 + d); return g; };
      const filter = (type, f, q) => { const b = ctx.createBiquadFilter(); b.type = type; b.frequency.value = f; if (q) b.Q.value = q; return b; };
      const metal = (d, base, bp, hp, peak) => {
        const mix = ctx.createBufferSource(); mix.buffer = this.metal; mix.loop = true; mix.playbackRate.value = (base / 40) * tune;
        mix.start(t, (t * 3.17) % 1.5); mix.stop(t + d + 0.05); srcs.push(mix); last = Math.max(last, t + d);
        const g = env(peak * 4.5, d);
        mix.connect(filter('bandpass', bp, 0.6)).connect(filter('highpass', hp)).connect(g).connect(out);
        return g;
      };
      const tom = (f, d) => {
        const o = osc('sine', f * tune, d);
        o.frequency.setValueAtTime(f * tune * 1.9, t); o.frequency.exponentialRampToValueAtTime(f * tune, t + 0.09);
        o.connect(env(0.95, d)).connect(out);
      };
      switch (pad.id) {
        case 'kick': {
          const d = kit.kDecay * dec, o = osc('sine', kit.kFreq * tune, d);
          o.frequency.setValueAtTime(kit.kStart * tune, t); o.frequency.exponentialRampToValueAtTime(kit.kFreq * tune, t + kit.kSweep);
          o.connect(env(1.15, d)).connect(out);
          osc('triangle', 1400, 0.02).connect(env(kit.click * 0.5, 0.012)).connect(out);
          break;
        }
        case 'snare': {
          const d = kit.sDecay * dec, o = osc('triangle', kit.sTone * tune, d * 0.7);
          o.frequency.setValueAtTime(kit.sTone * tune * 1.8, t); o.frequency.exponentialRampToValueAtTime(kit.sTone * tune, t + 0.03);
          o.connect(env(0.6, d * 0.7)).connect(out);
          noise(d).connect(filter('highpass', 1400)).connect(env(kit.sNoise * 0.75, d)).connect(out);
          break;
        }
        case 'clap': {
          const d = 0.22 * dec, g = ctx.createGain(), n = noise(d + 0.04);
          g.gain.setValueAtTime(0, t);
          for (let i = 0; i < 3; i++) { const b = t + i * 0.011; g.gain.setValueAtTime(0.9, b); g.gain.exponentialRampToValueAtTime(0.15, b + 0.01); }
          g.gain.setValueAtTime(0.9, t + 0.033); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.033 + d);
          n.connect(filter('bandpass', 1150 * tune, 1.3)).connect(filter('highpass', 600)).connect(g).connect(out);
          break;
        }
        case 'rim': {
          const g = env(0.7, 0.03 * dec);
          osc('triangle', 1650 * tune, 0.05).connect(g); osc('square', 480 * tune, 0.05).connect(g);
          g.connect(filter('bandpass', 1800 * tune, 2)).connect(out);
          break;
        }
        case 'chat': {
          metal(0.045 * dec, 40, 10000, kit.hatHp, 0.7);
          if (ch.openHats.length) {
            ch.openHats = ch.openHats.filter((o) => o.end > t - 0.5);
            for (const o of ch.openHats) if (o.t < t && o.end > t && o.g.gain.cancelAndHoldAtTime) { o.g.gain.cancelAndHoldAtTime(t); o.g.gain.setTargetAtTime(0, t, 0.006); }
          }
          break;
        }
        case 'ohat': { const d = 0.32 * dec; ch.openHats.push({ g: metal(d, 40, 10000, kit.hatHp, 0.6), t, end: t + d }); break; }
        case 'ltom': tom(88, 0.4 * dec); break;
        case 'mtom': tom(128, 0.33 * dec); break;
        case 'htom': tom(180, 0.27 * dec); break;
        case 'cow': {
          const d = 0.28 * dec, g = env(0.55, d);
          osc('square', 540 * tune, d).connect(g); osc('square', 800 * tune, d).connect(g);
          g.connect(filter('bandpass', 2640 * tune, 2.5)).connect(out);
          break;
        }
        case 'crash': {
          const d = 1.5 * dec;
          metal(d, 43, 8000, 4500, 0.5);
          noise(d).connect(filter('highpass', 6000)).connect(env(0.35, d * 0.8)).connect(out);
          break;
        }
        case 'ride': { metal(0.8 * dec, 52, 9000, 6500, 0.35); osc('triangle', 5200 * tune, 0.03).connect(env(0.12, 0.02)).connect(out); break; }
      }
      let left = srcs.length;
      for (const s of srcs) s.onended = () => { if (--left === 0) (out._tail || out).disconnect(); };
    }
  }
  DAW.Graph = Graph;

  /* Schedule every note whose start falls in [b0, b1). timeOf maps beats to context time. */
  DAW.scheduleRange = function (graph, p, b0, b1, timeOf) {
    const spb = 60 / p.bpm, sw = p.swing / 6;
    for (const t of p.tracks) {
      const ch = graph.channels.get(t.id);
      if (!ch) continue;
      const evs = [];
      for (const c of t.clips) {
        if (c.start >= b1 || c.start + c.length <= b0) continue;
        for (const n of c.notes) {
          if (n.start < 0 || n.start >= c.length) continue;
          const b = c.start + n.start;
          if (b < b0 || b >= b1) continue;
          evs.push({ b, n, len: Math.min(n.length, c.length - n.start) });
        }
      }
      evs.sort((x, y) => x.b - y.b);
      for (const e of evs) {
        const off = Math.floor(e.b * 4 + 1e-4) % 2 === 1 ? sw : 0;
        const time = timeOf(e.b + off);
        if (t.type === 'drums') graph.drumHit(ch, t.instrument, e.n.pitch - DAW.DRUM_BASE, e.n.vel, time);
        else graph.synthNote(ch, t.instrument, e.n.pitch, e.n.vel, time, Math.max(0.02, e.len * spb));
      }
    }
  };

  /* ---- realtime transport ---- */
  const LOOKAHEAD = 0.14;
  const E = (DAW.engine = {
    ctx: null, graph: null, playing: false, segs: [],
    ensure() {
      if (!this.ctx) {
        this.ctx = new (window.AudioContext || window.webkitAudioContext)({ latencyHint: 'interactive' });
        this.graph = new Graph(this.ctx, true);
        this.graph.sync(DAW.state.project);
      }
      if (this.ctx.state !== 'running') this.ctx.resume();
      return this.graph;
    },
    sync() { if (this.graph) this.graph.sync(DAW.state.project); },
    play(from) {
      this.ensure();
      if (this.playing) this.stop();
      this.cursorBeat = from; this.cursorTime = this.ctx.currentTime + 0.06;
      this.segs = [{ t: this.cursorTime, b: from, spb: 60 / DAW.state.project.bpm }];
      this.playing = true;
      this.timer = setInterval(() => this.tick(), 25);
      this.tick();
    },
    stop() {
      if (!this.playing) return;
      clearInterval(this.timer); this.playing = false;
      this.graph.panic();
    },
    tick() {
      const p = DAW.state.project, ctx = this.ctx, spb = 60 / p.bpm, now = ctx.currentTime, horizon = now + LOOKAHEAD;
      const loopOn = p.loop.on && p.loop.end - p.loop.start >= 0.25;
      let seg = this.segs[this.segs.length - 1];
      if (this.cursorTime < now) { // timer stalled: skip ahead rather than burst late notes
        this.cursorBeat += (now + 0.02 - this.cursorTime) / spb; this.cursorTime = now + 0.02;
        if (loopOn && this.cursorBeat >= p.loop.end) this.cursorBeat = p.loop.start + ((this.cursorBeat - p.loop.start) % (p.loop.end - p.loop.start));
        this.segs.push((seg = { t: this.cursorTime, b: this.cursorBeat, spb }));
      } else if (seg.spb !== spb) this.segs.push((seg = { t: this.cursorTime, b: this.cursorBeat, spb }));
      let guard = 0;
      while (this.cursorTime < horizon && guard++ < 256) {
        const b0 = this.cursorBeat, t0 = this.cursorTime;
        let b1 = b0 + (horizon - t0) / spb, wrap = false;
        if (loopOn && b0 < p.loop.end && b1 >= p.loop.end) { b1 = p.loop.end; wrap = true; }
        const timeOf = (b) => t0 + (b - b0) * spb;
        DAW.scheduleRange(this.graph, p, b0, b1, timeOf);
        if (DAW.state.metronome) for (let b = Math.ceil(b0 - 1e-9); b < b1; b++) this.click(timeOf(b), b % 4 === 0);
        this.cursorTime = timeOf(b1); this.cursorBeat = wrap ? p.loop.start : b1;
        if (wrap) this.segs.push({ t: this.cursorTime, b: p.loop.start, spb });
      }
      while (this.segs.length > 2 && this.segs[1].t < now - 1) this.segs.shift();
    },
    click(t, accent) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.frequency.value = accent ? 1760 : 1175;
      g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(accent ? 0.3 : 0.18, t + 0.001); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.05);
      o.connect(g).connect(c.destination); o.start(t); o.stop(t + 0.07);
    },
    currentBeat() {
      if (!this.playing) return 0;
      const now = this.ctx.currentTime;
      let s = this.segs[0];
      for (const x of this.segs) if (x.t <= now) s = x;
      return s.b + Math.max(0, now - s.t) / s.spb;
    },
    noteOn(track, pitch, vel) {
      const g = this.ensure(), ch = g.channels.get(track.id);
      if (!ch) return null;
      const t = this.ctx.currentTime + 0.004;
      if (track.type === 'drums') { g.drumHit(ch, track.instrument, pitch - DAW.DRUM_BASE, vel == null ? 0.85 : vel, t); return null; }
      return g.synthNote(ch, track.instrument, pitch, vel == null ? 0.8 : vel, t, null);
    },
    noteOff(v) { if (v) v.release(); },
    /* Offline bounce through the same graph. Returns an AudioBuffer. */
    async render(o) {
      const p = DAW.state.project, spb = 60 / p.bpm, sr = o.sampleRate || 44100;
      const dur = (o.endBeat - o.startBeat) * spb + (o.tail == null ? 3 : o.tail) + 0.05;
      const octx = new OfflineAudioContext(2, Math.ceil(dur * sr), sr);
      const g = new Graph(octx, false);
      g.sync(p);
      DAW.scheduleRange(g, p, o.startBeat, o.endBeat, (b) => 0.02 + (b - o.startBeat) * spb);
      return octx.startRendering();
    },
  });

  DAW.encodeWav = function (buf, bits) {
    const ch = buf.numberOfChannels, n = buf.length, bps = bits === 24 ? 3 : 2, size = n * ch * bps;
    const ab = new ArrayBuffer(44 + size), v = new DataView(ab);
    const str = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    str(0, 'RIFF'); v.setUint32(4, 36 + size, true); str(8, 'WAVE'); str(12, 'fmt '); v.setUint32(16, 16, true);
    v.setUint16(20, 1, true); v.setUint16(22, ch, true); v.setUint32(24, buf.sampleRate, true);
    v.setUint32(28, buf.sampleRate * ch * bps, true); v.setUint16(32, ch * bps, true); v.setUint16(34, bps * 8, true);
    str(36, 'data'); v.setUint32(40, size, true);
    const data = []; for (let c = 0; c < ch; c++) data.push(buf.getChannelData(c));
    let o = 44;
    for (let i = 0; i < n; i++) for (let c = 0; c < ch; c++) {
      const s = Math.max(-1, Math.min(1, data[c][i]));
      if (bps === 2) { v.setInt16(o, Math.round(s * 32767), true); o += 2; }
      else { const x = Math.round(s * 8388607); v.setUint8(o, x & 255); v.setUint8(o + 1, (x >> 8) & 255); v.setUint8(o + 2, (x >> 16) & 255); o += 3; }
    }
    return new Uint8Array(ab);
  };
})();

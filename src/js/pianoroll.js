/* Sable — piano roll / drum grid editor (canvas) */
(function () {
  'use strict';
  const DAW = window.DAW, h = DAW.h, clamp = DAW.clamp;
  const KEY_W = 72, RULER_H = 20, VEL_H = 62;
  const P = (DAW.pianoroll = { ppb: 96, tool: 'draw', sel: new Set(), lastLen: 0.25, clipboard: null });
  let root, bar, scroller, spacer, canvas, g, empty, vw = 0, vh = 0, dpr = 1, lastClip = null, marquee = null, info;

  const st = () => DAW.state;
  const cur = () => DAW.findClip(st().selClipId);
  const geom = (track) => (track.type === 'drums'
    ? { drum: true, rowH: 22, hi: DAW.DRUM_BASE + 11, lo: DAW.DRUM_BASE, rows: 12 }
    : { drum: false, rowH: 15, hi: 108, lo: 24, rows: 85 });
  const css = (n) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();

  P.init = function () {
    root = document.getElementById('tabPiano');
    const tool = (id, label, title) => h('button', { class: 'pr-tool', 'data-tool': id, title, onclick: () => { P.tool = id; syncBar(); } }, label);
    info = h('span', { class: 'pr-info' });
    bar = h('div', { class: 'pr-bar' },
      tool('draw', '✎ Draw', 'Draw tool: click to add notes, drag to set length, right-click to erase'),
      tool('select', '⬚ Select', 'Select tool: drag a box around notes (or Shift-drag with the draw tool)'),
      h('span', { class: 'sep' }),
      h('button', { title: 'Snap selected notes (or all notes) to the grid', onclick: quantize }, 'Quantize'),
      h('span', { class: 'sep' }),
      h('span', { class: 'pr-lab', text: 'Clip length' }),
      h('button', { class: 'mini', title: 'Shorten clip by one bar', onclick: () => clipLen(-4) }, '−'),
      h('span', { class: 'pr-len', id: 'prLen' }),
      h('button', { class: 'mini', title: 'Lengthen clip by one bar', onclick: () => clipLen(4) }, '+'),
      h('span', { class: 'sep' }),
      h('button', { class: 'mini', title: 'Zoom out', onclick: () => zoom(1 / 1.3) }, '−'),
      h('span', { class: 'pr-lab', text: 'Zoom' }),
      h('button', { class: 'mini', title: 'Zoom in', onclick: () => zoom(1.3) }, '+'),
      info);
    canvas = h('canvas', { class: 'pr-canvas' });
    spacer = h('div', { class: 'pr-spacer' }, canvas);
    scroller = h('div', { class: 'pr-scroll' }, spacer);
    empty = h('div', { class: 'pr-empty' }, 'No clip selected. Double-click an empty spot in a track lane to create one, or click an existing clip.');
    root.append(bar, scroller, empty);
    g = canvas.getContext('2d');
    scroller.addEventListener('scroll', P.draw);
    new ResizeObserver(resize).observe(scroller);
    canvas.addEventListener('pointerdown', down);
    canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    canvas.addEventListener('pointermove', hover);
    scroller.addEventListener('wheel', (e) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault(); zoom(e.deltaY < 0 ? 1.2 : 1 / 1.2, e.clientX - canvas.getBoundingClientRect().left);
    }, { passive: false });
    DAW.on('change', P.refresh);
  };

  function syncBar() {
    bar.querySelectorAll('.pr-tool').forEach((b) => b.classList.toggle('on', b.dataset.tool === P.tool));
    const r = cur();
    document.getElementById('prLen').textContent = r ? (r.clip.length / 4).toFixed(r.clip.length % 4 ? 2 : 0) + ' bars' : '—';
  }
  function zoom(f, anchorX) {
    const ax = anchorX == null ? vw / 2 : anchorX, beat = (scroller.scrollLeft + ax - KEY_W) / P.ppb;
    P.ppb = clamp(P.ppb * f, 20, 480); layout();
    scroller.scrollLeft = beat * P.ppb - ax + KEY_W; P.draw();
  }
  function clipLen(d) {
    const r = cur(); if (!r) return;
    const len = Math.max(1, Math.round((r.clip.length + d) * 4) / 4);
    if (!DAW.clipFits(r.track, r.clip.start, len, r.clip)) { DAW.toast('No room to lengthen: the next clip is in the way'); return; }
    r.clip.length = len; DAW.commit();
  }
  function quantize() {
    const r = cur(); if (!r) return;
    const s = st().snap || 0.25;
    for (const n of r.clip.notes) if (!P.sel.size || P.sel.has(n.id)) n.start = clamp(Math.round(n.start / s) * s, 0, Math.max(0, r.clip.length - s));
    DAW.commit();
  }
  function layout() {
    const r = cur(); if (!r) return;
    const G = geom(r.track);
    spacer.style.width = KEY_W + r.clip.length * P.ppb + 160 + 'px';
    spacer.style.height = RULER_H + G.rows * G.rowH + VEL_H + 'px';
  }
  function resize() {
    vw = scroller.clientWidth; vh = scroller.clientHeight; dpr = window.devicePixelRatio || 1;
    if (!vw || !vh) return;
    canvas.width = Math.round(vw * dpr); canvas.height = Math.round(vh * dpr);
    canvas.style.width = vw + 'px'; canvas.style.height = vh + 'px';
    P.draw();
  }
  P.refresh = function () {
    const r = cur();
    scroller.style.display = r ? '' : 'none'; empty.style.display = r ? 'none' : '';
    syncBar();
    if (!r) { lastClip = null; return; }
    const ids = new Set(r.clip.notes.map((n) => n.id));
    for (const id of [...P.sel]) if (!ids.has(id)) P.sel.delete(id);
    layout();
    if (lastClip !== r.clip.id) {
      lastClip = r.clip.id; P.sel.clear();
      resize();
      const G = geom(r.track);
      let mid = 60;
      if (r.clip.notes.length) mid = r.clip.notes.reduce((a, n) => a + n.pitch, 0) / r.clip.notes.length;
      scroller.scrollTop = G.drum ? 0 : (G.hi - mid) * G.rowH - (vh - RULER_H - VEL_H) / 2;
      scroller.scrollLeft = 0;
    }
    P.draw();
  };

  /* ---------- drawing ---------- */
  P.draw = function () {
    const r = cur();
    if (!r || !vw || !vh || scroller.style.display === 'none') return;
    const { track, clip } = r, G = geom(track), sx = scroller.scrollLeft, sy = scroller.scrollTop;
    const gridTop = RULER_H, gridBot = vh - VEL_H, gridH = gridBot - gridTop, x0 = KEY_W - sx;
    const C = P._C || (P._C = { bg: css('--pr-bg'), black: css('--pr-black'), line: css('--pr-line'), beat: css('--pr-beat'), bar: css('--pr-bar'), text: css('--dim'), panel: css('--panel'), accent: css('--accent') });
    g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, vw, vh);
    g.font = '10px ui-monospace, Menlo, Consolas, monospace'; g.textBaseline = 'middle';

    g.save(); g.beginPath(); g.rect(KEY_W, gridTop, vw - KEY_W, gridH); g.clip();
    g.fillStyle = C.bg; g.fillRect(KEY_W, gridTop, vw, gridH);
    const row0 = Math.max(0, Math.floor(sy / G.rowH));
    for (let row = row0; row < G.rows; row++) {
      const y = gridTop + row * G.rowH - sy; if (y > gridBot) break;
      const pitch = G.hi - row;
      if (G.drum ? row % 2 : DAW.isBlack(pitch)) { g.fillStyle = C.black; g.fillRect(KEY_W, y, vw, G.rowH); }
      g.fillStyle = !G.drum && pitch % 12 === 11 ? C.beat : C.line; g.fillRect(KEY_W, y, vw, 1);
    }
    let step = st().snap || 0.25; while (step * P.ppb < 7) step *= 2;
    for (let i = Math.max(0, Math.floor(sx / P.ppb / step)); ; i++) {
      const b = i * step, x = Math.round(x0 + b * P.ppb); if (x > vw) break; if (x < KEY_W) continue;
      const isBar = Math.abs(b / 4 - Math.round(b / 4)) < 1e-4, isBeat = Math.abs(b - Math.round(b)) < 1e-4;
      g.fillStyle = isBar ? C.bar : isBeat ? C.beat : C.line; g.fillRect(x, gridTop, 1, gridH);
    }
    const xEnd = x0 + clip.length * P.ppb;
    if (xEnd < vw) { g.fillStyle = 'rgba(0,0,0,.5)'; g.fillRect(Math.max(KEY_W, xEnd), gridTop, vw, gridH); }
    for (const n of clip.notes) {
      const x = x0 + n.start * P.ppb, w = Math.max(4, n.length * P.ppb - 1), y = gridTop + (G.hi - n.pitch) * G.rowH - sy;
      if (x > vw || x + w < KEY_W || y > gridBot || y + G.rowH < gridTop) continue;
      const selected = P.sel.has(n.id);
      g.globalAlpha = 0.45 + 0.55 * n.vel; g.fillStyle = track.color; g.fillRect(x, y + 1, w, G.rowH - 2); g.globalAlpha = 1;
      g.fillStyle = 'rgba(0,0,0,.35)'; g.fillRect(x, y + 1, 2, G.rowH - 2);
      if (selected) { g.strokeStyle = '#fff'; g.lineWidth = 1.5; g.strokeRect(x + 0.75, y + 1.75, w - 1.5, G.rowH - 3.5); }
    }
    if (marquee) { g.fillStyle = 'rgba(255,255,255,.08)'; g.strokeStyle = 'rgba(255,255,255,.6)'; g.lineWidth = 1; const m = marquee; g.fillRect(m.x, m.y, m.w, m.h); g.strokeRect(m.x + 0.5, m.y + 0.5, m.w, m.h); }
    g.restore();

    /* velocity lane */
    g.save(); g.beginPath(); g.rect(KEY_W, gridBot, vw - KEY_W, VEL_H); g.clip();
    g.fillStyle = C.panel; g.fillRect(KEY_W, gridBot, vw, VEL_H);
    for (const n of clip.notes) {
      const x = x0 + n.start * P.ppb; if (x > vw || x < KEY_W - 6) continue;
      const hh = n.vel * (VEL_H - 10);
      g.fillStyle = P.sel.has(n.id) ? '#fff' : track.color; g.fillRect(x, vh - 2 - hh, 4, hh); g.fillRect(x - 1, vh - 4 - hh, 6, 3);
    }
    g.restore();
    g.fillStyle = C.bar; g.fillRect(KEY_W, gridBot, vw, 1);

    /* ruler */
    g.fillStyle = C.panel; g.fillRect(0, 0, vw, RULER_H);
    g.save(); g.beginPath(); g.rect(KEY_W, 0, vw - KEY_W, RULER_H); g.clip();
    for (let b = Math.max(0, Math.floor(sx / P.ppb)); ; b++) {
      const x = Math.round(x0 + b * P.ppb); if (x > vw) break;
      const isBar = b % 4 === 0;
      g.fillStyle = C.bar; g.fillRect(x, isBar ? 4 : 13, 1, RULER_H);
      if (isBar || P.ppb > 60) { g.fillStyle = C.text; g.fillText(Math.floor((clip.start + b) / 4) + 1 + (isBar ? '' : '.' + ((b % 4) + 1)), x + 4, 9); }
    }
    g.restore();

    /* playhead */
    const pb = DAW.transport.beat() - clip.start;
    if (pb >= 0 && pb <= clip.length) { const x = x0 + pb * P.ppb; if (x >= KEY_W) { g.fillStyle = C.accent; g.fillRect(Math.round(x), 0, 1, vh); } }

    /* keyboard / pad names */
    g.save(); g.beginPath(); g.rect(0, gridTop, KEY_W, gridH); g.clip();
    for (let row = row0; row < G.rows; row++) {
      const y = gridTop + row * G.rowH - sy; if (y > gridBot) break;
      const pitch = G.hi - row;
      if (G.drum) {
        g.fillStyle = row % 2 ? '#2a2620' : '#322d26'; g.fillRect(0, y, KEY_W, G.rowH);
        g.fillStyle = '#d8d0c0'; g.fillText(DAW.DRUM_PADS[pitch - DAW.DRUM_BASE].name, 6, y + G.rowH / 2 + 0.5);
      } else {
        const black = DAW.isBlack(pitch);
        g.fillStyle = black ? '#191713' : '#e4dccb'; g.fillRect(0, y, black ? KEY_W * 0.62 : KEY_W, G.rowH);
        if (black) { g.fillStyle = '#e4dccb'; g.fillRect(KEY_W * 0.62, y, KEY_W, G.rowH); }
        if (pitch % 12 === 0) { g.fillStyle = '#5a5347'; g.fillText(DAW.noteName(pitch), KEY_W - 24, y + G.rowH / 2 + 0.5); }
      }
      g.fillStyle = 'rgba(0,0,0,.25)'; g.fillRect(0, y, KEY_W, 1);
    }
    g.restore();
    g.fillStyle = C.panel; g.fillRect(0, gridBot, KEY_W, VEL_H); g.fillRect(0, 0, KEY_W, RULER_H);
    g.fillStyle = C.text; g.fillText('VELOCITY', 8, gridBot + 12);
    g.fillStyle = C.bar; g.fillRect(KEY_W - 1, 0, 1, vh);
  };

  /* ---------- interaction ---------- */
  const local = (e) => { const b = canvas.getBoundingClientRect(); return { x: e.clientX - b.left, y: e.clientY - b.top }; };
  const beatAt = (x) => (x - KEY_W + scroller.scrollLeft) / P.ppb;
  const pitchAt = (y, G) => clamp(G.hi - Math.floor((y - RULER_H + scroller.scrollTop) / G.rowH), G.lo, G.hi);
  function noteAt(clip, beat, pitch) {
    for (let i = clip.notes.length - 1; i >= 0; i--) { const n = clip.notes[i]; if (n.pitch === pitch && beat >= n.start && beat < n.start + Math.max(n.length, 4 / P.ppb)) return n; }
    return null;
  }
  let pv = null;
  function preview(track, pitch, vel) {
    if (pv) DAW.engine.noteOff(pv);
    pv = DAW.engine.noteOn(track, pitch, vel == null ? 0.8 : vel);
    const mine = pv; setTimeout(() => { if (pv === mine) { DAW.engine.noteOff(mine); pv = null; } }, 220);
  }
  function hover(e) {
    const r = cur(); if (!r || e.buttons) return;
    const { x, y } = local(e), G = geom(r.track);
    let c = 'default';
    if (x >= KEY_W && y >= RULER_H && y < vh - VEL_H) {
      const n = noteAt(r.clip, beatAt(x), pitchAt(y, G));
      if (n) c = !G.drum && (n.start + n.length) * P.ppb - (x - KEY_W + scroller.scrollLeft) < 7 && n.length * P.ppb > 12 ? 'ew-resize' : 'move';
      else c = P.tool === 'draw' ? 'crosshair' : 'default';
      info.textContent = G.drum ? DAW.DRUM_PADS[pitchAt(y, G) - DAW.DRUM_BASE].name : DAW.noteName(pitchAt(y, G));
    } else if (y >= vh - VEL_H && x >= KEY_W) c = 'ns-resize';
    else if (x < KEY_W) c = 'pointer';
    canvas.style.cursor = c;
  }

  function down(e) {
    st().focus = 'piano';
    const r = cur(); if (!r) return;
    const { track, clip } = r, G = geom(track), { x, y } = local(e);
    e.preventDefault();
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();

    if (y < RULER_H && x >= KEY_W) { // seek
      const seek = (ev) => DAW.transport.seek(clip.start + clamp(DAW.snapRound(beatAt(local(ev).x)), 0, clip.length));
      seek(e); DAW.drag(e, seek); return;
    }
    if (x < KEY_W && y >= RULER_H && y < vh - VEL_H) { // audition keys
      let p = pitchAt(y, G), v = DAW.engine.noteOn(track, p, 0.8);
      DAW.drag(e, (ev) => { const q = pitchAt(local(ev).y, G); if (q !== p) { DAW.engine.noteOff(v); p = q; v = DAW.engine.noteOn(track, p, 0.8); } }, () => DAW.engine.noteOff(v));
      return;
    }
    if (y >= vh - VEL_H && x >= KEY_W) { // velocity lane
      const setVel = (ev) => {
        const m = local(ev), b = beatAt(m.x), vel = clamp(1 - (m.y - (vh - VEL_H) - 6) / (VEL_H - 10), 0.05, 1);
        for (const n of clip.notes) if (Math.abs((n.start - b) * P.ppb + 2) < 7 && (!P.sel.size || P.sel.has(n.id))) n.vel = vel;
        P.draw();
      };
      setVel(e); DAW.drag(e, setVel, () => DAW.commit()); return;
    }
    if (x < KEY_W) return;

    const beat = beatAt(x), pitch = pitchAt(y, G), hit = noteAt(clip, beat, pitch), snap = st().snap, minLen = snap || 0.0625;
    if (e.button === 2) { // erase
      const erase = (ev) => { const m = local(ev), n = noteAt(clip, beatAt(m.x), pitchAt(m.y, G)); if (n) { clip.notes.splice(clip.notes.indexOf(n), 1); P.sel.delete(n.id); P.draw(); } };
      erase(e); DAW.drag(e, erase, () => DAW.commit()); return;
    }
    if (e.button !== 0) return;
    if (hit) {
      if (e.shiftKey) { if (P.sel.has(hit.id)) P.sel.delete(hit.id); else P.sel.add(hit.id); P.draw(); return; }
      if (!P.sel.has(hit.id)) P.sel = new Set([hit.id]);
      const group = clip.notes.filter((n) => P.sel.has(n.id)).map((n) => ({ n, start: n.start, pitch: n.pitch, length: n.length }));
      const edge = !G.drum && (hit.start + hit.length - beat) * P.ppb < 7 && hit.length * P.ppb > 12;
      let lastP = hit.pitch;
      preview(track, hit.pitch, hit.vel); P.draw();
      DAW.drag(e, (ev) => {
        const m = local(ev);
        let db = beatAt(m.x) - beat; if (snap && !ev.altKey) db = Math.round(db / snap) * snap;
        if (edge) { for (const o of group) o.n.length = Math.max(minLen, o.length + db); }
        else {
          let dp = pitchAt(m.y, G) - pitch;
          for (const o of group) { db = Math.max(db, -o.start); db = Math.min(db, clip.length - minLen - o.start); dp = clamp(dp, G.lo - o.pitch, G.hi - o.pitch); }
          for (const o of group) { o.n.start = o.start + db; o.n.pitch = o.pitch + dp; }
          if (hit.pitch !== lastP) { lastP = hit.pitch; preview(track, hit.pitch, hit.vel); }
        }
        P.draw();
      }, () => { if (!G.drum) P.lastLen = hit.length; DAW.commit(); });
      return;
    }
    if (P.tool === 'select' || e.shiftKey) { // marquee
      const base = e.shiftKey ? new Set(P.sel) : new Set(), ox = x + scroller.scrollLeft, oy = y + scroller.scrollTop;
      if (!e.shiftKey) P.sel = new Set();
      DAW.drag(e, (ev) => {
        const m = local(ev), cx = m.x + scroller.scrollLeft, cy = m.y + scroller.scrollTop;
        const x1 = Math.min(ox, cx), x2 = Math.max(ox, cx), y1 = Math.min(oy, cy), y2 = Math.max(oy, cy);
        marquee = { x: x1 - scroller.scrollLeft, y: y1 - scroller.scrollTop, w: x2 - x1, h: y2 - y1 };
        const b1 = (x1 - KEY_W) / P.ppb, b2 = (x2 - KEY_W) / P.ppb, pHi = G.hi - Math.floor((y1 - RULER_H) / G.rowH), pLo = G.hi - Math.floor((y2 - RULER_H) / G.rowH);
        P.sel = new Set(base);
        for (const n of clip.notes) if (n.start < b2 && n.start + n.length > b1 && n.pitch <= pHi && n.pitch >= pLo) P.sel.add(n.id);
        P.draw();
      }, () => { marquee = null; P.draw(); });
      P.draw(); return;
    }
    /* draw a new note */
    const grid = snap || 0.25, start = snap ? DAW.snapFloor(beat) : beat;
    if (start < 0 || start >= clip.length) return;
    const n = DAW.note(pitch, start, G.drum ? Math.min(grid, 0.25) : Math.min(P.lastLen, clip.length - start), 0.8);
    clip.notes.push(n); P.sel = new Set([n.id]); preview(track, pitch, 0.8); P.draw();
    DAW.drag(e, (ev) => {
      if (G.drum) return;
      const b = beatAt(local(ev).x);
      if (b - start > n.length || b < start + n.length - grid) { n.length = clamp(Math.ceil((b - start) / grid) * grid, minLen, clip.length - start); P.draw(); }
    }, () => { if (!G.drum) P.lastLen = n.length; DAW.commit(); });
  }

  /* keyboard actions while the piano roll has focus */
  P.key = function (e) {
    const r = cur(); if (!r) return false;
    const { clip, track } = r, G = geom(track), mod = e.ctrlKey || e.metaKey, k = e.key.toLowerCase();
    const selNotes = () => clip.notes.filter((n) => P.sel.has(n.id));
    if (e.key === 'Delete' || e.key === 'Backspace') { if (!P.sel.size) return false; clip.notes = clip.notes.filter((n) => !P.sel.has(n.id)); P.sel.clear(); DAW.commit(); return true; }
    if (mod && k === 'a') { P.sel = new Set(clip.notes.map((n) => n.id)); P.draw(); return true; }
    if (e.key === 'Escape') { P.sel.clear(); P.draw(); return true; }
    if ((k === 'c' || k === 'x') && mod) {
      const s = selNotes(); if (!s.length) return false;
      const min = Math.min(...s.map((n) => n.start));
      P.clipboard = { drum: G.drum, span: Math.max(...s.map((n) => n.start + n.length)) - min, notes: s.map((n) => ({ pitch: n.pitch, start: n.start - min, length: n.length, vel: n.vel })) };
      if (k === 'x') { clip.notes = clip.notes.filter((n) => !P.sel.has(n.id)); P.sel.clear(); DAW.commit(); } else DAW.toast(s.length + ' note' + (s.length > 1 ? 's' : '') + ' copied');
      return true;
    }
    if (mod && (k === 'v' || k === 'd')) {
      let src = P.clipboard, at;
      if (k === 'd') {
        const s = selNotes(); if (!s.length) return false;
        const min = Math.min(...s.map((n) => n.start)), max = Math.max(...s.map((n) => n.start + n.length));
        src = { drum: G.drum, notes: s.map((n) => ({ pitch: n.pitch, start: n.start - min, length: n.length, vel: n.vel })) };
        at = min + (st().snap ? Math.ceil((max - min) / st().snap - 1e-6) * st().snap : max - min);
      } else {
        if (!src) return false;
        if (src.drum !== G.drum) { DAW.toast('Those notes were copied from a different kind of track'); return true; }
        const pb = DAW.transport.beat() - clip.start;
        at = pb >= 0 && pb < clip.length ? DAW.snapRound(pb) : 0;
      }
      const added = [];
      for (const o of src.notes) { const s = at + o.start; if (s >= clip.length) continue; const n = DAW.note(clamp(o.pitch, G.lo, G.hi), s, o.length, o.vel); clip.notes.push(n); added.push(n.id); }
      if (!added.length) { DAW.toast('No room left in this clip. Lengthen it first'); return true; }
      P.sel = new Set(added); DAW.commit(); return true;
    }
    if (e.key.startsWith('Arrow') && P.sel.size) {
      const s = selNotes(), step = st().snap || 0.25;
      let dp = 0, db = 0;
      if (e.key === 'ArrowUp') dp = e.shiftKey && !G.drum ? 12 : 1; if (e.key === 'ArrowDown') dp = e.shiftKey && !G.drum ? -12 : -1;
      if (e.key === 'ArrowRight') db = step; if (e.key === 'ArrowLeft') db = -step;
      for (const n of s) { if (n.pitch + dp > G.hi || n.pitch + dp < G.lo || n.start + db < 0 || n.start + db >= clip.length) return true; }
      for (const n of s) { n.pitch += dp; n.start += db; }
      if (dp && s.length) preview(track, s[0].pitch, s[0].vel);
      DAW.commit(); return true;
    }
    return false;
  };
})();

/* Sable — arrangement view: track headers, timeline ruler, clips */
(function () {
  'use strict';
  const DAW = window.DAW, h = DAW.h, clamp = DAW.clamp;
  const HEAD_W = 216, ROW_H = 70;
  const A = (DAW.arrange = { ppb: 24, HEAD_W });
  let scroll, inner, playhead, clipboard = null;

  const st = () => DAW.state, proj = () => DAW.state.project;
  const totalBeats = () => Math.max(256, Math.ceil((DAW.songEnd() + 64) / 4) * 4);
  const beatAtX = (clientX, lane) => (clientX - lane.getBoundingClientRect().left) / A.ppb;
  // clips snap to whole beats; Shift = 1/16 steps; with Snap set to Off they move and stretch freely
  const gridSnap = (b, free) => (!st().snap ? Math.round(b * 100) / 100 : free ? Math.round(b * 4) / 4 : Math.round(b));

  A.init = function () {
    scroll = document.getElementById('arrScroll'); inner = document.getElementById('arrInner');
    scroll.addEventListener('wheel', (e) => {
      if (!(e.ctrlKey || e.metaKey)) return;
      e.preventDefault();
      const r = scroll.getBoundingClientRect(), mx = e.clientX - r.left - HEAD_W, beat = (scroll.scrollLeft + mx) / A.ppb;
      A.ppb = clamp(A.ppb * (e.deltaY < 0 ? 1.2 : 1 / 1.2), 6, 120);
      A.render(); scroll.scrollLeft = beat * A.ppb - mx;
    }, { passive: false });
    scroll.addEventListener('pointerdown', () => { st().focus = 'arrange'; }, true);
    DAW.on('change', A.render);
  };
  A.zoom = (f) => { A.ppb = clamp(A.ppb * f, 6, 120); A.render(); };

  function drawPreview(cv, track, clip) {
    const w = cv.width, hh = cv.height, g = cv.getContext('2d');
    if (!clip.notes.length) return;
    let lo = 127, hi = 0;
    if (track.type === 'drums') { lo = DAW.DRUM_BASE; hi = DAW.DRUM_BASE + 11; }
    else { for (const n of clip.notes) { lo = Math.min(lo, n.pitch); hi = Math.max(hi, n.pitch); } lo -= 2; hi += 2; }
    const rows = hi - lo + 1, rh = Math.max(1.5, Math.min(5, hh / rows)), sx = w / clip.length;
    g.fillStyle = 'rgba(20,18,15,.8)';
    for (const n of clip.notes) {
      if (n.start >= clip.length) continue;
      const y = (hh - rh) * (1 - (n.pitch - lo) / Math.max(1, rows - 1));
      g.fillRect(n.start * sx, y, Math.max(1.5, Math.min(n.length, clip.length - n.start) * sx - 0.5), rh);
    }
  }

  function clipEl(track, clip) {
    const s = st(), w = Math.max(6, clip.length * A.ppb - 1);
    const cv = h('canvas', { class: 'clip-prev', width: Math.round(clamp(w, 4, 3000)), height: ROW_H - 26 });
    const el = h('div', { class: 'clip' + (s.selClipId === clip.id ? ' sel' : ''), style: { left: clip.start * A.ppb + 'px', width: w + 'px', background: track.color } },
      h('div', { class: 'clip-name', text: track.name }), cv, h('div', { class: 'clip-resize' }));
    drawPreview(cv, track, clip);
    el.addEventListener('dblclick', (e) => { e.stopPropagation(); DAW.setTab('piano'); });
    el.addEventListener('contextmenu', (e) => {
      e.preventDefault(); e.stopPropagation();
      st().selTrackId = track.id; st().selClipId = clip.id;
      inner.querySelectorAll('.clip.sel').forEach((c) => c.classList.remove('sel')); el.classList.add('sel');
      const items = [
        ['Delete clip', () => { track.clips.splice(track.clips.indexOf(clip), 1); st().selClipId = null; DAW.commit(); }],
        ['Duplicate clip', () => A.key({ key: 'd', ctrlKey: true })],
        ['Edit notes', () => { DAW.emit('change'); DAW.setTab('piano'); }],
      ];
      if (track.clips.length > 1) items.push(['Join all clips on this track into one', () => {
        const start = Math.min(...track.clips.map((c) => c.start)), end = Math.max(...track.clips.map((c) => c.start + c.length));
        const joined = DAW.makeClip(start, end - start, track.clips.flatMap((c) => c.notes.filter((n) => n.start < c.length).map((n) => Object.assign({}, n, { start: n.start + c.start - start, length: Math.min(n.length, c.length - n.start) }))));
        track.clips = [joined]; st().selClipId = joined.id; DAW.commit();
      }]);
      DAW.menu(e.clientX, e.clientY, items);
    });
    el.addEventListener('pointerdown', (e) => {
      if (e.button !== 0) return;
      e.stopPropagation();
      const resize = e.target.classList.contains('clip-resize');
      const dup = (e.altKey || e.ctrlKey || e.metaKey) && !resize;
      const startX = e.clientX, startY = e.clientY, o = { start: clip.start, length: clip.length };
      let moved = false, target = track, ns = clip.start, nl = clip.length;
      st().selTrackId = track.id; st().selClipId = clip.id;
      inner.querySelectorAll('.clip.sel').forEach((c) => c.classList.remove('sel')); el.classList.add('sel');
      DAW.drag(e, (ev) => {
        const db = (ev.clientX - startX) / A.ppb;
        if (Math.abs(ev.clientX - startX) > 3 || Math.abs(ev.clientY - startY) > 6) moved = true;
        if (!moved) return;
        if (resize) { nl = Math.max(ev.shiftKey || !st().snap ? 0.25 : 1, gridSnap(o.length + db, ev.shiftKey)); el.style.width = Math.max(6, nl * A.ppb - 1) + 'px'; return; }
        ns = Math.max(0, gridSnap(o.start + db, ev.shiftKey)); el.style.left = ns * A.ppb + 'px';
        const rows = [...inner.querySelectorAll('.track-row')];
        const row = rows.find((r) => { const b = r.getBoundingClientRect(); return ev.clientY >= b.top && ev.clientY < b.bottom; });
        const cand = row && DAW.track(row.dataset.id);
        if (cand && cand.type === track.type && cand !== target) { target = cand; row.querySelector('.lane').append(el); }
      }, () => {
        if (!moved) { DAW.emit('change'); return; }
        const fits = resize ? DAW.clipFits(track, clip.start, nl, clip) : DAW.clipFits(target, ns, clip.length, dup ? null : clip);
        if (!fits) { DAW.toast("Clips on the same track can't overlap"); DAW.emit('change'); return; }
        if (resize) clip.length = nl;
        else if (dup) {
          const c = DAW.clone(clip); c.id = DAW.uid(); c.notes.forEach((n) => (n.id = DAW.uid())); c.start = ns;
          target.clips.push(c); st().selClipId = c.id; st().selTrackId = target.id;
        } else {
          clip.start = ns;
          if (target !== track) { track.clips.splice(track.clips.indexOf(clip), 1); target.clips.push(clip); st().selTrackId = target.id; }
        }
        DAW.commit();
      });
    });
    return el;
  }

  function headEl(track, i) {
    const s = st(), p = proj();
    const live = () => DAW.engine.sync();
    const name = h('input', { class: 'tname', value: track.name, spellcheck: 'false', title: 'Rename track' });
    name.addEventListener('change', () => { track.name = name.value.trim() || track.name; DAW.commit(); });
    name.addEventListener('keydown', (e) => { if (e.key === 'Enter') name.blur(); });
    const vol = h('input', { type: 'range', min: 0, max: 1, step: 0.01, value: track.volume, class: 'tvol', title: 'Volume' });
    vol.addEventListener('input', () => { track.volume = +vol.value; live(); });
    vol.addEventListener('change', () => DAW.commit());
    const btn = (cls, text, title, fn) => h('button', { class: cls, title, onclick: (e) => { e.stopPropagation(); fn(); } }, text);
    const head = h('div', { class: 'track-head' + (s.selTrackId === track.id ? ' sel' : '') },
      h('div', { class: 'tcolor', style: { background: track.color }, title: 'Change colour', onclick: (e) => { e.stopPropagation(); track.color = DAW.COLORS[(DAW.COLORS.indexOf(track.color) + 1) % DAW.COLORS.length]; DAW.commit(); } }),
      h('div', { class: 'tmain' },
        h('div', { class: 'trow' }, name, btn('tdel', '×', 'Delete track', () => { p.tracks.splice(i, 1); DAW.commit(); })),
        h('div', { class: 'trow' },
          h('span', { class: 'ttype', text: track.type === 'drums' ? 'DRUMS' : track.instrument.source !== 'synth' ? 'INST' : 'SYNTH' }),
          btn('tbtn m' + (track.mute ? ' on' : ''), 'M', 'Mute', () => { track.mute = !track.mute; DAW.commit(); }),
          btn('tbtn s' + (track.solo ? ' on' : ''), 'S', 'Solo', () => { track.solo = !track.solo; DAW.commit(); }),
          vol)));
    head.addEventListener('contextmenu', (e) => { e.preventDefault(); DAW.menu(e.clientX, e.clientY, [['Delete track', () => { p.tracks.splice(p.tracks.indexOf(track), 1); DAW.commit(); }]]); });
    head.addEventListener('pointerdown', (e) => {
      if (e.button !== 0) return;
      // controls re-render on their own commit; re-rendering here would swallow their click
      if (e.target.closest('button, input, .tcolor')) { if (s.selTrackId !== track.id) { s.selTrackId = track.id; s.selClipId = null; } return; }
      DAW.select(track.id, s.selTrackId === track.id ? s.selClipId : null);
    });
    return head;
  }

  A.render = function () {
    if (!inner) return;
    const p = proj(), s = st(), total = totalBeats(), W = total * A.ppb, sl = scroll.scrollLeft, stp = scroll.scrollTop;
    inner.innerHTML = ''; inner.style.width = HEAD_W + W + 'px'; inner.style.setProperty('--ppb', A.ppb + 'px');

    /* ruler */
    const loopRow = h('div', { class: 'loop-row', title: 'Drag to set the loop region, click to toggle looping' });
    if (p.loop.end > p.loop.start) loopRow.append(h('div', { class: 'loop-region' + (p.loop.on ? ' on' : ''), style: { left: p.loop.start * A.ppb + 'px', width: (p.loop.end - p.loop.start) * A.ppb + 'px' } }));
    const barRow = h('div', { class: 'bar-row', title: 'Click or drag to move the playhead' });
    const every = A.ppb * 4 >= 44 ? 1 : A.ppb * 4 >= 22 ? 2 : A.ppb * 4 >= 11 ? 4 : 8;
    for (let bar = 0; bar < total / 4; bar += every) barRow.append(h('span', { class: 'bar-num', style: { left: bar * 4 * A.ppb + 'px' }, text: bar + 1 }));
    const body = h('div', { class: 'ruler-body', style: { width: W + 'px' } }, loopRow, barRow);
    loopRow.addEventListener('pointerdown', (e) => {
      const b0 = Math.max(0, Math.round(beatAtX(e.clientX, body)));
      let b1 = b0, moved = false;
      DAW.drag(e, (ev) => {
        b1 = Math.max(0, Math.round(beatAtX(ev.clientX, body)));
        if (b1 !== b0) { moved = true; p.loop = { on: true, start: Math.min(b0, b1), end: Math.max(b0, b1) }; const r = loopRow.querySelector('.loop-region') || loopRow.appendChild(h('div', { class: 'loop-region on' })); r.classList.add('on'); r.style.left = p.loop.start * A.ppb + 'px'; r.style.width = (p.loop.end - p.loop.start) * A.ppb + 'px'; }
      }, () => { if (!moved) p.loop.on = !p.loop.on; DAW.commit(); });
    });
    barRow.addEventListener('pointerdown', (e) => {
      const seek = (ev) => DAW.transport.seek(Math.max(0, DAW.snapRound(beatAtX(ev.clientX, body))));
      seek(e); DAW.drag(e, seek);
    });
    const corner = h('div', { class: 'corner' },
      h('span', { class: 'corner-label', text: 'TRACKS' }),
      h('button', { class: 'mini', title: 'Zoom out (Ctrl+wheel)', onclick: () => A.zoom(1 / 1.3) }, '−'),
      h('button', { class: 'mini', title: 'Zoom in (Ctrl+wheel)', onclick: () => A.zoom(1.3) }, '+'));
    inner.append(h('div', { class: 'ruler' }, corner, body));

    /* tracks */
    p.tracks.forEach((track, i) => {
      const lane = h('div', { class: 'lane', style: { width: W + 'px' } });
      for (const c of track.clips) lane.append(clipEl(track, c));
      lane.addEventListener('pointerdown', (e) => { if (e.button === 0) DAW.select(track.id, null); });
      lane.addEventListener('dblclick', (e) => {
        const at = beatAtX(e.clientX, lane);
        let start = Math.floor(at / 4) * 4, end = start + 4;
        for (const o of track.clips) { // squeeze the new clip into the gap that was clicked
          if (o.start + o.length <= at) start = Math.max(start, o.start + o.length);
          if (o.start > at) end = Math.min(end, o.start);
        }
        if (end - start < 0.25 || !DAW.clipFits(track, start, end - start)) return;
        const c = DAW.makeClip(start, end - start); track.clips.push(c);
        s.selTrackId = track.id; s.selClipId = c.id; DAW.commit(); DAW.setTab('piano');
      });
      const row = h('div', { class: 'track-row' + (s.selTrackId === track.id ? ' sel' : ''), 'data-id': track.id, style: { height: ROW_H + 'px' } }, headEl(track, i), lane);
      inner.append(row);
    });
    const add = (type) => {
      const n = p.tracks.length, t = DAW.makeTrack(type, type === 'drums' ? 'Drums' : 'Grand Piano', type === 'drums' ? 'Rock Kit' : 'Grand Piano', n);
      p.tracks.push(t); s.selTrackId = t.id; s.selClipId = null; DAW.commit();
    };
    inner.append(h('div', { class: 'track-row add-row' },
      h('div', { class: 'track-head add' },
        h('button', { class: 'add-btn', title: 'Adds a piano; pick another instrument or a synth in the Instrument tab', onclick: () => add('synth') }, '+ Instrument'),
        h('button', { class: 'add-btn', onclick: () => add('drums') }, '+ Drums')),
      h('div', { class: 'lane-hint', text: p.tracks.length ? 'Double-click a lane to create a clip · drag to move · drag the right edge to resize · Alt-drag to copy' : 'Add a track to get started' })));

    playhead = h('div', { id: 'playhead' }); inner.append(playhead);
    scroll.scrollLeft = sl; scroll.scrollTop = stp;
    A.setPlayhead(DAW.transport ? DAW.transport.beat() : 0, false);
  };

  A.setPlayhead = function (beat, follow) {
    if (!playhead) return;
    const x = beat * A.ppb;
    playhead.style.transform = `translateX(${HEAD_W + x}px)`;
    if (follow) {
      const vw = scroll.clientWidth - HEAD_W;
      if (x < scroll.scrollLeft || x > scroll.scrollLeft + vw - 24) scroll.scrollLeft = Math.max(0, x - 40);
    }
  };

  /* keyboard actions while the arrangement has focus */
  A.key = function (e) {
    const s = st(), r = DAW.findClip(s.selClipId), mod = e.ctrlKey || e.metaKey;
    if ((e.key === 'Delete' || e.key === 'Backspace') && r) { r.track.clips.splice(r.track.clips.indexOf(r.clip), 1); s.selClipId = null; DAW.commit(); return true; }
    if (mod && e.key.toLowerCase() === 'd' && r) {
      const c = DAW.clone(r.clip); c.id = DAW.uid(); c.notes.forEach((n) => (n.id = DAW.uid())); c.start = r.clip.start + r.clip.length;
      if (!DAW.clipFits(r.track, c.start, c.length)) { DAW.toast('No room to duplicate: another clip is in the way'); return true; }
      r.track.clips.push(c); s.selClipId = c.id; DAW.commit(); return true;
    }
    if (mod && e.key.toLowerCase() === 'c' && r) { clipboard = { type: r.track.type, clip: DAW.clone(r.clip) }; DAW.toast('Clip copied'); return true; }
    if (mod && e.key.toLowerCase() === 'v' && clipboard) {
      const t = DAW.selTrack();
      if (!t || t.type !== clipboard.type) { DAW.toast('Select a ' + clipboard.type + ' track to paste this clip'); return true; }
      const c = DAW.clone(clipboard.clip); c.id = DAW.uid(); c.notes.forEach((n) => (n.id = DAW.uid())); c.start = Math.max(0, Math.round(DAW.transport.beat()));
      if (!DAW.clipFits(t, c.start, c.length)) { DAW.toast('No room to paste at the playhead: another clip is in the way'); return true; }
      t.clips.push(c); s.selClipId = c.id; DAW.commit(); return true;
    }
    return false;
  };
})();

// Conuco "A day with Rosa" demo timeline. window.render(t) draws frame t (s) and resolves once videos are seeked.
(() => {
  const $ = (id) => document.getElementById(id);
  const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
  const p = (t, a, b) => clamp((t - a) / (b - a));
  const E = { out: (x) => 1 - Math.pow(1 - x, 3), inOut: (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2),
    back: (x) => { const c1 = 1.5, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); } };
  const lerp = (a, b, x) => a + (b - a) * x;
  const set = (el, st) => { if (typeof el === 'string') el = $(el); if (el) Object.assign(el.style, st); };
  const rise = (el, t, a, d = 0.7, dy = 40) => { const x = E.out(p(t, a, a + d)); set(el, { opacity: x, transform: `translateY(${(1 - x) * dy}px)` }); return x; };
  const pending = [];
  const seek = (id, time) => {
    const v = $(id); if (!v) return;
    const target = Math.max(0, Math.min(time, (v.duration || 1e9) - 0.04));
    if (Math.abs(v.currentTime - target) < 0.001 && v.readyState >= 2) return;
    pending.push(new Promise((res) => { const done = () => { v.removeEventListener('seeked', done); res(); }; v.addEventListener('seeked', done); v.currentTime = target; setTimeout(res, 4000); }));
  };

  // ---------------- real tap log from the recording driver (seconds since recording start, x, y in 1440x3120) ----------------
  const TAPS = {
    alert: [[9.60, 548, 524]],
    doctor: [[1.60, 720, 2694], [6.15, 597, 1541], [21.75, 780, 2285], [26.75, 1004, 2007]],
    act: [[3.91, 720, 628], [20.07, 777, 2281], [22.86, 1006, 2694]],
    market: [[3.57, 968, 707]], ask: [[3.83, 720, 850]],
    farm: [[3.86, 780, 1240], [30.95, 626, 2260], [46.36, 267, 2037]], settings: [[1.09, 1305, 249]],
  };
  const TAP_LAG = 0.15; // driver logs just before adb delivers the tap

  // ---------------- the day: beats with footage segments [outStart, outEnd, take, srcStart, srcEnd] ----------------
  const BEATS = [
    { a: 23.9, clock: '6:00', ap: 'am', title: 'Smart alerts', bul: ['Checked every 6 hours, also offline', 'Outbreaks near her crops', 'Fungus risk and the next safe spray window'], badge: [['ink', 'Background job'], ['', 'Notifications']],
      segs: [[23.9, 37.15, 'alert', 1.3, 14.55]] },
    { a: 37.15, clock: '6:05', ap: 'am', title: "Today's decision", bul: ['Spray window from wind, rain and daylight', '3-day water balance (FAO-56)', 'Fungus risk from hours of leaf wetness'], badge: [['', 'Offline rules, no AI needed']],
      segs: [[37.15, 44.5, 'today', 0.4, 10.4]] },
    { a: 44.5, clock: '7:30', ap: 'am', title: 'AI crop doctor', bul: ['Leaf photo → problem, severity, safe steps', 'Gemini Nano on the device first', 'No Nano on this phone → asks before the cloud'], badge: [['cloud', 'Cloud · with consent'], ['ink', 'answer in seconds']],
      segs: [[44.5, 48.0, 'doctor', 1.2, 6.8], [48.0, 52.3, 'doctor', 20.6, 27.5], [52.3, 57.95, 'doctor', 37.0, 42.65]] },
    { a: 57.95, clock: '7:35', ap: 'am', title: 'From diagnosis to action', bul: ['Each step becomes a task', 'Warn neighbours: an 11 km area, never the farm', 'Outbreak map of nearby reports'], badge: [],
      segs: [[57.95, 62.05, 'act', 2.6, 6.7], [62.05, 64.55, 'act', 19.3, 21.8], [64.55, 66.55, 'act', 23.6, 25.6]] },
    { a: 66.55, clock: '10:00', ap: 'am', title: 'Market prices', bul: ['Median of what farmers were paid', 'Trend and the best buyer nearby'], badge: [],
      segs: [[66.55, 71.15, 'market', 2.6, 7.6]] },
    { a: 71.15, clock: '12:00', ap: 'pm', title: 'Ask Conuco', bul: ['Ask by voice or by text', 'Every answer shows where it ran'], badge: [],
      segs: [[71.15, 73.35, 'ask', 3.0, 5.2], [73.35, 75.85, 'ask', 6.2, 8.7]] },
    { a: 75.85, clock: '6:00', ap: 'pm', title: 'Farm log', bul: ['Dictate an entry by voice', 'Profit this month', 'Harvest progress for each plot'], badge: [],
      segs: [[75.85, 77.65, 'farm', 3.4, 5.2], [77.65, 79.65, 'farm', 11.6, 13.6], [79.65, 81.25, 'farm', 46.3, 47.9], [81.25, 83.35, 'farm2', 0.4, 2.5]] },
    { a: 83.35, clock: '⚙', ap: 'any time', title: 'Settings', bul: ['Alerts on or off · Check now · home widget', 'Language and on-device AI status'], badge: [],
      segs: [[83.35, 84.55, 'settings', 0.6, 1.8], [84.55, 88.4, 'settings', 6.0, 9.85]] },
  ];
  const CLICKS = [];
  BEATS.forEach((b) => b.segs.forEach(([oa, ob, take, sa, sb]) => {
    const sp = (sb - sa) / (ob - oa);
    (TAPS[take] || []).forEach(([tt, x, y]) => { if (tt >= sa && tt <= sb) CLICKS.push({ t: oa + (tt + TAP_LAG - sa) / sp, x, y }); });
  }));
  window.CLICKS = CLICKS.map((c) => +c.t.toFixed(3));

  const S = { s1: [0, 6.7], s2: [6.2, 14.7], s3: [14.2, 23.0], s4: [22.5, 24.5], s5: [23.6, 88.8], s6: [88.4, 115.0], s7: [114.6, 130] };
  const FADE = 0.5;
  let lastBeat = -1;

  const scenes = {
    s1(t) {
      const h = (id, a) => { const x = E.out(p(t, a, a + 1.4)); set(id, { transform: `translateY(${(1 - x) * 260}px)` }); };
      h('h3', 0.0); h('h2', 0.25); h('h1', 0.5);
      set('sun', { transform: `translateY(${(1 - E.out(p(t, 0.3, 2.4))) * 260}px)` });
      const l = E.back(p(t, 1.2, 1.9)); set('logo', { opacity: p(t, 1.2, 1.5), transform: `scale(${0.3 + 0.7 * l})` });
      rise('word', t, 1.6, 0.8, 50); rise('tag', t, 2.3, 0.8, 30);
      set('s1', { transform: `scale(${1 + 0.05 * E.inOut(p(t, 5.6, 6.7))})` });
    },
    s2(t) {
      const a = 6.2;
      rise('s2k', t, a + 0.2); rise('s2t', t, a + 0.4, 0.9, 50);
      rise('st1', t, a + 1.4, 0.8, 60); rise('st2', t, a + 3.8, 0.8, 60);
      $('n1').textContent = Math.round(40 * E.out(p(t, a + 1.5, a + 2.8)));
      document.querySelectorAll('#st2 .bar').forEach((b, i) => set(b, { opacity: 1 - 0.75 * E.out(p(t, a + 4.6 + i * 0.25, a + 5.2 + i * 0.25)) }));
      $('slash').style.strokeDashoffset = 180 * (1 - E.out(p(t, a + 5.4, a + 6.0)));
    },
    s3(t) {
      const a = 14.2;
      rise('s3k', t, a + 0.2); rise('s3t', t, a + 0.4, 0.8, 30);
      [['d1', 0.9], ['d2', 2.8], ['d3', 4.6]].forEach(([id, s]) => { const x = E.back(p(t, a + s, a + s + 0.6)); set(id, { opacity: p(t, a + s, a + s + 0.3), transform: `scale(${0.85 + 0.15 * x})` }); });
      const ph = E.out(p(t, a + 0.6, a + 1.5)); set('s3p', { opacity: ph, transform: `scale(.92) translateY(${(1 - ph) * 80}px)` });
      seek('v3', lerp(0.3, 4.5, p(t, a, 23)));
    },
    s4(t) {
      const x = E.back(p(t, 22.6, 23.3)); set('s4t', { transform: `scale(${0.8 + 0.2 * x})` }); rise('s4s', t, 22.9, 0.5, 20);
    },
    s5(t) {
      const bi = BEATS.reduce((acc, b, i) => (t >= b.a ? i : acc), 0);
      const b = BEATS[bi];
      if (bi !== lastBeat) {
        $('clk').textContent = b.clock; $('clkl').textContent = b.ap; $('ftitle').textContent = b.title;
        $('fbul').replaceChildren(...b.bul.map((x) => { const d = document.createElement('div'); d.textContent = x; return d; }));
        $('fbadge').replaceChildren(...b.badge.map(([cls, x]) => { const s = document.createElement('span'); s.className = `badge ${cls}`; s.textContent = x; return s; }));
        lastBeat = bi;
      }
      const lt = t - b.a;
      rise('clock', t, b.a, 0.5, 20); rise('ftitle', t, b.a + 0.15, 0.55, 26);
      [...$('fbul').children].forEach((d, i) => rise(d, t, b.a + 0.5 + i * 0.35, 0.5, 18));
      [...$('fbadge').children].forEach((d, i) => { const x = E.back(p(t, b.a + 1.4 + i * 0.3, b.a + 1.9 + i * 0.3)); set(d, { opacity: p(t, b.a + 1.4 + i * 0.3, b.a + 1.6 + i * 0.3), transform: `scale(${0.6 + 0.4 * x})` }); });
      // phone enters once
      const ph = E.out(p(t, 23.6, 24.4)); set('ph', { opacity: ph, transform: `translateY(${(1 - ph) * 90}px)` });
      // footage
      const seg = b.segs.find(([oa, ob]) => t >= oa && t < ob) || b.segs[b.segs.length - 1];
      const [oa, ob, take, sa, sb] = seg; const sp = (sb - sa) / (ob - oa);
      document.querySelectorAll('#ph video').forEach((v) => v.classList.toggle('on', v.id === `v${take}`));
      seek(`v${take}`, sa + (t - oa) * sp);
      set('speed', { opacity: sp > 1.15 ? 1 : 0 }); $('speed').textContent = `⏩ ${sp.toFixed(1)}× faster`;
      // tap ripple
      const c = CLICKS.find((k) => t >= k.t && t < k.t + 0.55);
      if (c) { const k = (t - c.t) / 0.55; set('tap', { left: `${(c.x / 1440) * 100}%`, top: `${((c.y - 150) / 2800) * 100}%`, opacity: 1 - k, transform: `scale(${0.5 + 0.9 * E.out(k)})` }); }
      else set('tap', { opacity: 0 });
    },
    s6(t) {
      const a = 88.4;
      rise('fk', t, a + 0.1); rise('ft', t, a + 0.25, 0.7, 30);
      document.querySelectorAll('#fchips .chip').forEach((c, i) => { const x = E.back(p(t, a + 0.6 + i * 0.1, a + 1.1 + i * 0.1)); set(c, { opacity: p(t, a + 0.6 + i * 0.1, a + 0.8 + i * 0.1) * (1 - p(t, 91.6, 92.1)), transform: `scale(${0.6 + 0.4 * x})` }); });
      const win = (id, s, e, from = 70) => { const i = E.out(p(t, s, s + 0.7)); const o = 1 - E.inOut(p(t, e - 0.45, e)); set(id, { opacity: Math.min(i, o), transform: `translateY(${(1 - i) * from}px)` }); };
      win('fcode', 91.7, 100.2); win('bA', 92.0, 100.2, 90); seek('vfA', 3.5); set('hA', { opacity: p(t, 94.5, 94.9) * (0.6 + 0.4 * Math.sin(t * 4)) });
      win('bB', 99.9, 103.1, 90); seek('vfB', 3); set('vfB', { transform: `scale(${1 + 0.04 * p(t, 99.9, 103.1)})` });
      win('bC', 102.9, 108.4, 80); win('bD', 103.2, 108.4, 80); seek('vfC', 3); seek('vfD', 3);
      win('lC', 103.6, 108.4, 16); win('lD', 103.9, 108.4, 16);
      win('bE', 108.2, 115.0, 80); win('bF', 108.6, 115.0, 80); seek('vfE', 3); seek('vfF', 3);
      win('lE', 109.0, 115.0, 16); win('lF', 109.4, 115.0, 16);
      set('hE', { opacity: p(t, 109.6, 110.0) }); set('hF', { opacity: p(t, 111.8, 112.2) });
    },
    s7(t) {
      const a = 114.6;
      const ph = E.out(p(t, a, a + 0.9)); set('cp', { opacity: ph, transform: `scale(.92) translateX(${(1 - ph) * 100}px)` });
      seek('vc', lerp(0.3, 6.0, p(t, a, 130)));
      const e = E.out(p(t, a + 0.4, a + 1.4)); set('cend', { opacity: e, transform: `translateY(${(1 - e) * 40}px)` });
    },
  };

  // ---------------- captions ----------------
  const VO = [
    [0.8, 21.32, "This is Conuco, a free field assistant for small farmers in the Dominican Republic. | Pests and diseases can destroy up to forty percent of a harvest, and in the hills there's often no signal and no agronomist. | Conuco reads a sick leaf with Gemini Nano on the phone, turns the weather into daily decisions, and keeps working offline."],
    [24.0, 12.98, "Rosa's phone buzzes. | Every few hours, even offline, Conuco checks the forecast and her neighbours' reports, | and sends alerts: Black Sigatoka nearby, high fungus risk, and the next safe spray window."],
    [37.3, 7.05, 'Today turns it into one decision: not now, spray Saturday morning. | Water today, and watch for fungus.'],
    [44.65, 13.18, 'In the field, she checks a sick banana leaf. | This phone has no Gemini Nano, so Conuco asks before the photo leaves. | Seconds later: Black Sigatoka, severe, with safe steps and no pesticide doses.'],
    [58.1, 8.34, 'One tap adds a step to her tasks. | One tap warns her neighbours on the outbreak map, as an eleven-kilometre area, never her farm.'],
    [66.7, 4.31, 'At the market, she checks what buyers pay this week, and who pays best.'],
    [71.3, 4.40, 'At noon, she asks Conuco a question, by voice or by text.'],
    [76.0, 7.19, 'In the evening, she logs the day by voice. | Her farm log shows the month\'s profit, and how close each plot is to harvest.'],
    [83.5, 4.17, 'Alerts, language and on-device AI are all in settings.'],
    [88.6, 25.73, 'Under the hood, Conuco is built on Firebase. | Firebase AI Logic routes every request: Gemini Nano on the device first, Gemini in the cloud only with consent. | Remote Config tunes the thresholds live. | Authentication and Firestore keep each farm log private and offline-first. | App Check verifies every AI call, and Crashlytics and Performance watch every release.'],
    [115.2, 10.53, 'Conuco. The device first, the cloud only with consent. | Built for the farmers where the signal stops. | Try it at petiht.com/conuco.'],
  ];
  const CUES = [];
  VO.forEach(([start, dur, text]) => {
    const parts = text.split('|').map((s) => s.trim()); const total = parts.reduce((n, s) => n + s.length, 0); let at = start;
    parts.forEach((s) => { const d = (dur * s.length) / total; CUES.push([+at.toFixed(3), +(at + d).toFixed(3), s]); at += d; });
  });
  window.CUES = CUES;
  const CH = [[0, 23.6], [23.6, 88.4], [88.4, 114.6], [114.6, 130]];

  // ---------------- inserted scene: the same leaf with Gemini Nano in Chrome (fresh recording) ----------------
  const INS = 57.95, INS_D = 13.6;
  const NANO = [[0, 4.3, 9.6, 13.9], [4.3, 7.2, 13.9, 56.6], [7.2, 11.6, 56.8, 61.2], [11.6, 13.6, 70.0, 72.0]];
  const NANO_CLICKS = [11.1, 13.35].map((r) => INS + (r - 9.6));
  const shift = (x) => (x >= INS ? x + INS_D : x);
  const CUES_T = CUES.map(([a, b, s]) => [shift(a), shift(b), s]);
  CUES_T.push([INS + 0.9, INS + 4.7, 'On a laptop with Gemini Nano, Chrome runs the same diagnosis right on the device.'],
              [INS + 4.7, INS + 12.2, 'Black Sigatoka again, with an honest confidence, and the photo never left.']);
  CUES_T.sort((x, y) => x[0] - y[0]);
  window.CUES = CUES_T;
  window.CLICKS = [...CLICKS.map((c) => shift(c.t)), ...NANO_CLICKS].sort((x, y) => x - y).map((x) => +x.toFixed(3));
  window.DURATION = 130 + INS_D;
  const nanoScene = (u) => {
    set('sN', { visibility: 'visible', opacity: Math.min(p(u, 0, 0.45), 1 - p(u, INS_D - 0.45, INS_D)) });
    const seg = NANO.find(([a, b]) => u >= a && u < b) || NANO[NANO.length - 1];
    const [a, b, ra, rb] = seg; const sp = (rb - ra) / (b - a);
    seek('vN', ra + (u - a) * sp);
    const y = u < 7.2 ? -269 : u < 11.6 ? -20 : -342;
    set('vN', { transform: `translateY(${y}px)` });
    set('nSpeed', { opacity: sp > 1.5 ? 1 : 0 });
    const br = E.out(p(u, 0.1, 0.8)); set('nB', { opacity: br, transform: `scale(${0.96 + 0.04 * br})` });
    rise('nClock', u, 0.4, 0.5, 20); rise('nTitle', u, 0.55, 0.55, 26);
    [...$('nBul').children].forEach((d, i) => rise(d, u, 1.0 + i * 0.5, 0.5, 18));
    const bd = E.back(p(u, 7.4, 7.9)); set('nBadge', { opacity: p(u, 7.4, 7.6), transform: `scale(${0.6 + 0.4 * bd})` });
    set('nHl', { opacity: u >= 11.8 ? 0.6 + 0.4 * Math.sin(u * 4) : 0 });
  };

  window.render = async (t) => {
    pending.length = 0;
    const inIns = t >= INS && t < INS + INS_D;
    const tt = t;
    t = t < INS ? t : (inIns ? INS - 0.001 : t - INS_D);
    for (const [id, [a, b]] of Object.entries(S)) {
      const el = $(id);
      if (t < a || t > b) { el.style.opacity = 0; el.style.visibility = 'hidden'; continue; }
      el.style.visibility = 'visible';
      el.style.opacity = a === 0 ? 1 - p(t, b - FADE, b) : Math.min(p(t, a, a + FADE), b === 130 ? 1 : 1 - p(t, b - FADE, b));
      scenes[id](t);
    }
    if (inIns) nanoScene(tt - INS); else set('sN', { visibility: 'hidden', opacity: 0 });
    if (inIns) set('tap', { opacity: 0 });
    const cue = CUES_T.find(([a, b]) => tt >= a && tt < b);
    const day = t >= 23.6 && t <= 88.6;
    set('cap', day ? { left: '1290px', maxWidth: '1000px' } : { left: '50%', maxWidth: '1560px' });
    if (cue) { $('cap').textContent = cue[2]; set('cap', { opacity: Math.min(1, p(tt, cue[0], cue[0] + 0.12), 1 - p(tt, cue[1] - 0.08, cue[1])) }); } else set('cap', { opacity: 0 });
    set('chapters', { opacity: p(t, 1.0, 1.8) * (1 - p(t, 121, 122)) });
    document.querySelectorAll('#chapters .tr i').forEach((bar, i) => { bar.style.width = `${p(t, CH[i][0], CH[i][1]) * 100}%`; });
    set('fade', { opacity: p(t, 128.8, 130) });
    await Promise.all(pending);
    return true;
  };
  window.ready = async () => {
    await document.fonts.ready;
    await Promise.all([...document.querySelectorAll('video')].map((v) => (v.readyState >= 1 ? 0 : new Promise((r) => { v.addEventListener('loadedmetadata', r, { once: true }); setTimeout(r, 8000); }))));
    return true;
  };
})();

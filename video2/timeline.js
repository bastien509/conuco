// Conuco demo timeline. window.render(t) draws the frame at time t (seconds) and resolves once every video is seeked.
// Deterministic: the renderer screenshots each frame at 30 fps. Footage is real app recordings (px/*.mp4).
(() => {
  const $ = (id) => document.getElementById(id);
  const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
  const p = (t, a, b) => clamp((t - a) / (b - a));
  const E = {
    out: (x) => 1 - Math.pow(1 - x, 3),
    inOut: (x) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2),
    back: (x) => { const c1 = 1.5, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); },
  };
  const lerp = (a, b, x) => a + (b - a) * x;
  const set = (el, st) => { if (typeof el === 'string') el = $(el); if (el) Object.assign(el.style, st); };
  // reveal: opacity + upward slide with an ease, starting at a for d seconds
  const rise = (el, t, a, d = 0.7, dy = 40) => { const x = E.out(p(t, a, a + d)); set(el, { opacity: x, transform: `translateY(${(1 - x) * dy}px)` }); return x; };
  const pending = [];
  const seek = (id, time) => {
    const v = $(id); if (!v) return;
    const target = Math.max(0, Math.min(time, (v.duration || 1e9) - 0.04));
    if (Math.abs(v.currentTime - target) < 0.001 && v.readyState >= 2) return;
    pending.push(new Promise((res) => { const done = () => { v.removeEventListener('seeked', done); res(); }; v.addEventListener('seeked', done); v.currentTime = target; setTimeout(res, 4000); }));
  };
  // piecewise map: [[t0, src0], [t1, src1], ...] → linear inside each segment, holds outside
  const map = (lt, pts) => {
    if (lt <= pts[0][0]) return pts[0][1];
    for (let i = 1; i < pts.length; i++) {
      const [ta, sa, mode] = pts[i - 1]; const [tb, sb] = pts[i];
      if (lt <= tb) return mode === 'jump' ? sa + (lt - ta) : lerp(sa, sb, (lt - ta) / (tb - ta));
    }
    return pts[pts.length - 1][1];
  };

  // ---------------- scenes: [id, start, end] with 0.5 s cross-fades ----------------
  const S = {
    s1: [0, 6.6], s2: [6.2, 15.6], s3: [15.2, 26.4], s4: [26.0, 31.2], s5: [30.8, 61.2], s6: [60.8, 72.8],
    s7: [72.4, 91.2], s8: [90.8, 106.4], s9: [106.0, 131.2], s10: [130.8, 145.4], s11: [145.0, 180],
  };
  const FADE = 0.5;

  const scenes = {
    s1(t) {
      const h = (id, a) => { const x = E.out(p(t, a, a + 1.4)); set(id, { transform: `translateY(${(1 - x) * 260}px)` }); };
      h('h3', 0.0); h('h2', 0.25); h('h1', 0.5);
      set('rows', { opacity: 0.55 * p(t, 1.0, 2.0) });
      const s = E.out(p(t, 0.3, 2.4)); set('sun', { transform: `translateY(${(1 - s) * 260}px)` });
      const l = E.back(p(t, 1.6, 2.3)); set('logo', { opacity: p(t, 1.6, 1.9), transform: `scale(${0.3 + 0.7 * l})` });
      rise('word', t, 2.0, 0.8, 50); rise('tag', t, 2.7, 0.8, 30);
      const z = E.inOut(p(t, 5.4, 6.6)); set('s1', { transform: `scale(${1 + 0.06 * z})` });
    },
    s2(t) {
      const l = 6.2;
      rise(document.querySelector('#s2 .kicker'), t, l + 0.2); rise(document.querySelector('#s2 .title'), t, l + 0.4, 0.9, 50);
      rise('st1', t, l + 1.4, 0.8, 60); rise('st2', t, l + 3.6, 0.8, 60); rise('st3', t, l + 5.8, 0.8, 60);
      $('n1').textContent = Math.round(500 * E.out(p(t, l + 1.5, l + 2.8)));
      $('n2').textContent = Math.round(20 + 20 * E.out(p(t, l + 3.7, l + 4.8)));
      document.querySelectorAll('#st3 .bar').forEach((b, i) => set(b, { opacity: 1 - 0.75 * E.out(p(t, l + 6.6 + i * 0.25, l + 7.2 + i * 0.25)) }));
      $('slash').style.strokeDashoffset = 180 * (1 - E.out(p(t, l + 7.4, l + 8.0)));
      set('s2', { transform: `translateY(${-30 * E.inOut(p(t, 14.8, 15.6))}px)` });
    },
    s3(t) {
      const a = 15.2;
      rise('s3k', t, a + 0.3); rise('s3t', t, a + 0.5, 0.8, 30);
      const b = E.out(p(t, a + 0.7, a + 1.6)); set('s3b', { opacity: b, transform: `translateX(${(1 - b) * -120}px)` });
      const ph = E.out(p(t, a + 1.0, a + 1.9)); set('s3p', { opacity: ph, transform: `translateX(${(1 - ph) * 120}px) rotate(${(1 - ph) * 4}deg)` });
      rise('s3bl', t, a + 1.8, 0.6, 16); rise('s3pl', t, a + 2.0, 0.6, 16);
      seek('v3b', map(t - a, [[0, 3.8], [9.5, 12.8]]));
      seek('v3p', map(t - a, [[0, 0.2], [2.5, 0.2], [11.2, 9.0]]));
      // drift toward the phone for the next scene
      const z = E.inOut(p(t, 25.4, 26.4)); set('s3b', { filter: `blur(${z * 4}px)` });
    },
    s4(t) {
      const a = 26.0;
      const z = E.inOut(p(t, a, a + 1.0));
      set('s4p', { transform: `translateY(${lerp(60, 0, z)}px) scale(${lerp(0.9, 1.12, z)})` });
      seek('v4p', 0.25);
      [['c41', a + 1.0], ['c42', a + 1.6], ['c43', a + 2.2]].forEach(([id, s]) => {
        const x = E.back(p(t, s, s + 0.6)); set(id, { opacity: p(t, s, s + 0.3), transform: `scale(${0.7 + 0.3 * x})` });
      });
    },
    s5(t) {
      const a = 30.8;
      rise('s5k', t, a + 0.2); rise('s5t', t, a + 0.35, 0.8, 30);
      const chips = document.querySelectorAll('#s5chips .chip');
      chips.forEach((c, i) => { const x = E.back(p(t, a + 0.7 + i * 0.12, a + 1.2 + i * 0.12)); set(c, { opacity: p(t, a + 0.7 + i * 0.12, a + 0.9 + i * 0.12), transform: `scale(${0.6 + 0.4 * x})` }); });
      const win = (id, s, e, from = 60) => { const i = E.out(p(t, s, s + 0.7)); const o = 1 - E.inOut(p(t, e - 0.5, e)); set(id, { opacity: Math.min(i, o), transform: `translateY(${(1 - i) * from}px)` }); };
      // b: AI Logic code + console (33.4 – 40.4)
      win('s5code', a + 2.6, a + 9.6); win('s5b1', a + 3.0, a + 9.6, 90);
      seek('v5a', 7.5);
      set('hl5a', { opacity: p(t, a + 5.0, a + 5.4) * (0.6 + 0.4 * Math.sin((t - a) * 4)) });
      set('v5a', { transform: `scale(${1 + 0.05 * p(t, a + 3, a + 9.6)})` });
      // c: Remote Config (40.2 – 45.6)
      win('s5rc', a + 9.4, a + 14.8);
      document.querySelectorAll('#rcrows .rc').forEach((r, i) => rise(r, t, a + 9.9 + i * 0.25, 0.5, 20));
      // d: Auth + Firestore (45.4 – 52.6)
      win('s5b2', a + 14.6, a + 21.8, 80); win('s5b3', a + 15.0, a + 21.8, 80);
      rise('s5l1', t, a + 15.6, 0.6, 14); rise('s5l2', t, a + 16.0, 0.6, 14);
      if (t > a + 21.8) { set('s5l1', { opacity: 0 }); set('s5l2', { opacity: 0 }); }
      seek('v5b', 8); seek('v5c', 8);
      // e: App Check (52.4 – 61.2)
      win('s5b4', a + 21.6, a + 31, 80);
      seek('v5d', 8);
      set('v5d', { transform: `scale(${1 + 0.08 * E.inOut(p(t, a + 22.5, a + 30))})` });
      set('hl5d', { opacity: p(t, a + 23.6, a + 24.0) });
      const bd = document.querySelectorAll('#s5badges > *'); bd.forEach((b, i) => rise(b, t, a + 23 + i * 0.5, 0.6, 24));
      if (t < a + 21.6) bd.forEach((b) => set(b, { opacity: 0 }));
      // title + chips leave once the detail panels take over
      const out = E.inOut(p(t, a + 2.2, a + 2.8)); set('s5chips', { opacity: 1 - out });
    },
    s6(t) {
      const a = 60.8;
      document.querySelectorAll('#s6 .kicker, #s6 .title').forEach((el, i) => rise(el, t, a + 0.3 + i * 0.2, 0.7, 30));
      rise('s6txt', t, a + 1.0, 0.8, 24);
      document.querySelectorAll('#s6chips .badge').forEach((b, i) => { const x = E.back(p(t, a + 3.0 + i * 0.4, a + 3.5 + i * 0.4)); set(b, { opacity: p(t, a + 3.0 + i * 0.4, a + 3.2 + i * 0.4), transform: `scale(${0.6 + 0.4 * x})` }); });
      const ph = E.out(p(t, a + 0.4, a + 1.3)); set('s6p', { opacity: ph, transform: `translateY(${(1 - ph) * 80}px)` });
      seek('v6p', map(t - a, [[0, 0.3], [11, 4.5]]));
      const pulse = (id, s) => { const k = ((t - s) % 1.4) / 1.4; set(id, { opacity: t > s ? (1 - k) : 0, transform: `scale(${0.7 + 0.6 * k})` }); };
      pulse('r6a', a + 2.0); pulse('r6b', a + 5.2);
    },
    s7(t) {
      const a = 72.4, lt = t - a;
      const b = E.out(p(t, a, a + 0.8)); set('s7b', { opacity: b, transform: `scale(${0.96 + 0.04 * b})` });
      const src = map(lt, [[0, 15.0], [4.4, 21.6], [8.4, 57.6], [13.6, 63.0], [13.6, 72.0], [18.8, 80.0]]);
      seek('v7', src);
      // scroll the recording inside the window to keep the action in view
      const y = lt < 4.4 ? lerp(-150, -250, E.inOut(p(lt, 3.6, 4.4))) : lt < 8.4 ? -250 : lt < 13.6 ? lerp(-250, 0, E.inOut(p(lt, 8.4, 9.2))) : lerp(0, -230, E.inOut(p(lt, 13.6, 14.4)));
      set('v7', { transform: `translateY(${y}px)` });
      set('s7speed', { opacity: Math.min(p(lt, 4.4, 4.7), 1 - p(lt, 8.1, 8.4)) });
      rise(document.querySelector('#s7side .kicker'), t, a + 0.6); rise(document.querySelector('#s7side .display'), t, a + 0.8, 0.8, 30);
      rise('s7a', t, a + 2.0, 0.6, 20); rise('s7b2', t, a + 4.0, 0.6, 20); rise('s7c', t, a + 8.6, 0.6, 20);
      const bd = E.back(p(t, a + 10.4, a + 11.0)); set('s7badge', { opacity: p(t, a + 10.4, a + 10.6), transform: `scale(${0.6 + 0.4 * bd})` });
      set('hl7', { opacity: lt > 14.4 ? 0.6 + 0.4 * Math.sin(lt * 4) : 0 });
    },
    s8(t) {
      const a = 90.8, lt = t - a;
      const ph = E.out(p(t, a, a + 0.9)); set('s8p', { opacity: ph, transform: `translateX(${(1 - ph) * -100}px)` });
      seek('v8p', map(lt, [[0, 20.6], [5.6, 26.2, 'jump'], [5.6, 27.4], [7.2, 29.0, 'jump'], [7.2, 88.4], [15.6, 96.8]]));
      rise(document.querySelector('#s8side .kicker'), t, a + 0.5); rise(document.querySelector('#s8side .display'), t, a + 0.7, 0.8, 30);
      rise('s8a', t, a + 1.6, 0.6, 20); rise('s8b', t, a + 7.4, 0.6, 20); rise('s8c', t, a + 8.2, 0.6, 20);
    },
    s9(t) {
      const a = 106.0, lt = t - a;
      const ph = E.out(p(t, a, a + 0.9)); set('s9p', { opacity: ph, transform: `translateY(${(1 - ph) * 80}px)` });
      const steps = [['a1', 0.4], ['a2', 4.6], ['a3', 8.4], ['a4', 11.4], ['a5', 17.8]];
      steps.forEach(([id, s]) => rise(id, t, a + s, 0.6, 24));
      const active = steps.reduce((acc, s, i) => (lt >= s[1] ? i : acc), 0);
      steps.forEach(([id], i) => $(id).classList.toggle('on', i === active));
      // footage: act (0–17.6), map (17.6–21.6), today decision (21.6–)
      seek('v9a', map(lt, [[0, 1.2], [4.6, 5.8, 'jump'], [4.6, 14.2], [8.4, 18.0, 'jump'], [8.4, 29.3], [11.4, 32.3, 'jump'], [11.4, 35.6], [14.4, 38.6]]));
      seek('v9b', map(lt, [[14.4, 1.4], [17.8, 4.8]]));
      seek('v9c', 0.25);
      set('v9b', { opacity: p(lt, 14.2, 14.6) }); set('v9c', { opacity: p(lt, 17.6, 18.0) });
    },
    s10(t) {
      const a = 130.8;
      const w = (id, s) => { const x = E.out(p(t, a + s, a + s + 0.8)); set(id, { opacity: x, transform: `${id.startsWith('s10p') ? 'scale(.86) ' : ''}translateY(${(1 - x) * 70}px)` }); };
      w('s10b', 0.2); w('s10p1', 0.6); w('s10p2', 1.0);
      rise('s10l1', t, a + 1.0, 0.5, 14); rise('s10l2', t, a + 1.3, 0.5, 14); rise('s10l3', t, a + 1.6, 0.5, 14);
      rise(document.querySelector('#s10 .kicker'), t, a + 0.1);
      seek('v10a', map(t - a, [[0, 35.5], [14.6, 44.0]]));
      seek('v10b', map(t - a, [[0, 1.0], [14.6, 10.5]]));
      seek('v10c', map(t - a, [[0, 0.3], [14.6, 6.8]]));
    },
    s11(t) {
      const a = 145.0;
      const d = E.out(p(t, a, a + 1.0)); set('s11b', { opacity: d, transform: `translateX(${(1 - d) * -80}px)` }); set('s11p', { opacity: d, transform: `translateX(${(1 - d) * 80}px)` });
      seek('v11a', map(t - a, [[0, 4.2], [6, 10.0]])); seek('v11b', map(t - a, [[0, 0.3], [6, 4.0]]));
      // devices step aside for the end card
      const g = E.inOut(p(t, a + 5.6, a + 6.8));
      set('s11b', { opacity: d * (1 - g), transform: `translateX(${-g * 400}px) scale(${1 - 0.15 * g})` });
      set('s11p', { opacity: d * (1 - g), transform: `translateX(${g * 400}px) scale(${1 - 0.15 * g})` });
      const e = E.out(p(t, a + 6.4, a + 7.4)); set('s11end', { opacity: e, transform: `translateY(${(1 - e) * 40}px)` });
    },
  };

  // ---------------- captions (split from the narration, timed by length) ----------------
  const VO = [
    ['v1', 1.5, 13.09, "In the hills of the Dominican Republic, small farms grow much of the country's food. | But pests and diseases can take up to forty percent of a harvest, | and where the signal stops, help is far away."],
    ['v2', 15.8, 11.89, 'Meet Conuco: a field assistant for those farmers, | on the web in Chrome and as a native Android app. | It turns the weather into clear daily decisions, and it keeps working offline.'],
    ['v3', 31.4, 25.16, 'Conuco is built on Firebase. | Firebase AI Logic runs hybrid inference: Gemini Nano on the device first, | and Gemini in the cloud only when it helps. | Remote Config tunes the confidence floor and the models, live. | Authentication and Firestore keep every farm log private and offline-first. | And App Check, with Play Integrity and reCAPTCHA Enterprise, protects every AI call.'],
    ['v4', 61.6, 10.45, 'Now, the crop doctor. On Android, the big camera button opens a leaf camera, | with a frame and live checks for blur and light, before the photo is taken.'],
    ['v5', 73.0, 15.65, 'On a laptop with Gemini Nano, Chrome runs the diagnosis right on the device. | The photo is redrawn first, so location and camera data are gone. | Nano answers with an honest confidence. On this device, the photo never left.'],
    ['v6', 91.4, 12.83, 'This phone has no Gemini Nano, so Conuco asks before anything leaves. | With a yes, Gemini in the cloud answers in seconds: | Black Sigatoka, severe, ninety-five percent.'],
    ['v7', 106.6, 21.47, 'Every step becomes a task with one tap. | The advice is filtered: no pesticide doses, and a reminder to check with an extension officer. | One tap warns the neighbours: the report lands on the outbreak map as an eleven-kilometre area, never a farm. | And each morning, Today turns it all into one clear decision.'],
    ['v8', 148.0, 13.64, 'Conuco. The device first, the cloud only with consent, on the web and on Android. | Built with Firebase, for the farmers where the signal stops. | Try it today at petiht.com/conuco.'],
  ];
  const CUES = [];
  VO.forEach(([, start, dur, text]) => {
    const parts = text.split('|').map((s) => s.trim());
    const total = parts.reduce((n, s) => n + s.length, 0);
    let at = start;
    parts.forEach((s) => { const d = (dur * s.length) / total; CUES.push([at, at + d, s]); at += d; });
  });
  window.CUES = CUES;

  const CH = [[0, 30.8], [30.8, 60.8], [60.8, 145], [145, 180]];

  window.render = async (t) => {
    pending.length = 0;
    for (const [id, [a, b]] of Object.entries(S)) {
      const el = $(id);
      const vis = t >= a && t <= b;
      if (!vis) { el.style.opacity = 0; el.style.visibility = 'hidden'; continue; }
      el.style.visibility = 'visible';
      const o = Math.min(p(t, a, a + FADE), 1 - p(t, b - FADE, b));
      el.style.opacity = a === 0 ? Math.min(1, 1 - p(t, b - FADE, b)) : o;
      scenes[id](t);
      if (id === 's2') el.style.opacity = o;
    }
    const cue = CUES.find(([a, b]) => t >= a && t < b);
    const cap = $('cap');
    if (cue) { cap.textContent = cue[2]; cap.style.opacity = Math.min(1, p(t, cue[0], cue[0] + 0.15), 1 - p(t, cue[1] - 0.1, cue[1])); } else cap.style.opacity = 0;
    set('chapters', { opacity: clamp(p(t, 6.4, 7.2)) * (1 - p(t, 151.4, 152.2)) });
    document.querySelectorAll('#chapters .tr i').forEach((bar, i) => { bar.style.width = `${p(t, CH[i][0], CH[i][1]) * 100}%`; });
    set('fade', { opacity: p(t, 178.6, 180) });
    await Promise.all(pending);
    return true;
  };
  window.ready = async () => {
    await document.fonts.ready;
    await Promise.all([...document.querySelectorAll('video')].map((v) => (v.readyState >= 1 ? 0 : new Promise((r) => { v.addEventListener('loadedmetadata', r, { once: true }); setTimeout(r, 8000); }))));
    return true;
  };
})();

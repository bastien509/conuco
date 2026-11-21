// Demo autopilot for recording the Conuco video (local only, never deployed). It drives the real app
// with a visible cursor, one scene per ?scene=... URL. Every AI answer is real; nothing is mocked.
(() => {
  const scene = new URLSearchParams(location.search).get('scene') || '';
  if (!scene) return;
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const $ = (s) => document.querySelector(s);
  const byText = (sel, text) => [...document.querySelectorAll(sel)].find((e) => e.textContent.trim().includes(text));
  async function waitFor(fn, timeout = 180000) {
    const end = performance.now() + timeout;
    while (performance.now() < end) { const v = fn(); if (v) return v; await sleep(200); }
    throw new Error('timeout');
  }
  const set = (el, st) => Object.assign(el.style, st);
  const cur = document.createElement('div');
  cur.innerHTML = '<svg viewBox="0 0 24 24" width="30" height="30"><path d="M4 2l16 9-7 1.6L9.6 20z" fill="#fff" stroke="#10231a" stroke-width="1.6" stroke-linejoin="round"/></svg>';
  set(cur, { position: 'fixed', left: '0', top: '0', zIndex: '99999', pointerEvents: 'none', transition: 'transform .7s cubic-bezier(.3,.8,.3,1)', filter: 'drop-shadow(0 3px 6px rgba(0,0,0,.45))' });
  let cx = innerWidth * 0.62, cy = innerHeight * 0.7;
  const place = () => { cur.style.transform = `translate(${cx}px,${cy}px)`; };
  async function moveTo(el, block = 'center') {
    el.scrollIntoView({ block, behavior: 'smooth' });
    await sleep(700);
    const r = el.getBoundingClientRect();
    cx = r.left + Math.min(r.width * 0.5, 80); cy = r.top + r.height * 0.55; place();
    await sleep(800);
  }
  async function click(el, pause = 600) {
    await moveTo(el);
    const rp = document.createElement('div');
    set(rp, { position: 'fixed', left: `${cx - 4}px`, top: `${cy - 4}px`, width: '16px', height: '16px', borderRadius: '50%', border: '3px solid #F5C518', zIndex: '99998', pointerEvents: 'none' });
    document.body.append(rp);
    rp.animate([{ transform: 'scale(1)', opacity: 1 }, { transform: 'scale(4)', opacity: 0 }], { duration: 600 }).onfinish = () => rp.remove();
    el.click();
    await sleep(pause);
  }
  async function typeInto(el, text) {
    await moveTo(el);
    el.focus();
    for (const ch of text) { el.value += ch; el.dispatchEvent(new Event('input', { bubbles: true })); await sleep(70); }
  }
  const scrollTo = async (el, block = 'start', wait = 1400) => {
    if (block === 'start') { const y = el.getBoundingClientRect().top + scrollY - 84; scrollTo0({ top: y, behavior: 'smooth' }); } else el.scrollIntoView({ block, behavior: 'smooth' });
    await sleep(wait);
  };
  const scrollTo0 = (o) => window.scrollTo(o);
  const btn = (t, sel = 'button') => waitFor(() => byText(sel, t), 30000);
  const go = async (r) => { location.hash = `#${r}`; await sleep(1800); };

  const scenes = {
    async landing() {
      await sleep(15000);
      await scrollTo($('#how'), 'start', 9000);
      await scrollTo($('#features'), 'start', 8000);
    },
    async onboard() {
      await sleep(2000);
      await click(await btn('Start without an account'), 3500);
      await waitFor(() => $('.onboard'));
      await sleep(800);
      await click(byText('.seg button', 'English'), 900);
      await typeInto($('.onboard input[type=search]'), 'Santiago de los Caballeros');
      await click(await waitFor(() => byText('.result', 'DO')), 900);
      for (const n of ['Plantain', 'Banana', 'Beans', 'Cacao', 'Coffee']) await click(byText('.pick', n), 250);
      await typeInto($('input[autocomplete=given-name]'), 'Rosa');
      await click(await btn('Start'), 300);
      await waitFor(() => $('.decisions'));
      await sleep(4000);
      await scrollTo($('.decisions'), 'center', 5000);
    },
    async today() {
      await go('today');
      await waitFor(() => $('.decisions'));
      await sleep(2500);
      await scrollTo($('.card.alert') || $('.weather'), 'start', 3500);
      await scrollTo($('.decisions'), 'center', 4000);
      await click(await btn('Explain my day'), 500);
      await waitFor(() => $('.brief .card.ai') || $('.brief .err'));
      await sleep(400);
      await scrollTo($('.brief'), 'center', 5000);
      const tr = $('.brief details.trace'); if (tr) { tr.open = true; await scrollTo(tr, 'center', 5000); }
    },
    async doctor() {
      await go('doctor');
      await sleep(1500);
      await click(await waitFor(() => byText('.sample', 'Banana')), 2500);
      await click($('input[name=mode][value=smart]').closest('label'), 600);
      await click(await btn('Diagnose'), 300);
      await waitFor(() => $('.diag') || $('#consent[open]'));
      await sleep(500);
      await scrollTo($('.diag'), 'start', 6000);
      const acts = $('.diag ol.actions'); if (acts) await scrollTo(acts, 'center', 4000);
      const tr = $('details.trace'); if (tr) { tr.open = true; await scrollTo(tr, 'center', 6000); }
    },
    async cloud() {
      await go('doctor');
      await sleep(1500);
      await click(await waitFor(() => byText('.sample', 'Coffee')), 2500);
      await click($('input[name=mode][value=cloud]').closest('label'), 600);
      await click(await btn('Diagnose'), 300);
      const dlg = await waitFor(() => $('#consent[open]'));
      await sleep(4500);
      await click(byText('#consent button', 'Yes, send it'), 300);
      await waitFor(() => $('.diag'));
      await sleep(500);
      await scrollTo($('.diag'), 'start', 5000);
      const warn = byText('button', 'Warn neighbours');
      if (warn) { await click(warn, 3000); }
      const tr = $('details.trace'); if (tr) { tr.open = true; await scrollTo(tr, 'center', 5000); }
    },
    async map() {
      await go('map');
      await sleep(5000);
      await scrollTo($('#map'), 'center', 5000);
      await scrollTo($('.legend'), 'start', 5000);
    },
    async market() {
      await go('market');
      await sleep(3500);
      await scrollTo($('.prices'), 'start', 5000);
      await go('farm');
      await sleep(3000);
      await scrollTo($('.totals'), 'start', 4000);
      const log = byText('.card h2', 'Farm log'); if (log) await scrollTo(log, 'start', 5000);
    },
    async close() {
      await sleep(1500);
      await scrollTo($('.tech'), 'center', 7000);
      await scrollTo($('#judges'), 'start', 6000);
    },
  };

  window.addEventListener('load', async () => {
    document.body.append(cur); place();
    const fn = scenes[scene];
    if (fn) { try { await fn(); } catch (e) { document.title = `demo error: ${e.message}`; } }
  });
})();

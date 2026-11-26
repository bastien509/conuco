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
    async onboard() {
      await sleep(1500);
      await click(await btn('Start without an account'), 3000);
      await waitFor(() => $('.onboard'));
      await sleep(600);
      await click(byText('.seg button', 'English'), 800);
      await typeInto($('.onboard input[type=search]'), 'Santiago de los Caballeros');
      await click(await waitFor(() => byText('.result', 'DO')), 900);
      for (const n of ['Plantain', 'Banana', 'Beans', 'Cacao', 'Coffee']) await click(byText('.pick', n), 220);
      const nm = $('input[autocomplete=given-name]'); if (nm) await typeInto(nm, 'Rosa');
      await click(await btn('Start'), 300);
      await waitFor(() => $('.hero-decision'));
      await sleep(3000);
    },
    async today() {
      await go('today');
      await waitFor(() => $('.hero-decision'));
      await sleep(3500);
      await scrollTo($('.tiles'), 'center', 3500);
      await scrollTo($('.weather'), 'center', 3000);
      await click(await btn('Explain my day'), 500);
      await waitFor(() => $('.brief .card.ai') || $('.brief .err'), 240000);
      await sleep(300);
      await scrollTo($('.brief'), 'center', 6000);
    },
    async doctor() {
      await go('doctor');
      await sleep(1500);
      await click(await waitFor(() => byText('.sample', 'Banana') || $('.sample')), 2200);
      await click($('input[name=mode][value=private]').closest('label'), 700);
      await click(await btn('Diagnose'), 300);
      await waitFor(() => $('.diag-head') || $('.diag'), 240000);
      await sleep(300);
      await scrollTo($('.diag'), 'start', 6000);
      const ck = $('.checklist'); if (ck) { await scrollTo(ck, 'center', 2500); const st = $('.check-step'); if (st) await click(st, 2500); }
      const tr = $('details.trace'); if (tr) { tr.open = true; await scrollTo(tr, 'center', 6000); }
    },
  };

  window.addEventListener('load', async () => {
    document.body.append(cur); place();
    const fn = scenes[scene];
    if (fn) { try { await fn(); } catch (e) { document.title = `demo error: ${e.message}`; } }
  });
})();

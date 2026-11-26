// Features showcase: pick a feature, the phone shows that real screen. Auto-advances every 5 s until the visitor
// interacts, pauses on hover/focus or when the section is off screen, and never moves with prefers-reduced-motion.
(() => {
  const list = document.querySelector('.fx-list');
  const phone = document.getElementById('fx-phone');
  if (!list || !phone) return;
  const tabs = [...list.querySelectorAll('.fx')];
  const shots = [...phone.querySelectorAll('.fx-screen img')];
  const STEP = 5000;
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  let i = 0, timer = null, auto = !still, hover = false, visible = false;

  const show = (n, focus = false) => {
    i = (n + tabs.length) % tabs.length;
    tabs.forEach((t, k) => {
      const on = k === i;
      t.classList.toggle('on', on);
      t.setAttribute('aria-selected', String(on));
      t.tabIndex = on ? 0 : -1;
    });
    shots.forEach((s, k) => s.classList.toggle('on', k === i));
    phone.setAttribute('aria-labelledby', tabs[i].id);
    if (focus) tabs[i].focus();
    restart();
  };
  const restart = () => {
    clearTimeout(timer);
    list.classList.toggle('playing', auto && visible && !hover);
    tabs.forEach((t) => { const b = t.querySelector('.fx-bar'); b.style.animation = 'none'; void b.offsetWidth; b.style.animation = ''; });
    if (auto && visible && !hover) timer = setTimeout(() => show(i + 1), STEP);
  };
  const stopAuto = () => { auto = false; restart(); };

  tabs.forEach((t, k) => t.addEventListener('click', () => { stopAuto(); show(k); }));
  list.addEventListener('keydown', (e) => {
    const map = { ArrowDown: 1, ArrowRight: 1, ArrowUp: -1, ArrowLeft: -1 };
    if (e.key in map) { e.preventDefault(); stopAuto(); show(i + map[e.key], true); }
    else if (e.key === 'Home') { e.preventDefault(); stopAuto(); show(0, true); }
    else if (e.key === 'End') { e.preventDefault(); stopAuto(); show(tabs.length - 1, true); }
  });
  const box = list.closest('.showcase');
  box.addEventListener('mouseenter', () => { hover = true; restart(); });
  box.addEventListener('mouseleave', () => { hover = false; restart(); });
  box.addEventListener('focusin', () => { hover = true; restart(); });
  box.addEventListener('focusout', () => { hover = false; restart(); });
  new IntersectionObserver(([en]) => { visible = en.isIntersecting; restart(); }, { threshold: 0.35 }).observe(box);
  show(0);
})();

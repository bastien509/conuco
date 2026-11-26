// Light theme by default. Dark only when the visitor picks it with the theme button; the choice is remembered.
// Loaded in <head> (not deferred) so the saved theme applies before the first paint.
(() => {
  const KEY = 'conuco-theme';
  let theme = 'light';
  try { if (localStorage.getItem(KEY) === 'dark') theme = 'dark'; } catch { /* storage blocked: stay light */ }
  const root = document.documentElement;
  root.dataset.theme = theme;

  const paint = () => {
    root.dataset.theme = theme;
    document.querySelectorAll('meta[name="theme-color"]').forEach((m) => { m.content = theme === 'dark' ? '#0f1412' : '#1f6f3f'; });
    document.querySelectorAll('[data-theme-toggle]').forEach((b) => {
      const dark = theme === 'dark';
      b.setAttribute('aria-pressed', String(dark));
      b.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
      b.title = b.getAttribute('aria-label');
    });
  };
  addEventListener('DOMContentLoaded', () => {
    paint();
    document.querySelectorAll('[data-theme-toggle]').forEach((b) => b.addEventListener('click', () => {
      theme = theme === 'dark' ? 'light' : 'dark';
      try { localStorage.setItem(KEY, theme); } catch { /* not remembered */ }
      paint();
    }));
  });
})();

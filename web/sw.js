// Conuco service worker: keeps the app shell available with no signal.
// Firestore keeps farm data offline by itself; this only caches Conuco's own files.
const CACHE = 'conuco-shell-v7';
const SHELL = ['app.html', 'assets/app.css?v=7', 'assets/conuco.js?v=7', 'vendor/leaflet.css?v=7', 'assets/logo.svg', 'manifest.webmanifest'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return; // never touch Firebase, Google or weather traffic
  if (e.request.mode === 'navigate' && /\/app(\.html)?$/.test(url.pathname)) {
    e.respondWith(fetch(e.request).then((r) => {
      const copy = r.clone();
      caches.open(CACHE).then((c) => c.put('app.html', copy));
      return r;
    }).catch(() => caches.match('app.html')));
    return;
  }
  e.respondWith(caches.match(e.request).then((hit) => hit || fetch(e.request).then((r) => {
    if (r.ok && !url.pathname.endsWith('.apk')) { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return r;
  })));
});

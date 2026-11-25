const CACHE_NAME = 'pn8000-descarga-v4.4';
const ASSETS = ['./', './index.html', './manifest.json?v=4', './icon-192.png?v=3', './icon-512.png?v=3'];

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  if (event.request.url.includes('version.json') || event.request.url.includes('script.google.com')) return;
  const req = event.request.mode === 'navigate'
    ? new Request(event.request.url, { cache: 'no-store' })
    : event.request;
  event.respondWith(
    fetch(req)
      .then(res => {
        const clone = res.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        return res;
      })
      .catch(() => caches.match(event.request).then(r => r || (event.request.mode === 'navigate' ? caches.match('./index.html') : undefined)))
  );
});

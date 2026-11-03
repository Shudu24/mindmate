/* Mindmate service worker.
 *
 * The whole app shell is precached on install, so after the first visit
 * Mindmate opens and works with no connection at all. Bump CACHE when any
 * file in SHELL changes, otherwise phones keep serving the old copy.
 */
const CACHE = 'mindmate-v3';

const SHELL = [
  './',
  './index.html',
  './css/styles.css',
  './js/app.js',
  './js/db.js',
  './js/crypto.js',
  './js/api.js',
  './manifest.webmanifest',
  './icons/icon.svg'
];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE)
      .then(function (c) { return c.addAll(SHELL); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (k) {
        if (k !== CACHE) return caches.delete(k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  const req = e.request;

  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;

  /* The API is never cached. It is same-origin, so without this a GET of
     /api/me would be answered from a stale copy - and would keep answering
     while offline, which would show a signed-in state that is not real. */
  if (new URL(req.url).pathname.indexOf('/api/') === 0) return;

  /* Cache first. The shell is small and versioned, and a student on a train
     should never wait on the network for a screen that is already on disk. */
  e.respondWith(
    caches.match(req).then(function (hit) {
      if (hit) return hit;
      return fetch(req).then(function (res) {
        if (res && res.ok && res.type === 'basic') {
          const copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(req, copy); });
        }
        return res;
      }).catch(function () {
        /* Offline and not cached: navigations still get the app shell. */
        if (req.mode === 'navigate') return caches.match('./index.html');
        return new Response('', { status: 504, statusText: 'Offline' });
      });
    })
  );
});

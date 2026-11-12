// Sonora service worker: keeps the app shell available offline. API and audio always go to the server.
const BUILD = '__BUILD__'
const FILES = __FILES__
const CACHE = `sonora-${BUILD}`

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('sonora-') && k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname === '/sonora.apk') return
  if (req.mode === 'navigate') {
    // Network-first: after an update the new app shows up at once.
    event.respondWith(fetch(req).then((r) => {
      const copy = r.clone()
      caches.open(CACHE).then((c) => c.put('/', copy))
      return r
    }).catch(() => caches.match('/')))
    return
  }
  if (url.pathname.startsWith('/assets/') || FILES.includes(url.pathname)) {
    event.respondWith(caches.match(req).then((hit) => hit || fetch(req)))
  }
})

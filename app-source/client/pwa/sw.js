/* Apotik Lestari — service worker.
   Purpose: satisfy PWA installability (a registered worker with a fetch
   handler) and make repeat opens faster, WITHOUT ever risking stale
   business data:
   - Non-GET requests (all server actions: sales, stock, cash, reports)
     are never intercepted — data always comes from the server.
   - Navigations are network-first; the cached app shell is only a
     fallback when the network is unreachable.
   - Same-origin files under /assets/ (built JS/CSS/icons/manifest) use
     stale-while-revalidate; their file names are content-hashed, so a
     cached copy can never shadow a newer release.
   - Blob downloads (Excel exports/templates) are network-only. */
/* v2: bumped when the first-load payload was slimmed down (subset icon
   font, lazy Excel library). The activate handler deletes every older
   cache, so phones that cached the heavy v1 shell drop it on next open. */
var CACHE = "apotik-lestari-v2";

self.addEventListener("install", function () {
  self.skipWaiting();
});

self.addEventListener("activate", function (event) {
  event.waitUntil((async function () {
    var keys = await caches.keys();
    await Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", function (event) {
  var req = event.request;
  if (req.method !== "GET") return;
  var url;
  try { url = new URL(req.url); } catch (e) { return; }
  if (url.origin !== self.location.origin) return;
  if (url.pathname.indexOf("/blobs/") >= 0) return; // downloads: network-only

  if (req.mode === "navigate") {
    event.respondWith((async function () {
      try {
        var fresh = await fetch(req);
        // Cache only healthy page responses as the offline fallback; an
        // error/redirect response must never become the cached shell.
        if (fresh && fresh.ok) {
          try {
            var cache = await caches.open(CACHE);
            await cache.put("./", fresh.clone());
          } catch (e2) { /* cache full/blocked — serving fresh is enough */ }
        }
        return fresh;
      } catch (e) {
        var cache2 = await caches.open(CACHE);
        var cached = await cache2.match("./");
        return cached || Response.error();
      }
    })());
    return;
  }

  if (url.pathname.indexOf("/assets/") >= 0) {
    event.respondWith((async function () {
      var cache = await caches.open(CACHE);
      var cached = await cache.match(req);
      var network = fetch(req).then(function (res) {
        if (res && res.ok) cache.put(req, res.clone());
        return res;
      }).catch(function () { return cached; });
      return cached || network;
    })());
  }
});

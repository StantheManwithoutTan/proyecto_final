const CACHE_NAME = 'user-urls-cache-v1';
const USER_URL_API = '/api/urls/user'; // The API endpoint for user-specific URLs

// Add URLs of essential static assets for the url_home page
const STATIC_ASSETS_TO_CACHE = [
    '/url_home.html',
    '/style.css',
    '/favicon.ico', // Example: Add your favicon or other core assets
    // Add paths to any essential JavaScript files loaded by url_home.html
    // e.g., '/js/urlHomeLogic.js'
    // Note: Bootstrap and other libraries loaded from CDNs won't be cached by this SW by default.
];

// Install event: Cache static assets
self.addEventListener('install', event => {
    console.log('[Service Worker] Installing...');
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                console.log('[Service Worker] Caching static assets');
                // Cache API endpoint initially (optional, provides immediate offline fallback)
                // const apiRequest = new Request(USER_URL_API, { headers: { 'Accept': 'application/json' } });
                // cache.add(apiRequest); // Be careful with auth headers here if needed immediately
                return cache.addAll(STATIC_ASSETS_TO_CACHE);
            })
            .catch(error => {
                console.error('[Service Worker] Failed to cache static assets:', error);
            })
    );
    self.skipWaiting(); // Activate worker immediately
});

// Activate event: Clean up old caches
self.addEventListener('activate', event => {
    console.log('[Service Worker] Activating...');
    event.waitUntil(
        caches.keys().then(cacheNames => {
            return Promise.all(
                cacheNames.map(cacheName => {
                    if (cacheName !== CACHE_NAME) {
                        console.log('[Service Worker] Deleting old cache:', cacheName);
                        return caches.delete(cacheName);
                    }
                })
            );
        })
    );
    return self.clients.claim(); // Take control of uncontrolled clients
});

// Fetch event: Handle requests
self.addEventListener('fetch', event => {
    const requestUrl = new URL(event.request.url);

    // Strategy for the user URL API: Network first, then cache
    if (requestUrl.pathname === USER_URL_API) {
        // IMPORTANT: Service workers cannot directly access localStorage for the token.
        // The fetch request from the page *must* include the Authorization header.
        // The SW intercepts the request *with* its headers.
        event.respondWith(
            fetch(event.request)
                .then(networkResponse => {
                    // If fetch is successful, clone it, cache it, and return it
                    console.log('[Service Worker] Fetched from network:', event.request.url);
                    const responseToCache = networkResponse.clone();
                    caches.open(CACHE_NAME)
                        .then(cache => {
                            console.log('[Service Worker] Caching API response:', event.request.url);
                            cache.put(event.request, responseToCache); // Cache the request with headers
                        });
                    return networkResponse;
                })
                .catch(error => {
                    // If fetch fails (offline), try to get from cache
                    console.log('[Service Worker] Network fetch failed, trying cache for:', event.request.url, error);
                    return caches.match(event.request)
                        .then(cachedResponse => {
                            if (cachedResponse) {
                                console.log('[Service Worker] Serving API from cache:', event.request.url);
                                return cachedResponse;
                            }
                            // Optional: Return a custom offline response if not in cache
                            console.warn('[Service Worker] API not in cache and network failed:', event.request.url);
                            // return new Response(JSON.stringify({ error: 'Offline and data not cached' }), {
                            //     headers: { 'Content-Type': 'application/json' },
                            //     status: 503 // Service Unavailable
                            // });
                            return undefined; // Let the browser handle the error
                        });
                })
        );
    }
    // Strategy for static assets: Cache first, then network
    else if (STATIC_ASSETS_TO_CACHE.includes(requestUrl.pathname)) {
         event.respondWith(
            caches.match(event.request)
                .then(cachedResponse => {
                    if (cachedResponse) {
                        // console.log('[Service Worker] Serving static asset from cache:', event.request.url);
                        return cachedResponse;
                    }
                    // Not in cache - fetch from network (should have been cached on install, but as fallback)
                    console.log('[Service Worker] Static asset not in cache, fetching from network:', event.request.url);
                    return fetch(event.request);
                }
            )
        );
    }
    // For other requests (e.g., CDNs, other APIs), just fetch normally (pass through)
    // else {
    //     // console.log('[Service Worker] Passing through request:', event.request.url);
    //     // Default browser behavior applies if not handled:
    //     // event.respondWith(fetch(event.request));
    // }
});
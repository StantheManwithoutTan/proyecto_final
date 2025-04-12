const CACHE_VERSION = 'url-shortener-v2';
const STATIC_CACHE = `static-${CACHE_VERSION}`;
const DATA_CACHE = `data-${CACHE_VERSION}`;

// Files to cache
const STATIC_FILES = [
  '/url_home.html',
  '/login.html',
  '/style.css',
  '/offline.html'
  // Remove external resources that may be causing errors
  // You can add them back one by one to identify which is failing
];

// Message event - store authentication data
self.addEventListener('message', event => {
  if (event.data && event.data.type === 'STORE_AUTH_DATA') {
    // Store token for offline use
    self.__TOKEN = event.data.token;
    console.log('[Service Worker] Authentication token stored');
  }
});

// Install event - cache essential files
self.addEventListener('install', event => {
  console.log('[Service Worker] Installing...');
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then(cache => {
        console.log('[Service Worker] Caching static files');
        // Cache each file individually to prevent one failure from breaking everything
        return Promise.all(
          STATIC_FILES.map(url => {
            return cache.add(url).catch(error => {
              console.error(`[Service Worker] Failed to cache: ${url}`, error);
            });
          })
        );
      })
  );
  self.skipWaiting();
});

// Activate event - clean up old caches
self.addEventListener('activate', event => {
  console.log('[Service Worker] Activating...');
  event.waitUntil(
    caches.keys()
      .then(keyList => {
        return Promise.all(
          keyList.map(key => {
            if (key !== STATIC_CACHE && key !== DATA_CACHE) {
              console.log('[Service Worker] Removing old cache:', key);
              return caches.delete(key);
            }
          })
        );
      })
  );
  return self.clients.claim();
});

// Fetch event - handle requests
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  
  // Handle API requests
  if (url.pathname.startsWith('/api/')) {
    // Special handling for user URLs API 
    if (url.pathname === '/api/urls/user') {
      event.respondWith(
        fetch(event.request)
          .then(response => {
            if (response.ok) {
              const clonedResponse = response.clone();
              caches.open(DATA_CACHE)
                .then(cache => {
                  cache.put(event.request, clonedResponse);
                  console.log('[Service Worker] URLs data cached successfully');
                });
            }
            return response;
          })
          .catch(error => {
            console.log('[Service Worker] Network error, fetching from cache:', error);
            return caches.match(event.request)
              .then(cachedResponse => {
                if (cachedResponse) {
                  console.log('[Service Worker] Returning cached URLs data');
                  return cachedResponse;
                }
                // If nothing in cache, return empty array with offline indicator
                console.log('[Service Worker] No cached data, returning empty array');
                return new Response(JSON.stringify({
                  offline: true,
                  urls: []
                }), {
                  headers: { 'Content-Type': 'application/json' }
                });
              });
          })
      );
    } else {
      // For other API requests
      event.respondWith(
        fetch(event.request)
          .catch(() => {
            return caches.match(event.request);
          })
      );
    }
  } 
  // Handle page navigation 
  else if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .catch(() => {
          console.log('[Service Worker] Serving cached page for navigation');
          return caches.match(event.request)
            .then(cachedResponse => {
              if (cachedResponse) {
                return cachedResponse;
              }
              return caches.match('/offline.html');
            });
        })
    );
  }
  // Handle static assets (CSS, JS, images)
  else {
    event.respondWith(
      caches.match(event.request)
        .then(cachedResponse => {
          if (cachedResponse) {
            return cachedResponse;
          }
          return fetch(event.request)
            .then(response => {
              if (response.ok) {
                const responseToCache = response.clone();
                caches.open(STATIC_CACHE)
                  .then(cache => {
                    cache.put(event.request, responseToCache);
                  });
              }
              return response;
            });
        })
    );
  }
});
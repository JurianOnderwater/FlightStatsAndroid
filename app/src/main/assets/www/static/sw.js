// static/sw.js

// Increment the cache name to ensure the service worker updates
const CACHE_NAME = 'flightstats-cache-v2';
const URLS_TO_CACHE = [
    '/',
    '/flights',
    '/static/css/style.css',
    '/static/js/app.js',
    '/static/js/map.js',
    '/static/manifest.json',
    '/static/assets/favicon.svg',
    '/static/airports.csv',
    '/static/countries_with_a2.geojson' // Use the new file
];

// Install event: This will now log each file as it caches it.
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                console.log('Opened cache. Caching individual files...');
                const promises = URLS_TO_CACHE.map(url => {
                    return fetch(url)
                        .then(response => {
                            if (!response.ok) {
                                // This will immediately tell us which file has an issue (like a 404)
                                throw new TypeError(`Request for ${url} failed with status ${response.status}`);
                            }
                            console.log(`Successfully fetched and caching: ${url}`);
                            return cache.put(url, response);
                        })
                        .catch(err => {
                            // This will catch network errors for a specific file
                            console.error(`Failed to fetch and cache ${url}`, err);
                            throw err; // Re-throw to ensure the installation fails
                        });
                });
                return Promise.all(promises);
            })
    );
});

// Fetch event: This part handles serving from the cache once installed.
self.addEventListener('fetch', event => {
    // We only want to cache GET requests for our app pages, not API calls.
    if (event.request.method === 'GET' && !event.request.url.includes('/api/data')) {
        event.respondWith(
            caches.match(event.request)
                .then(response => {
                    // If we find a match in the cache, return it. Otherwise, fetch from the network.
                    return response || fetch(event.request);
                })
        );
    }
});
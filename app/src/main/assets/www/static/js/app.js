// static/js/app.js

document.addEventListener('DOMContentLoaded', function() {
    // --- Add Flight Button Navigation ---
    // This makes the button in the footer work.
    const addFlightFab = document.querySelector('#add-flight-fab');
    if (addFlightFab) {
        addFlightFab.addEventListener('click', () => {
            const url = addFlightFab.getAttribute('data-url');
            if (url) {
                window.location.href = url;
            }
        });
    }

    // --- Service Worker Registration for PWA ---
    // This makes the app installable and work offline.
    if ('serviceWorker' in navigator) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register('/static/sw.js')
                .then(registration => {
                    console.log('ServiceWorker registration successful: ', registration.scope);
                })
                .catch(err => {
                    console.error('ServiceWorker registration failed: ', err);
                });
        });
    }
});
// static/js/map.js

/**
 * Calculates a series of points along a great-circle arc.
 */
function getGreatCirclePoints(start, end) {
    const points = [];
    const numPoints = 100;
    const lat1 = start.lat * Math.PI / 180, lon1 = start.lng * Math.PI / 180;
    const lat2 = end.lat * Math.PI / 180, lon2 = end.lng * Math.PI / 180;
    const d = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin((lat1 - lat2) / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon1 - lon2) / 2), 2)));
    for (let i = 0; i <= numPoints; i++) {
        const f = i / numPoints;
        if (Math.sin(d) === 0) { points.push([start.lat, start.lng]); continue; }
        const A = Math.sin((1 - f) * d) / Math.sin(d);
        const B = Math.sin(f * d) / Math.sin(d);
        const x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
        const y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
        const z = A * Math.sin(lat1) + B * Math.sin(lat2);
        const lat = Math.atan2(z, Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2))) * 180 / Math.PI;
        const lon = Math.atan2(y, x) * 180 / Math.PI;
        points.push([lat, lon]);
    }
    return points;
}

/**
 * Helper to get current CSS variables
 */
function getThemeColors() {
    const styles = getComputedStyle(document.documentElement);
    return {
        primary: styles.getPropertyValue('--md-sys-color-primary').trim(),
        primaryContainer: styles.getPropertyValue('--md-sys-color-primary-container').trim(),
        onPrimaryContainer: styles.getPropertyValue('--md-sys-color-on-primary-container').trim(),
        secondary: styles.getPropertyValue('--md-sys-color-secondary').trim(),
        secondaryContainer: styles.getPropertyValue('--md-sys-color-secondary-container').trim(),
        tertiary: styles.getPropertyValue('--md-sys-color-tertiary').trim(),
        tertiaryContainer: styles.getPropertyValue('--md-sys-color-tertiary-container').trim(),
        surfaceVariant: styles.getPropertyValue('--md-sys-color-surface-variant').trim(),
        surfaceContainerLow: styles.getPropertyValue('--md-sys-color-surface-container-low').trim(),
        surfaceBorder: styles.getPropertyValue('--md-sys-color-surface').trim(),
        compYellow: styles.getPropertyValue('--md-sys-color-primary-comp-yellow').trim(),
        compPurple: styles.getPropertyValue('--md-sys-color-primary-comp-purple').trim()
    };
}

document.addEventListener('DOMContentLoaded', () => {
    // --- Initialise Viewers ---
    const map = L.map('map', {
        maxBounds: [[-90, -180], [90, 180]]
    }).setView([20, 0], 2);
    
    L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
    }).addTo(map);

    // --- Initialise ECharts Globe View ---
    const globeContainer = document.getElementById('cesium-container'); // Reusing the old container
    const globeChart = echarts.init(globeContainer);

    // --- Setup UI and Data Storage ---
    const layersByYear = {};
    const loader = document.getElementById('loader-container');
    let allEchartsRoutes = []; // To store flight data for ECharts
    let longestFlightLayer = null;
    let shortestFlightLayer = null;
    const viewSwitch = document.getElementById('view-switch');
    const leafletContainer = document.getElementById('map');

    viewSwitch.addEventListener('input', () => {
        if (!viewSwitch.selected) {
            leafletContainer.style.display = 'block';
            globeContainer.style.display = 'none';
        } else {
            leafletContainer.style.display = 'none';
            globeContainer.style.display = 'block';
            globeChart.resize(); // Important: resize chart when it becomes visible
        }
    });
    leafletContainer.style.display = 'block';
    globeContainer.style.display = 'none';
    viewSwitch.selected = false;

    if (loader) loader.style.display = 'flex';

    // --- Fetch and Process Data ---
    fetch('static/airports.csv')
        .then(response => response.text())
        .then(csvText => {
            Papa.parse(csvText, {
                header: true, skipEmptyLines: true,
                complete: async (results) => {
                    const airportData = new Map();
                    results.data.forEach(row => {
                        if (row.iata_code && row.latitude_deg && row.longitude_deg) {
                            airportData.set(row.iata_code, {
                                lat: parseFloat(row.latitude_deg), 
                                lng: parseFloat(row.longitude_deg), 
                                country: row.iso_country,
                                name: row.name, // Also store the name for the datalist
                                city: row.municipality // And the city
                            });
                        }
                    });
                    localStorage.setItem('airportData', JSON.stringify(Array.from(airportData.entries())));

                    console.log(`Finished parsing and cached ${airportData.size} airports.`);
                    
                    const allFlights = await getAllFlights();
                    const { uniqueYears } = calculateAndDisplayStats(allFlights, airportData);
                    const aggregatedRoutes = new Map();
                    const colors = getThemeColors(); // Get initial colors

                    // --- Prepare Data and Draw for Both Views ---
                    allFlights.forEach(flight => {
                        const origin = airportData.get(flight.origin);
                        const dest = airportData.get(flight.destination);
                        const canonicalRoute = [flight.origin, flight.destination].sort().join('-');
                        if (!aggregatedRoutes.has(canonicalRoute)) {
                            aggregatedRoutes.set(canonicalRoute, { count: 0, maxDate: '1900-01-01' });
                        }
                        const routeData = aggregatedRoutes.get(canonicalRoute);
                        routeData.count += 1;
                        if (flight.date > routeData.maxDate) {
                            routeData.maxDate = flight.date;
                        }

                        if (origin && dest && !isNaN(origin.lat) && !isNaN(dest.lat)) {
                            const lineColour = colors.primary; // Use theme color
                            const lineWeight = routeData.count
                            const year = new Date(flight.date).getFullYear();
                            
                            const startPointL = L.latLng(origin.lat, origin.lng);
                            const endPointL = L.latLng(dest.lat, dest.lng);
                            const curvePoints = getGreatCirclePoints(startPointL, endPointL);
                            const leafletLine = L.polyline(curvePoints, { color: lineColour, weight: lineWeight, opacity: 0.7, className: 'flight-line' }); // Added class for easier selection if needed
                            const markerOptions = { radius: 3, fillColor: lineColour, color: "#000", weight: 0.5, opacity: 1, fillOpacity: 0.8, className: 'flight-dot' };
                            const tooltipOptions = { permanent: true, direction: 'top', offset: [0, -5], className: 'airport-label' };
                            
                            const startDot = L.circleMarker(startPointL, markerOptions).bindTooltip(flight.origin, tooltipOptions).openTooltip();
                            const endDot = L.circleMarker(endPointL, markerOptions).bindTooltip(flight.destination, tooltipOptions).openTooltip();
                            const leafletLayer = L.featureGroup([leafletLine, startDot, endDot]);

                            if (!layersByYear[year]) layersByYear[year] = [];
                            layersByYear[year].push({ leaflet: leafletLayer, line: leafletLine, dots: [startDot, endDot] });

                            const lineWeightDouble = lineWeight * 2;
                            allEchartsRoutes.push({
                                year: year,
                                coords: [[origin.lng, origin.lat], [dest.lng, dest.lat]],
                                weight: lineWeightDouble
                            });
                        }
                    });

                    // --- Configure and Render Globe ---
                    globeChart.setOption({
                        backgroundColor: '#000011',
                        globe: {
                            baseTexture: '/static/textures/world.topo.bathy.200401.desat2.jpg',
                            heightTexture: '/static/textures/bathymetry_bw_composite_4k.jpg',
                            shading: 'lambert',
                            light: { ambient: { intensity: 0.5 }, main: { intensity: 0.6 } },
                            viewControl: { autoRotate: true, autoRotateSpeed: 1.1 }
                        },
                        series: {
                            type: 'lines3D',
                            coordinateSystem: 'globe',
                            blendMode: 'lighter',
                            effect: { show: true, constantSpeed: 5 },
                            lineStyle: { color: colors.primary, opacity: 0.7 }, // Use theme color
                            data: allEchartsRoutes.map(route => ({
                                coords: route.coords,
                                lineStyle: {
                                    width: route.weight,
                                }
                            }))
                        }
                    });
                
                    // --- Create Filter Chips ---
                    const chipContainer = document.getElementById('chip-container');
                    chipContainer.innerHTML = '';
                    const selectedYears = new Set(uniqueYears);

                    uniqueYears.forEach(year => {
                        const chip = document.createElement('md-filter-chip');
                        chip.label = String(year);
                        chip.selected = true;
                        chip.addEventListener('click', () => {
                            if (chip.selected) selectedYears.add(year);
                            else selectedYears.delete(year);

                            // Filter Map
                            const leafletLayers = layersByYear[year] || [];
                            leafletLayers.forEach(l => chip.selected ? map.addLayer(l.leaflet) : map.removeLayer(l.leaflet));

                            // Filter Globe
                            const filteredRoutes = allEchartsRoutes.filter(r => selectedYears.has(r.year));
                            globeChart.setOption({ series: { data: filteredRoutes.map(r => r.coords) } });
                        });
                        chipContainer.appendChild(chip);
                    });

                    for (const year in layersByYear) {
                        layersByYear[year].forEach(l => map.addLayer(l.leaflet));
                    }

                    // --- Find and Draw Longest/Shortest Flights ---
                    if (allFlights.length >= 2) {
                        allFlights.forEach(flight => {
                            if (typeof flight.distance === 'undefined') {
                                const origin = airportData.get(flight.origin);
                                const dest = airportData.get(flight.destination);
                                if (origin && dest && !isNaN(origin.lat)) {
                                    flight.distance = haversine(origin.lat, origin.lng, dest.lat, dest.lng);
                                } else {
                                    flight.distance = 0;
                                }
                            }
                        });

                        const sortedByDist = [...allFlights].filter(f => f.distance > 0).sort((a, b) => a.distance - b.distance);
                        
                        if (sortedByDist.length > 0) {
                            const shortestFlight = sortedByDist[0];
                            const longestFlight = sortedByDist[sortedByDist.length - 1];

                            // Draw the longest flight
                            if (longestFlight) {
                                const origin = airportData.get(longestFlight.origin);
                                const dest = airportData.get(longestFlight.destination);
                                const startPoint = L.latLng(origin.lat, origin.lng);
                                const endPoint = L.latLng(dest.lat, dest.lng);
                                const curvePoints = getGreatCirclePoints(startPoint, endPoint);
                                longestFlightLayer = L.polyline(curvePoints, { color: colors.compYellow, weight: 4, opacity: 1, dashArray: '5, 5' }).addTo(map);
                            }

                            // Draw the shortest flight
                            if (shortestFlight && shortestFlight.id !== longestFlight.id) {
                                const origin = airportData.get(shortestFlight.origin);
                                const dest = airportData.get(shortestFlight.destination);
                                const startPoint = L.latLng(origin.lat, origin.lng);
                                const endPoint = L.latLng(dest.lat, dest.lng);
                                const curvePoints = getGreatCirclePoints(startPoint, endPoint);
                                shortestFlightLayer = L.polyline(curvePoints, { color: colors.compPurple, weight: 4, opacity: 1, dashArray: '5, 5' }).addTo(map);
                            }
                        }
                    }
                    
                    if (loader) loader.style.display = 'none';

                    // --- Theme Change Listener ---
                    window.addEventListener('themeChanged', () => {
                        const newColors = getThemeColors();

                        // Update Leaflet layers
                        for (const year in layersByYear) {
                            layersByYear[year].forEach(item => {
                                item.line.setStyle({ color: newColors.primary });
                                item.dots.forEach(dot => {
                                    dot.setStyle({ fillColor: newColors.primary });
                                });
                            });
                        }

                        // Update Longest/Shortest
                        if (longestFlightLayer) longestFlightLayer.setStyle({ color: newColors.compYellow });
                        if (shortestFlightLayer) shortestFlightLayer.setStyle({ color: newColors.compPurple });

                        // Update Globe
                        globeChart.setOption({
                            series: {
                                lineStyle: { color: newColors.primary }
                            }
                        });
                    });
                }
            });
        })
        .catch(error => {
            console.error("Failed to fetch airports.csv:", error);
            if (loader) loader.style.display = 'none';
        });
});
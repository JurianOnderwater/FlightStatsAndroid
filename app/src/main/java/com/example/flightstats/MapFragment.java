package com.example.flightstats;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AirportImporter;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.CsvImporter;
import com.example.flightstats.data.Flight;
import com.google.android.material.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private MapView mapView;
    private TextView statFlights, statCountries, statAirports, statRoutes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);

        // OSMDroid initialisation
        Configuration.getInstance().load(requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext()));
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        mapView = view.findViewById(R.id.map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        
        // Prevent infinite zooming and scrolling
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(12.0);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0));

        mapView.getController().setZoom(3.0);
        mapView.getController().setCenter(new GeoPoint(30.0, 15.0));

        statFlights   = view.findViewById(R.id.stat_flights);
        statCountries = view.findViewById(R.id.stat_countries);
        statAirports  = view.findViewById(R.id.stat_airports);
        statRoutes    = view.findViewById(R.id.stat_routes);

        // Sequential: import airports → import flights → render
        AirportImporter.importIfNeeded(requireContext(), ignored ->
                CsvImporter.importIfNeeded(requireContext(), ignored2 ->
                        loadMapAndStats()));

        return view;
    }

    /** Can be called externally (e.g. after adding a flight) to refresh. */
    public void refresh() {
        if (mapView != null) mapView.getOverlays().clear();
        loadMapAndStats();
    }

    private void loadMapAndStats() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            List<Flight> flights = db.flightDao().getAllFlights();

            Set<String> airportSet = new HashSet<>();
            Set<String> countrySet = new HashSet<>();

            // Count bi-directional route frequencies
            Map<String, Integer>  routeFreq    = new HashMap<>();
            Map<String, GeoPoint> positionCache = new HashMap<>();
            // Store ordered route data (from/to based on first encounter)
            Map<String, RouteData> routeDataMap = new HashMap<>();

            for (Flight f : flights) {
                airportSet.add(f.origin);
                airportSet.add(f.destination);

                String[] pair = {f.origin, f.destination};
                Arrays.sort(pair);
                String routeKey = pair[0] + "-" + pair[1];

                // Increment frequency
                routeFreq.put(routeKey, routeFreq.getOrDefault(routeKey, 0) + 1);

                // Resolve airport positions (cached)
                GeoPoint fromPt = resolvePosition(db, positionCache, f.origin);
                GeoPoint toPt   = resolvePosition(db, positionCache, f.destination);

                // Track countries
                Airport oAirport = db.airportDao().getByIata(f.origin);
                Airport dAirport = db.airportDao().getByIata(f.destination);
                if (oAirport != null) countrySet.add(oAirport.country);
                if (dAirport != null) countrySet.add(dAirport.country);

                if (fromPt != null && toPt != null && !routeDataMap.containsKey(routeKey)) {
                    routeDataMap.put(routeKey, new RouteData(fromPt, toPt, f.origin, f.destination));
                }
            }

            int finalFlights   = flights.size();
            int finalAirports  = airportSet.size();
            int finalRoutes    = routeDataMap.size();
            int finalCountries = countrySet.size();

            // Resolve theme colorPrimary on background thread is unsafe — pass key/value map to UI thread
            final Map<String, Integer>  finalRouteFreq    = routeFreq;
            final Map<String, RouteData> finalRouteDataMap = routeDataMap;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (getView() == null || !isAdded()) return;
                mapView.getOverlays().clear();

                // Resolve theme colorPrimary on the main thread
                TypedValue tv = new TypedValue();
                requireContext().getTheme().resolveAttribute(
                        com.google.android.material.R.attr.colorPrimary, tv, true);
                int primaryColor = tv.data;
                // Apply 80% alpha for a nice semi-transparent look
                int routeColor = (0xCC000000 & 0xFF000000) | (primaryColor & 0x00FFFFFF);

                float density = requireContext().getResources().getDisplayMetrics().density;

                // Draw tapered spindle arcs ordered by frequency (least frequent first so busy routes render on top)
                List<String> routeKeys = new ArrayList<>(finalRouteDataMap.keySet());
                routeKeys.sort((a, b) -> finalRouteFreq.getOrDefault(a, 1) - finalRouteFreq.getOrDefault(b, 1));

                for (String key : routeKeys) {
                    RouteData r = finalRouteDataMap.get(key);
                    int freq = finalRouteFreq.getOrDefault(key, 1);
                    List<GeoPoint> arc = GeodesicHelper.greatCircleArc(r.from, r.to, 64);
                    TaperedRouteOverlay overlay = new TaperedRouteOverlay(arc, freq, routeColor, density);
                    mapView.getOverlays().add(overlay);
                }

                // Draw M3 circle markers on top (deduplicated by airport)
                Set<String> drawnAirports = new HashSet<>();
                for (RouteData r : finalRouteDataMap.values()) {
                    drawMarker(r.from, r.fromCode, drawnAirports);
                    drawMarker(r.to,   r.toCode,   drawnAirports);
                }

                mapView.invalidate();

                statFlights.setText(String.valueOf(finalFlights));
                statAirports.setText(String.valueOf(finalAirports));
                statRoutes.setText(String.valueOf(finalRoutes));
                statCountries.setText(String.valueOf(finalCountries));
            });
        });
    }

    private void drawMarker(GeoPoint pt, String iata, Set<String> drawn) {
        if (drawn.contains(iata)) return;
        drawn.add(iata);
        Marker m = new Marker(mapView);
        m.setPosition(pt);
        m.setTitle(iata);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Drawable icon = AirportMarkerIcon.create(requireContext(), iata);
        m.setIcon(icon);
        mapView.getOverlays().add(m);
    }

    private GeoPoint resolvePosition(AppDatabase db, Map<String, GeoPoint> cache, String iata) {
        if (cache.containsKey(iata)) return cache.get(iata);
        Airport a = db.airportDao().getByIata(iata);
        if (a == null) return null;
        GeoPoint pt = new GeoPoint(a.lat, a.lng);
        cache.put(iata, pt);
        return pt;
    }

    @Override public void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override public void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    private static class RouteData {
        GeoPoint from, to;
        String fromCode, toCode;
        RouteData(GeoPoint from, GeoPoint to, String fromCode, String toCode) {
            this.from = from; this.to = to;
            this.fromCode = fromCode; this.toCode = toCode;
        }
    }
}

package com.example.flightstats;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
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

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
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
            Set<String> routeSet   = new HashSet<>();
            Set<String> countrySet = new HashSet<>();

            // Collect unique routes and resolve airport positions
            Map<String, GeoPoint> positionCache = new HashMap<>();
            List<RouteData> routes = new ArrayList<>();

            for (Flight f : flights) {
                airportSet.add(f.origin);
                airportSet.add(f.destination);

                String[] pair = {f.origin, f.destination};
                Arrays.sort(pair);
                String routeKey = pair[0] + "-" + pair[1];

                // Resolve airport positions (cached)
                GeoPoint fromPt = resolvePosition(db, positionCache, f.origin);
                GeoPoint toPt   = resolvePosition(db, positionCache, f.destination);

                // Track countries
                Airport oAirport = db.airportDao().getByIata(f.origin);
                Airport dAirport = db.airportDao().getByIata(f.destination);
                if (oAirport != null) countrySet.add(oAirport.country);
                if (dAirport != null) countrySet.add(dAirport.country);

                if (fromPt != null && toPt != null && !routeSet.contains(routeKey)) {
                    routeSet.add(routeKey);
                    routes.add(new RouteData(fromPt, toPt, f.origin, f.destination));
                }
            }

            int finalFlights   = flights.size();
            int finalAirports  = airportSet.size();
            int finalRoutes    = routeSet.size();
            int finalCountries = countrySet.size();

            new Handler(Looper.getMainLooper()).post(() -> {
                if (getView() == null || !isAdded()) return;
                mapView.getOverlays().clear();

                // Draw geodesic route arcs
                for (RouteData r : routes) {
                    Polyline line = new Polyline(mapView);
                    List<GeoPoint> arc = GeodesicHelper.greatCircleArc(r.from, r.to, 64);
                    line.setPoints(arc);
                    line.setColor(0xCC1976D2); // M3 primary blue
                    line.setWidth(2.5f);
                    line.setTitle(r.fromCode + " → " + r.toCode);
                    mapView.getOverlays().add(line);
                }

                // Draw M3 circle markers (deduplicated by airport)
                Set<String> drawnAirports = new HashSet<>();
                for (RouteData r : routes) {
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

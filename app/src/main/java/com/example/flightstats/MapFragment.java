package com.example.flightstats;

import android.content.SharedPreferences;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AirportImporter;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.CsvImporter;
import com.example.flightstats.data.Flight;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ImageButton;
import android.view.Gravity;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;

public class MapFragment extends Fragment {

    private MapView mapView;

    private MaterialCardView btnProfile;
    private TextView textProfileInitial;

    private boolean isInitialLoad = true;

    // Config state tracking
    private String currentHometown = "";
    private String currentUserName = "";
    private String currentMapStyle = "";
    private String currentPreferredUnit = "";

    // Dialog stats counts
    private int totalFlightsCount = 0;
    private int totalCountriesCount = 0;
    private int totalAirportsCount = 0;
    private int totalRoutesCount = 0;

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
        mapView.setMinZoomLevel(4.0);
        mapView.setMaxZoomLevel(12.0);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setScrollableAreaLimitDouble(new org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0));



        // Find Settings Views
        btnProfile = view.findViewById(R.id.btn_profile);
        textProfileInitial = view.findViewById(R.id.text_profile_initial);

        View profileContainer = view.findViewById(R.id.profile_container);
        if (profileContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(profileContainer, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                
                int topInset = Math.max(systemBars.top, displayCutout.top);
                int endInset = Math.max(systemBars.right, displayCutout.right);
                int startInset = Math.max(systemBars.left, displayCutout.left);
                
                int densityPadding = (int) (16 * v.getResources().getDisplayMetrics().density);
                
                v.setPadding(densityPadding + startInset, densityPadding + topInset, densityPadding + endInset, densityPadding);
                return insets;
            });
        }

        // Initialize SharedPreferences and load configuration state fields
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        currentHometown = prefs.getString("hometown", "AMS").trim().toUpperCase();
        currentUserName = prefs.getString("user_name", "User").trim();
        currentMapStyle = prefs.getString("map_style", "light");
        currentPreferredUnit = prefs.getString("preferred_unit", "km");

        if (textProfileInitial != null && !currentUserName.isEmpty()) {
            textProfileInitial.setText(currentUserName.substring(0, 1).toUpperCase());
        }

        // Tap profile button to open Google App-style profile switcher dialog
        btnProfile.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_card, null);
            
            TextView tvInitial = dialogView.findViewById(R.id.dialog_profile_initial);
            TextView tvName = dialogView.findViewById(R.id.dialog_profile_name);
            TextView tvHometown = dialogView.findViewById(R.id.dialog_profile_hometown);
            TextView tvStatFlights = dialogView.findViewById(R.id.dialog_stat_flights);
            TextView tvStatCountries = dialogView.findViewById(R.id.dialog_stat_countries);
            TextView tvStatAirports = dialogView.findViewById(R.id.dialog_stat_airports);
            ImageButton btnCloseDialog = dialogView.findViewById(R.id.btn_dialog_close);
            MaterialButton btnDialogSettings = dialogView.findViewById(R.id.btn_dialog_settings);

            SharedPreferences dPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String dName = dPrefs.getString("user_name", "User").trim();
            String dHometown = dPrefs.getString("hometown", "AMS").trim().toUpperCase();

            if (tvInitial != null && !dName.isEmpty()) tvInitial.setText(dName.substring(0, 1).toUpperCase());
            if (tvName != null) tvName.setText(dName);
            if (tvHometown != null) tvHometown.setText("Home Airport: " + dHometown);
            if (tvStatFlights != null) tvStatFlights.setText(String.valueOf(totalFlightsCount));
            if (tvStatCountries != null) tvStatCountries.setText(String.valueOf(totalCountriesCount));
            if (tvStatAirports != null) tvStatAirports.setText(String.valueOf(totalAirportsCount));

            AlertDialog profileDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

            if (btnCloseDialog != null) {
                btnCloseDialog.setOnClickListener(dv -> profileDialog.dismiss());
            }

            if (btnDialogSettings != null) {
                btnDialogSettings.setOnClickListener(dv -> {
                    profileDialog.dismiss();
                    android.content.Intent intent = new android.content.Intent(requireContext(), SettingsActivity.class);
                    startActivity(intent);
                });
            }

            profileDialog.show();
        });

        // Morphing FAB & Search Overlay Controls
        FloatingActionButton fabMapAction = view.findViewById(R.id.fab_map_action);
        MaterialCardView cardSearchOverlay = view.findViewById(R.id.card_search_overlay);
        ImageButton btnCloseOverlay = view.findViewById(R.id.btn_close_overlay);
        MaterialButton btnOverlayScan = view.findViewById(R.id.btn_overlay_scan);
        MaterialButton btnOverlayAdd = view.findViewById(R.id.btn_overlay_add);

        if (fabMapAction != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fabMapAction, (v, insets) -> {
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                float density = getResources().getDisplayMetrics().density;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                lp.bottomMargin = (int) (16 * density) + navBarHeight;
                v.setLayoutParams(lp);
                return insets;
            });
        }

        if (cardSearchOverlay != null) {
            ViewCompat.setOnApplyWindowInsetsListener(cardSearchOverlay, (v, insets) -> {
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                float density = getResources().getDisplayMetrics().density;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                lp.bottomMargin = (int) (16 * density) + navBarHeight;
                v.setLayoutParams(lp);
                return insets;
            });
            LayoutShapeHelper.applyToView(cardSearchOverlay);
        }

        if (fabMapAction != null && cardSearchOverlay != null) {
            fabMapAction.setOnClickListener(v -> {
                com.google.android.material.transition.MaterialContainerTransform transform =
                        new com.google.android.material.transition.MaterialContainerTransform();
                transform.setStartView(fabMapAction);
                transform.setEndView(cardSearchOverlay);
                transform.setDuration(400L);
                transform.addTarget(cardSearchOverlay);
                transform.setScrimColor(android.graphics.Color.TRANSPARENT);
                
                androidx.transition.TransitionManager.beginDelayedTransition((ViewGroup) view, transform);
                
                fabMapAction.setVisibility(View.GONE);
                cardSearchOverlay.setVisibility(View.VISIBLE);
            });

            if (btnCloseOverlay != null) {
                btnCloseOverlay.setOnClickListener(v -> {
                    com.google.android.material.transition.MaterialContainerTransform transform =
                            new com.google.android.material.transition.MaterialContainerTransform();
                    transform.setStartView(cardSearchOverlay);
                    transform.setEndView(fabMapAction);
                    transform.setDuration(350L);
                    transform.addTarget(fabMapAction);
                    transform.setScrimColor(android.graphics.Color.TRANSPARENT);
                    
                    androidx.transition.TransitionManager.beginDelayedTransition((ViewGroup) view, transform);
                    
                    cardSearchOverlay.setVisibility(View.GONE);
                    fabMapAction.setVisibility(View.VISIBLE);
                });
            }

            if (btnOverlayScan != null) {
                btnOverlayScan.setOnClickListener(v -> {
                    cardSearchOverlay.setVisibility(View.GONE);
                    fabMapAction.setVisibility(View.VISIBLE);
                    android.content.Intent intent = new android.content.Intent(requireContext(), BarcodeScannerActivity.class);
                    startActivity(intent);
                });
            }

            if (btnOverlayAdd != null) {
                btnOverlayAdd.setOnClickListener(v -> {
                    cardSearchOverlay.setVisibility(View.GONE);
                    fabMapAction.setVisibility(View.VISIBLE);
                    AddFlightBottomSheet sheet = new AddFlightBottomSheet();
                    sheet.setOnFlightAddedListener(this::refresh);
                    sheet.show(getChildFragmentManager(), "add_flight");
                });
            }
        }

        // Sequential: import airports → import flights → render
        AirportImporter.importIfNeeded(requireContext(), ignored ->
                CsvImporter.importIfNeeded(requireContext(), ignored2 ->
                        loadMapAndStats()));

        return view;
    }
    public void refresh() {
        if (mapView != null) mapView.getOverlays().clear();
        loadMapAndStats();
    }

    private void loadMapAndStats() {
        if (!isAdded()) return;
        android.content.Context context = getContext();
        if (context == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String hometown = prefs.getString("hometown", "AMS").trim().toUpperCase();
        float defaultZoom = Math.max(4.0f, prefs.getFloat("default_zoom", 4.5f));
        String mapStyle = prefs.getString("map_style", "light");

        // Apply Map style dynamically
        if ("satellite".equals(mapStyle)) {
            mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.USGS_SAT);
            mapView.getOverlayManager().getTilesOverlay().setColorFilter(null);
        } else {
            mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
            if ("dark".equals(mapStyle)) {
                // High contrast inverted matrix for dark mode style
                float[] colorMatrix = {
                    -0.85f, 0, 0, 0, 220,
                    0, -0.85f, 0, 0, 220,
                    0, 0, -0.85f, 0, 220,
                    0, 0, 0, 1.0f, 0
                };
                mapView.getOverlayManager().getTilesOverlay().setColorFilter(new android.graphics.ColorMatrixColorFilter(colorMatrix));
            } else {
                mapView.getOverlayManager().getTilesOverlay().setColorFilter(null);
            }
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            android.content.Context bgContext = getContext();
            if (bgContext == null) return;
            AppDatabase db = AppDatabase.getDatabase(bgContext);
            List<Flight> dbFlights = db.flightDao().getAllFlights();
            String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            List<Flight> flights = new ArrayList<>();
            for (Flight f : dbFlights) {
                if (f.date != null && f.date.compareTo(todayStr) < 0) {
                    flights.add(f);
                }
            }

            Set<String> airportSet = new HashSet<>();
            Set<String> countrySet = new HashSet<>();

            // Collect unique routes and resolve airport positions
            Map<String, GeoPoint> positionCache = new HashMap<>();
            Map<String, RouteData> routeDataMap = new HashMap<>();

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

                if (fromPt != null && toPt != null && !routeDataMap.containsKey(routeKey)) {
                    routeDataMap.put(routeKey, new RouteData(fromPt, toPt, f.origin, f.destination));
                }
            }

            int finalFlights   = flights.size();
            int finalAirports  = airportSet.size();
            int finalRoutes    = routeDataMap.size();
            int finalCountries = countrySet.size();

            // Find hometown airport details
            Airport hometownAirport = db.airportDao().getByIata(hometown);
            GeoPoint centerPoint = null;
            if (hometownAirport != null) {
                centerPoint = new GeoPoint(hometownAirport.lat, hometownAirport.lng);
            } else {
                // Default fallback: AMS if hometown is not found
                Airport amsAirport = db.airportDao().getByIata("AMS");
                if (amsAirport != null) {
                    centerPoint = new GeoPoint(amsAirport.lat, amsAirport.lng);
                } else {
                    centerPoint = new GeoPoint(52.3105, 4.7683); // AMS raw coords fallback
                }
            }
            final GeoPoint finalCenterPoint = centerPoint;

            // Resolve theme colorPrimary on background thread is unsafe — pass map to UI thread
            final Map<String, RouteData> finalRouteDataMap = routeDataMap;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (getView() == null || !isAdded()) return;
                
                // Save counts for profile card dialog overview
                totalFlightsCount = finalFlights;
                totalCountriesCount = finalCountries;
                totalAirportsCount = finalAirports;
                totalRoutesCount = finalRoutes;

                mapView.getOverlays().clear();

                // Resolve theme colorPrimary on the main thread
                TypedValue tv = new TypedValue();
                requireContext().getTheme().resolveAttribute(
                        androidx.appcompat.R.attr.colorPrimary, tv, true);
                int primaryColor = tv.data;
                // Apply 80% alpha for a nice semi-transparent look
                int routeColor = (0xCC000000 & 0xFF000000) | (primaryColor & 0x00FFFFFF);

                // Collect lines and points for animation
                final List<Polyline> routePolylines = new ArrayList<>();
                final List<List<GeoPoint>> allRoutePoints = new ArrayList<>();

                // Draw route lines using theme colorPrimary (either geodesic or straight)
                boolean isCurved = prefs.getBoolean("map_route_curved", true);
                for (String key : finalRouteDataMap.keySet()) {
                    RouteData r = finalRouteDataMap.get(key);
                    List<GeoPoint> points;
                    if (isCurved) {
                        points = GeodesicHelper.greatCircleArc(r.from, r.to, 64);
                    } else {
                        points = new ArrayList<>();
                        points.add(r.from);
                        points.add(r.to);
                    }
                    Polyline line = new Polyline(mapView);
                    line.setColor(routeColor);
                    line.setWidth(2.5f);
                    line.setTitle(r.fromCode + " → " + r.toCode);
                    
                    // Initially set empty to draw progressively
                    line.setPoints(new ArrayList<>());
                    mapView.getOverlays().add(line);
                    
                    routePolylines.add(line);
                    allRoutePoints.add(points);
                }

                // Draw M3 circle markers on top (deduplicated by airport)
                final List<Marker> airportMarkers = new ArrayList<>();
                Set<String> drawnAirports = new HashSet<>();
                for (RouteData r : finalRouteDataMap.values()) {
                    Marker m1 = drawMarker(r.from, r.fromCode, drawnAirports);
                    if (m1 != null) airportMarkers.add(m1);
                    
                    Marker m2 = drawMarker(r.to,   r.toCode,   drawnAirports);
                    if (m2 != null) airportMarkers.add(m2);
                }

                // Create ValueAnimator for drawing progress and marker fade-in
                ValueAnimator pathAnimator = ValueAnimator.ofFloat(0f, 1f);
                pathAnimator.setDuration(1800L); // 1.8 seconds duration
                pathAnimator.setInterpolator(new DecelerateInterpolator());
                pathAnimator.addUpdateListener(animation -> {
                    if (!isAdded() || mapView == null) return;
                    float fraction = (float) animation.getAnimatedValue();
                    
                    // Update line points
                    for (int i = 0; i < routePolylines.size(); i++) {
                        Polyline line = routePolylines.get(i);
                        List<GeoPoint> fullPoints = allRoutePoints.get(i);
                        int count = Math.max(2, (int) (fullPoints.size() * fraction));
                        if (count <= fullPoints.size()) {
                            line.setPoints(new ArrayList<>(fullPoints.subList(0, count)));
                        } else {
                            line.setPoints(new ArrayList<>(fullPoints));
                        }
                    }
                    
                    // Fade in markers
                    for (Marker marker : airportMarkers) {
                        marker.setAlpha(fraction);
                    }
                    
                    mapView.invalidate();
                });
                pathAnimator.start();

                // If it's the initial load, center and zoom the map
                if (isInitialLoad) {
                    isInitialLoad = false;
                    if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
                        mapView.getController().setZoom((double) defaultZoom);
                        mapView.getController().setCenter(finalCenterPoint);
                    } else {
                        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                if (isAdded() && mapView != null) {
                                    mapView.getController().setZoom((double) defaultZoom);
                                    mapView.getController().setCenter(finalCenterPoint);
                                }
                            }
                        });
                    }
                }

                mapView.invalidate();


            });
        });
    }

    private Marker drawMarker(GeoPoint pt, String iata, Set<String> drawn) {
        if (drawn.contains(iata)) return null;
        drawn.add(iata);
        Marker m = new Marker(mapView);
        m.setPosition(pt);
        m.setTitle(iata);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Drawable icon = AirportMarkerIcon.create(requireContext(), iata);
        m.setIcon(icon);
        m.setAlpha(0f);
        mapView.getOverlays().add(m);
        return m;
    }

    private GeoPoint resolvePosition(AppDatabase db, Map<String, GeoPoint> cache, String iata) {
        if (cache.containsKey(iata)) return cache.get(iata);
        Airport a = db.airportDao().getByIata(iata);
        if (a == null) return null;
        GeoPoint pt = new GeoPoint(a.lat, a.lng);
        cache.put(iata, pt);
        return pt;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();

        // Check if preferences changed in SettingsActivity
        if (isAdded()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String hometown = prefs.getString("hometown", "AMS").trim().toUpperCase();
            String userName = prefs.getString("user_name", "User").trim();
            String mapStyle = prefs.getString("map_style", "light");
            String unit = prefs.getString("preferred_unit", "km");

            boolean needsReload = !hometown.equals(currentHometown)
                    || !mapStyle.equals(currentMapStyle)
                    || !unit.equals(currentPreferredUnit);

            if (needsReload) {
                currentHometown = hometown;
                currentMapStyle = mapStyle;
                currentPreferredUnit = unit;
                refresh();
            }

            // Apply card shape theme preference
            LayoutShapeHelper.applyToView(getView());

            if (!userName.equals(currentUserName)) {
                currentUserName = userName;
                if (textProfileInitial != null && !userName.isEmpty()) {
                    textProfileInitial.setText(userName.substring(0, 1).toUpperCase());
                }
            }
        }
    }
    @Override public void onPause()  { super.onPause();  if (mapView != null) mapView.onPause(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isInitialLoad = true;
    }

    private static class RouteData {
        GeoPoint from, to;
        String fromCode, toCode;
        RouteData(GeoPoint from, GeoPoint to, String fromCode, String toCode) {
            this.from = from; this.to = to;
            this.fromCode = fromCode; this.toCode = toCode;
        }
    }
}

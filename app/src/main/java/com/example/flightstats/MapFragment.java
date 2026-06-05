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
import android.widget.ProgressBar;
import android.view.Gravity;

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
    private TextView statFlights, statCountries, statAirports, statRoutes;

    private MaterialCardView cardSettings;
    private View settingsScrim;
    private MaterialCardView btnProfile;
    private TextInputEditText inputHometown;
    private Slider sliderZoom;
    private com.google.android.material.materialswitch.MaterialSwitch switchAiOverviews;
    private MaterialButtonToggleGroup toggleThemeGroup;
    private MaterialButton btnThemeLight, btnThemeDark, btnThemeSystem;
    private MaterialButton btnClose;

    // Settings nav toggle group and tab contents
    private MaterialButtonToggleGroup settingsNavGroup;
    private androidx.core.widget.NestedScrollView tabContentUser, tabContentMap, tabContentTheme, tabContentAi;
    private MaterialButtonToggleGroup toggleUnitGroup;
    private MaterialButtonToggleGroup toggleRouteGroup;
    private MaterialButtonToggleGroup toggleToneGroup;
    private TextInputEditText inputUserName;
    private TextView textProfileInitial;

    private boolean isInitialLoad = true;

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

        statFlights   = view.findViewById(R.id.stat_flights);
        statCountries = view.findViewById(R.id.stat_countries);
        statAirports  = view.findViewById(R.id.stat_airports);
        statRoutes    = view.findViewById(R.id.stat_routes);

        // Find Settings Views
        btnProfile        = view.findViewById(R.id.btn_profile);
        cardSettings      = view.findViewById(R.id.card_settings);
        settingsScrim     = view.findViewById(R.id.settings_scrim);
        inputHometown     = view.findViewById(R.id.input_hometown);
        sliderZoom        = view.findViewById(R.id.slider_zoom);
        toggleThemeGroup  = view.findViewById(R.id.toggle_theme_group);
        btnThemeLight     = view.findViewById(R.id.btn_theme_light);
        btnThemeDark      = view.findViewById(R.id.btn_theme_dark);
        btnThemeSystem    = view.findViewById(R.id.btn_theme_system);
        btnClose          = view.findViewById(R.id.btn_settings_close);
        switchAiOverviews = view.findViewById(R.id.switch_ai_overviews);
        
        MaterialButton btnRegenerateAllStories = view.findViewById(R.id.btn_regenerate_all_stories);
        if (btnRegenerateAllStories != null) {
            btnRegenerateAllStories.setOnClickListener(v -> regenerateAllStoriesWithAI());
        }

        settingsNavGroup  = view.findViewById(R.id.settings_tab_layout);
        tabContentUser    = view.findViewById(R.id.settings_tab_content_user);
        tabContentMap     = view.findViewById(R.id.settings_tab_content_map);
        tabContentTheme   = view.findViewById(R.id.settings_tab_content_theme);
        tabContentAi      = view.findViewById(R.id.settings_tab_content_ai);
        inputUserName     = view.findViewById(R.id.input_user_name);
        textProfileInitial = view.findViewById(R.id.text_profile_initial);

        toggleUnitGroup   = view.findViewById(R.id.toggle_unit_group);
        toggleRouteGroup  = view.findViewById(R.id.toggle_route_group);
        toggleToneGroup   = view.findViewById(R.id.toggle_tone_group);

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

        // Initialize SharedPreferences and load overlay fields
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String hometownPref = prefs.getString("hometown", "AMS").trim().toUpperCase();
        float zoomPref = Math.max(4.0f, prefs.getFloat("default_zoom", 4.5f));
        int themePref = prefs.getInt("theme_mode", 2); // 0 = Light, 1 = Dark, 2 = System
        boolean aiOverviewsPref = prefs.getBoolean("enable_ai_overviews", true);
        String userNamePref = prefs.getString("user_name", "User").trim();
        if (userNamePref.isEmpty()) userNamePref = "User";

        String unitPref = prefs.getString("preferred_unit", "km");
        boolean routeCurvedPref = prefs.getBoolean("map_route_curved", true);
        String tonePref = prefs.getString("ai_summary_tone", "analytical");

        // Load existing preference values to UI elements without triggering listeners prematurely
        inputHometown.setText(hometownPref);
        sliderZoom.setValue(zoomPref);
        switchAiOverviews.setChecked(aiOverviewsPref);
        inputUserName.setText(userNamePref);
        textProfileInitial.setText(userNamePref.substring(0, 1).toUpperCase());

        if (themePref == 0) {
            toggleThemeGroup.check(R.id.btn_theme_light);
        } else if (themePref == 1) {
            toggleThemeGroup.check(R.id.btn_theme_dark);
        } else {
            toggleThemeGroup.check(R.id.btn_theme_system);
        }

        if (toggleUnitGroup != null) {
            if ("mi".equals(unitPref)) {
                toggleUnitGroup.check(R.id.btn_unit_imperial);
            } else {
                toggleUnitGroup.check(R.id.btn_unit_metric);
            }
        }

        if (toggleRouteGroup != null) {
            if (routeCurvedPref) {
                toggleRouteGroup.check(R.id.btn_route_curved);
            } else {
                toggleRouteGroup.check(R.id.btn_route_straight);
            }
        }

        if (toggleToneGroup != null) {
            if ("narrative".equals(tonePref)) {
                toggleToneGroup.check(R.id.btn_tone_narrative);
            } else {
                toggleToneGroup.check(R.id.btn_tone_analytical);
            }
        }

        // Wire up the icon nav toggle group
        if (settingsNavGroup != null) {
            settingsNavGroup.check(R.id.settings_tab_btn_user);
            settingsNavGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (tabContentUser != null) tabContentUser.setVisibility(checkedId == R.id.settings_tab_btn_user ? View.VISIBLE : View.GONE);
                if (tabContentMap != null) tabContentMap.setVisibility(checkedId == R.id.settings_tab_btn_map ? View.VISIBLE : View.GONE);
                if (tabContentTheme != null) tabContentTheme.setVisibility(checkedId == R.id.settings_tab_btn_theme ? View.VISIBLE : View.GONE);
                if (tabContentAi != null) tabContentAi.setVisibility(checkedId == R.id.settings_tab_btn_ai ? View.VISIBLE : View.GONE);
            });
        }

        // Tap profile button to open floating Settings Card, reset to Profile tab
        btnProfile.setOnClickListener(v -> {
            cardSettings.setVisibility(View.VISIBLE);
            settingsScrim.setVisibility(View.VISIBLE);
            if (settingsNavGroup != null) settingsNavGroup.check(R.id.settings_tab_btn_user);
            if (tabContentUser != null) tabContentUser.setVisibility(View.VISIBLE);
            if (tabContentMap != null) tabContentMap.setVisibility(View.GONE);
            if (tabContentTheme != null) tabContentTheme.setVisibility(View.GONE);
            if (tabContentAi != null) tabContentAi.setVisibility(View.GONE);
        });

        // Click listener to close floating card (simply dismiss since everything is autosaved)
        View.OnClickListener dismissListener = v -> {
            cardSettings.setVisibility(View.GONE);
            settingsScrim.setVisibility(View.GONE);
        };
        if (btnClose != null) {
            btnClose.setOnClickListener(dismissListener);
        }
        settingsScrim.setOnClickListener(dismissListener);

        // Real-time autosave listeners for settings
        inputUserName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String name = s.toString().trim();
                if (name.isEmpty()) name = "User";
                prefs.edit().putString("user_name", name).apply();
                textProfileInitial.setText(name.substring(0, 1).toUpperCase());
            }
        });

        inputHometown.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String iata = s.toString().trim().toUpperCase();
                if (iata.length() == 3) {
                    inputHometown.setError(null);
                    prefs.edit().putString("hometown", iata).apply();
                    isInitialLoad = true;
                    loadMapAndStats();
                } else {
                    inputHometown.setError("Please enter a valid 3-letter IATA code");
                }
            }
        });

        sliderZoom.addOnChangeListener((slider, value, fromUser) -> {
            prefs.edit().putFloat("default_zoom", value).apply();
            if (mapView != null) {
                mapView.getController().setZoom((double) value);
            }
        });

        toggleThemeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int themeMode = 2; // System Auto
            if (checkedId == R.id.btn_theme_light) {
                themeMode = 0;
            } else if (checkedId == R.id.btn_theme_dark) {
                themeMode = 1;
            }
            prefs.edit().putInt("theme_mode", themeMode).apply();
            
            if (themeMode == 0) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (themeMode == 1) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        });

        if (toggleUnitGroup != null) {
            toggleUnitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                String unit = (checkedId == R.id.btn_unit_imperial) ? "mi" : "km";
                prefs.edit().putString("preferred_unit", unit).apply();
                loadMapAndStats();
            });
        }

        if (toggleRouteGroup != null) {
            toggleRouteGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                boolean curved = (checkedId == R.id.btn_route_curved);
                prefs.edit().putBoolean("map_route_curved", curved).apply();
                loadMapAndStats();
            });
        }

        if (toggleToneGroup != null) {
            toggleToneGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                String tone = (checkedId == R.id.btn_tone_narrative) ? "narrative" : "analytical";
                prefs.edit().putString("ai_summary_tone", tone).apply();
            });
        }

        switchAiOverviews.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("enable_ai_overviews", isChecked).apply();
        });

        // Sequential: import airports → import flights → render
        AirportImporter.importIfNeeded(requireContext(), ignored ->
                CsvImporter.importIfNeeded(requireContext(), ignored2 ->
                        loadMapAndStats()));

        return view;
    }

    /** Can be called externally (e.g. after adding a flight) to refresh. */

    private void regenerateAllStoriesWithAI() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setIndeterminate(true);
        layout.addView(progressBar);

        final TextView textView = new TextView(requireContext());
        textView.setText("Initializing on-device AI Core...");
        textView.setTextSize(14f);
        textView.setTextAppearance(requireContext(), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        textView.setPadding((int) (16 * getResources().getDisplayMetrics().density), 0, 0, 0);
        layout.addView(textView);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gemini Nano On-Device AI")
            .setView(layout)
            .setCancelable(false)
            .create();
        dialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(requireContext());
                List<Flight> dbFlights = db.flightDao().getAllFlights();
                String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                List<Flight> allFlights = new ArrayList<>();
                for (Flight f : dbFlights) {
                    if (f.date != null && f.date.compareTo(todayStr) < 0) {
                        allFlights.add(f);
                    }
                }
                
                Set<String> yearsSet = new HashSet<>();
                for (Flight f : allFlights) {
                    if (f.date != null && f.date.length() >= 4) {
                        yearsSet.add(f.date.substring(0, 4));
                    }
                }
                String currentCalendarYear = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR));
                yearsSet.add(currentCalendarYear);
                
                List<String> yearsToGenerate = new ArrayList<>(yearsSet);
                yearsToGenerate.add("All Time");

                GenerativeModelFutures futures = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
                SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                
                for (String year : yearsToGenerate) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        textView.setText("Generating story for " + year + "...");
                    });
                    
                    List<Flight> filteredFlights;
                    if (year.equals("All Time")) {
                        filteredFlights = allFlights;
                    } else {
                        filteredFlights = new ArrayList<>();
                        for (Flight f : allFlights) {
                            if (f.date != null && f.date.startsWith(year)) {
                                filteredFlights.add(f);
                            }
                        }
                    }
                    
                    if (filteredFlights.isEmpty()) continue;
                    
                    // Sort by date so flight directions are in chronological order
                    filteredFlights.sort((a, b) -> {
                        if (a.date == null) return -1;
                        if (b.date == null) return 1;
                        return a.date.compareTo(b.date);
                    });

                    int flights = filteredFlights.size();
                    double km = 0;
                    Set<String> countries = new HashSet<>();
                    List<String> trips = new ArrayList<>();
                    for (Flight f : filteredFlights) {
                        km += f.distance;
                        String originName = f.origin;
                        String destName = f.destination;
                        Airport o = db.airportDao().getByIata(f.origin);
                        if (o != null) {
                            countries.add(o.country);
                            if (o.city != null && !o.city.isEmpty()) originName = o.city;
                        }
                        Airport d = db.airportDao().getByIata(f.destination);
                        if (d != null) {
                            countries.add(d.country);
                            if (d.city != null && !d.city.isEmpty()) destName = d.city;
                        }
                        trips.add(originName + " → " + destName);
                    }

                    String countryList = android.text.TextUtils.join(", ", countries);
                    String tripList = android.text.TextUtils.join("; ", trips);

                    String tone = settingsPrefs.getString("ai_summary_tone", "analytical");
                    String toneInstruction;
                    if ("narrative".equals(tone)) {
                        toneInstruction = "Write in a warm, narrative, and conversational travel log style. Flowing prose only, no bullet lists. Max 2 short paragraphs.";
                    } else {
                        toneInstruction = "Write in an analytical, concise, and structured style. Use a bulleted list for key highlights and statistics.";
                    }

                    String unitPrefVal = settingsPrefs.getString("preferred_unit", "km");
                    double distanceVal = km;
                    String unitName = "km";
                    if ("mi".equals(unitPrefVal)) {
                        distanceVal = km * 0.6213711922;
                        unitName = "miles";
                    }

                    String prompt = "Write a short travel summary for " + year + ", addressing the reader as 'you'. " +
                        toneInstruction + " " +
                        "Stick to the facts below — do not invent destinations or activities. " +
                        "Stats: " + flights + " flights, " + (int)distanceVal + " " + unitName + " total. " +
                        "Countries: " + countryList + ". " +
                        "Flights in order: " + tripList + ". " +
                        "Group legs into trips where logical. Bold place names and key numbers. No emojis.";

                    GenerateContentRequest.Builder requestBuilder = new GenerateContentRequest.Builder(new TextPart(prompt));
                    requestBuilder.setTemperature(0.7f);
                    requestBuilder.setMaxOutputTokens(256);
                    GenerateContentRequest request = requestBuilder.build();
                        
                    try {
                        ListenableFuture<GenerateContentResponse> future = futures.generateContent(request);
                        GenerateContentResponse result = future.get(); // Blocking wait for sequential generation
                        settingsPrefs.edit().putString("saved_story_" + year, result.getCandidates().get(0).getText()).apply();
                    } catch (Exception e) {
                        // Fallback on error: remove saved story to rely on organic engine
                        settingsPrefs.edit().remove("saved_story_" + year).apply();
                        int currentVersion = settingsPrefs.getInt("story_regen_version_" + year, 0);
                        settingsPrefs.edit().putInt("story_regen_version_" + year, currentVersion + 1).apply();
                    }
                }
                
                int globalVersion = settingsPrefs.getInt("story_regen_version", 0);
                settingsPrefs.edit().putInt("story_regen_version", globalVersion + 1).apply();

                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    cardSettings.setVisibility(View.GONE);
                    settingsScrim.setVisibility(View.GONE);
                    Snackbar.make(mapView, "Stories regenerated successfully", Snackbar.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    Snackbar.make(mapView, "AI Generation encountered an error: " + errorMsg, Snackbar.LENGTH_LONG).show();
                });
            }
        });
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
                mapView.getOverlays().clear();

                // Resolve theme colorPrimary on the main thread
                TypedValue tv = new TypedValue();
                requireContext().getTheme().resolveAttribute(
                        androidx.appcompat.R.attr.colorPrimary, tv, true);
                int primaryColor = tv.data;
                // Apply 80% alpha for a nice semi-transparent look
                int routeColor = (0xCC000000 & 0xFF000000) | (primaryColor & 0x00FFFFFF);

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
                    line.setPoints(points);
                    line.setColor(routeColor);
                    line.setWidth(2.5f);
                    line.setTitle(r.fromCode + " → " + r.toCode);
                    mapView.getOverlays().add(line);
                }

                // Draw M3 circle markers on top (deduplicated by airport)
                Set<String> drawnAirports = new HashSet<>();
                for (RouteData r : finalRouteDataMap.values()) {
                    drawMarker(r.from, r.fromCode, drawnAirports);
                    drawMarker(r.to,   r.toCode,   drawnAirports);
                }

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

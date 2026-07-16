package com.example.flightstats;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText inputName;
    private TextInputEditText inputHometown;
    private TextInputLayout inputHometownLayout;
    private MaterialButtonToggleGroup toggleMapStyle;
    private Slider sliderZoom;
    private MaterialSwitch switchCurved;
    private MaterialButtonToggleGroup toggleThemeMode;
    private MaterialButtonToggleGroup toggleUnit;
    private MaterialSwitch switchAi;
    private MaterialButtonToggleGroup toggleAiTone;
    private MaterialButtonToggleGroup toggleAiModel;
    private MaterialButtonToggleGroup toggleShapeFamily;
    private Slider sliderShapeRadius;
    private TextView txtShapeRadius;
    private MaterialButton btnRegenerate;

    private SharedPreferences prefs;
    private boolean isUpdatingUi = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Bind Toolbar
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // Bind Views
        inputName = findViewById(R.id.settings_input_name);
        inputHometown = findViewById(R.id.settings_input_hometown);
        inputHometownLayout = findViewById(R.id.settings_input_hometown_layout);
        toggleMapStyle = findViewById(R.id.settings_toggle_map_style);
        sliderZoom = findViewById(R.id.settings_slider_zoom);
        switchCurved = findViewById(R.id.settings_switch_curved);
        toggleThemeMode = findViewById(R.id.settings_toggle_theme_mode);
        toggleUnit = findViewById(R.id.settings_toggle_unit);
        switchAi = findViewById(R.id.settings_switch_ai);
        toggleAiTone = findViewById(R.id.settings_toggle_ai_tone);
        toggleAiModel = findViewById(R.id.settings_toggle_ai_model);
        toggleShapeFamily = findViewById(R.id.settings_toggle_shape_family);
        sliderShapeRadius = findViewById(R.id.settings_slider_shape_radius);
        txtShapeRadius = findViewById(R.id.settings_txt_shape_radius);
        btnRegenerate = findViewById(R.id.settings_btn_regenerate_stories);

        // Load values and configure listeners
        loadPreferencesToUi();
        setupListeners();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadPreferencesToUi() {
        isUpdatingUi = true;

        // Profile
        String name = prefs.getString("user_name", "User").trim();
        inputName.setText(name);

        String hometown = prefs.getString("hometown", "AMS").trim().toUpperCase();
        inputHometown.setText(hometown);

        // Map settings
        String mapStyle = prefs.getString("map_style", "light");
        if ("dark".equals(mapStyle)) {
            toggleMapStyle.check(R.id.settings_map_dark);
        } else if ("satellite".equals(mapStyle) || "sat".equals(mapStyle)) {
            toggleMapStyle.check(R.id.settings_map_sat);
        } else {
            toggleMapStyle.check(R.id.settings_map_light);
        }

        float zoom = prefs.getFloat("default_zoom", 4.5f);
        sliderZoom.setValue(Math.max(4.0f, Math.min(zoom, 10.0f)));

        boolean curved = prefs.getBoolean("map_route_curved", true);
        switchCurved.setChecked(curved);

        // Theme and Unit
        int themeMode = prefs.getInt("theme_mode", 2); // 0 = Light, 1 = Dark, 2 = System
        if (themeMode == 0) {
            toggleThemeMode.check(R.id.settings_theme_light);
        } else if (themeMode == 1) {
            toggleThemeMode.check(R.id.settings_theme_dark);
        } else {
            toggleThemeMode.check(R.id.settings_theme_system);
        }

        String unit = prefs.getString("preferred_unit", "km");
        if ("mi".equals(unit)) {
            toggleUnit.check(R.id.settings_unit_mi);
        } else {
            toggleUnit.check(R.id.settings_unit_km);
        }

        // Corner Shape Family
        String shapeFamily = prefs.getString("shape_family", "rounded");
        if ("cut".equals(shapeFamily)) {
            toggleShapeFamily.check(R.id.settings_shape_cut);
        } else {
            toggleShapeFamily.check(R.id.settings_shape_rounded);
        }

        // Corner Radius Slider
        float shapeRadius = prefs.getFloat("shape_radius", 16f);
        sliderShapeRadius.setValue(shapeRadius);
        txtShapeRadius.setText(Math.round(shapeRadius) + " dp");

        // AI Settings
        boolean aiEnabled = prefs.getBoolean("enable_ai_overviews", true);
        switchAi.setChecked(aiEnabled);

        String tone = prefs.getString("ai_summary_tone", "analytical");
        if ("friendly".equals(tone)) {
            toggleAiTone.check(R.id.settings_tone_friendly);
        } else if ("whimsical".equals(tone)) {
            toggleAiTone.check(R.id.settings_tone_whimsical);
        } else {
            toggleAiTone.check(R.id.settings_tone_analytical);
        }

        String model = prefs.getString("ai_model_pref", "gemma");
        if ("gemini".equals(model)) {
            toggleAiModel.check(R.id.settings_model_gemini);
        } else {
            toggleAiModel.check(R.id.settings_model_gemma);
        }

        LayoutShapeHelper.applyToView(findViewById(android.R.id.content));
        isUpdatingUi = false;
    }

    private void setupListeners() {
        // Name Change
        inputName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                String name = s.toString().trim();
                if (name.isEmpty()) name = "User";
                prefs.edit().putString("user_name", name).apply();
            }
        });

        // Hometown Change
        inputHometown.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isUpdatingUi) return;
                String iata = s.toString().trim().toUpperCase();
                if (iata.length() == 3) {
                    inputHometownLayout.setError(null);
                    prefs.edit().putString("hometown", iata).apply();
                } else if (iata.length() > 0) {
                    inputHometownLayout.setError("Must be a 3-letter IATA code");
                }
            }
        });

        // Map Style Toggle
        toggleMapStyle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            String style = "light";
            if (checkedId == R.id.settings_map_dark) style = "dark";
            if (checkedId == R.id.settings_map_sat) style = "satellite";
            prefs.edit().putString("map_style", style).apply();
        });

        // Map Zoom Slider
        sliderZoom.addOnChangeListener((slider, value, fromUser) -> {
            if (isUpdatingUi || !fromUser) return;
            prefs.edit().putFloat("default_zoom", value).apply();
        });

        // Curved Routes Switch
        switchCurved.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUi) return;
            prefs.edit().putBoolean("map_route_curved", isChecked).apply();
        });

        // Theme Mode Toggle (Light/Dark/System)
        toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            int mode = 2; // System
            if (checkedId == R.id.settings_theme_light) {
                mode = 0;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.settings_theme_dark) {
                mode = 1;
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            prefs.edit().putInt("theme_mode", mode).apply();
        });

        // Preferred Unit Toggle (km/mi)
        toggleUnit.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            String unit = (checkedId == R.id.settings_unit_mi) ? "mi" : "km";
            prefs.edit().putString("preferred_unit", unit).apply();
        });

        // Corner Shape Family Toggle
        toggleShapeFamily.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            String family = (checkedId == R.id.settings_shape_cut) ? "cut" : "rounded";
            prefs.edit().putString("shape_family", family).apply();
            LayoutShapeHelper.applyToView(findViewById(android.R.id.content));
        });

        // Corner Radius Slider Listener
        sliderShapeRadius.addOnChangeListener((slider, value, fromUser) -> {
            if (isUpdatingUi) return;
            prefs.edit().putFloat("shape_radius", value).apply();
            txtShapeRadius.setText(Math.round(value) + " dp");
            LayoutShapeHelper.applyToView(findViewById(android.R.id.content));
        });

        // AI Enable Switch
        switchAi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingUi) return;
            prefs.edit().putBoolean("enable_ai_overviews", isChecked).apply();
        });

        // AI Narrative Tone
        toggleAiTone.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            String tone = "analytical";
            if (checkedId == R.id.settings_tone_friendly) tone = "friendly";
            if (checkedId == R.id.settings_tone_whimsical) tone = "whimsical";
            prefs.edit().putString("ai_summary_tone", tone).apply();
        });

        // AI Model Preference
        toggleAiModel.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isUpdatingUi || !isChecked) return;
            String model = (checkedId == R.id.settings_model_gemini) ? "gemini" : "gemma";
            prefs.edit().putString("ai_model_pref", model).apply();
        });

        // Regenerate Button Click
        btnRegenerate.setOnClickListener(v -> regenerateAllStoriesWithAI());
    }

    private void regenerateAllStoriesWithAI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        layout.addView(progressBar);

        final TextView textView = new TextView(this);
        textView.setText("Initializing on-device AI Core...");
        textView.setTextSize(14f);
        textView.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        textView.setPadding((int) (16 * getResources().getDisplayMetrics().density), 0, 0, 0);
        layout.addView(textView);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Gemini Nano On-Device AI")
            .setView(layout)
            .setCancelable(false)
            .create();
        dialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(this);
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

                // Check model preference
                String modelPref = prefs.getString("ai_model_pref", "gemma");
                GenerativeModelFutures currentClient;
                boolean isUsingPreview;

                if ("gemini".equals(modelPref)) {
                    currentClient = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
                    isUsingPreview = false;
                } else {
                    com.google.mlkit.genai.prompt.ModelConfig.Builder modelConfigBuilder = new com.google.mlkit.genai.prompt.ModelConfig.Builder();
                    modelConfigBuilder.setReleaseStage(com.google.mlkit.genai.prompt.ModelReleaseStage.PREVIEW);
                    modelConfigBuilder.setPreference(com.google.mlkit.genai.prompt.ModelPreference.FAST);
                    com.google.mlkit.genai.prompt.ModelConfig modelConfig = modelConfigBuilder.build();

                    com.google.mlkit.genai.prompt.GenerationConfig.Builder genConfigBuilder = new com.google.mlkit.genai.prompt.GenerationConfig.Builder();
                    genConfigBuilder.setModelConfig(modelConfig);
                    com.google.mlkit.genai.prompt.GenerationConfig genConfig = genConfigBuilder.build();

                    currentClient = GenerativeModelFutures.from(Generation.INSTANCE.getClient(genConfig));
                    isUsingPreview = true;
                }

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

                    String tone = prefs.getString("ai_summary_tone", "analytical");
                    String toneInstruction;
                    if ("friendly".equals(tone)) {
                        toneInstruction = "Write in a warm, narrative, friendly, and conversational travel log style. Flowing prose only, no bullet lists. Max 2 short paragraphs.";
                    } else if ("whimsical".equals(tone)) {
                        toneInstruction = "Write in a whimsical, playful, storytelling, and highly creative style. Max 2 short paragraphs.";
                    } else {
                        toneInstruction = "Write in an analytical, concise, and structured style. Use a bulleted list for key highlights and statistics.";
                    }

                    String unitPrefVal = prefs.getString("preferred_unit", "km");
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
                        ListenableFuture<GenerateContentResponse> future = currentClient.generateContent(request);
                        GenerateContentResponse result = future.get();
                        prefs.edit().putString("saved_story_" + year, result.getCandidates().get(0).getText()).apply();
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : "";
                        if (e.getCause() != null && e.getCause().getMessage() != null) {
                            msg += " " + e.getCause().getMessage();
                        }
                        if (isUsingPreview && (msg.contains("606") || msg.contains("FEATURE_NOT_FOUND"))) {
                            isUsingPreview = false;
                            try {
                                currentClient = GenerativeModelFutures.from(Generation.INSTANCE.getClient());
                                ListenableFuture<GenerateContentResponse> stableFuture = currentClient.generateContent(request);
                                GenerateContentResponse result = stableFuture.get();
                                prefs.edit().putString("saved_story_" + year, result.getCandidates().get(0).getText()).apply();
                            } catch (Exception stableE) {
                                prefs.edit().remove("saved_story_" + year).apply();
                                int currentVersion = prefs.getInt("story_regen_version_" + year, 0);
                                prefs.edit().putInt("story_regen_version_" + year, currentVersion + 1).apply();
                            }
                        } else {
                            prefs.edit().remove("saved_story_" + year).apply();
                            int currentVersion = prefs.getInt("story_regen_version_" + year, 0);
                            prefs.edit().putInt("story_regen_version_" + year, currentVersion + 1).apply();
                        }
                    }
                }

                int globalVersion = prefs.getInt("story_regen_version", 0);
                prefs.edit().putInt("story_regen_version", globalVersion + 1).apply();

                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "Stories regenerated successfully", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                final String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                new Handler(Looper.getMainLooper()).post(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "AI Generation encountered an error: " + errorMsg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}

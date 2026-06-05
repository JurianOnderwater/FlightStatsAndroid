package com.example.flightstats;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.view.Gravity;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.Utils;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.Collections;
import android.widget.HorizontalScrollView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.carousel.CarouselLayoutManager;
import com.google.android.material.carousel.CarouselSnapHelper;
import com.google.android.material.carousel.MaskableFrameLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.common.util.concurrent.ListenableFuture;

public class StatsFragment extends Fragment {

    private static final String PREFS_CARDS = "StatsCards";
    private static final String[] CARD_IDS   = {"overview", "distance", "longest", "top_airports", "charts", "footprint", "time", "top_routes"};
    private static final String[] CARD_LABELS = {"Overview", "Distance", "Longest Flight", "Top Airports", "Flight Patterns", "Global Footprint", "Time Aloft", "Top Routes"};
    private static final Set<String> DEFAULT_ON = new HashSet<>(Arrays.asList("overview", "distance", "longest", "top_airports", "charts", "footprint"));

    private View root;
    private BarChart barChart;
    private EdgeToEdgePieView pieChart;
    private MaterialButtonToggleGroup toggleGroup;

    // Story Card views
    private View cardStoryStats;
    private TextView storyTitleStats;
    private TextView storyTextStats;
    private View viewFadeOverlay;
    private com.google.android.material.button.MaterialButton btnExpandStory;
    private com.google.android.material.button.MaterialButton btnRegenerateStoryIndividual;
    private androidx.core.widget.NestedScrollView scrollStatsContent;
    private String lastSelectedYear = null;
    private boolean isStoryExpanded = false;

    // Carousel views and lists
    private RecyclerView statsCarousel;
    private CarouselAdapter carouselAdapter;
    private final List<String> visibleCarouselCardIds = new ArrayList<>();
    private final CarouselData carouselData = new CarouselData();

    private android.text.Spanned parseMarkdown(String markdown) {
        if (markdown == null) return new android.text.SpannableString("");
        String escaped = markdown
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        String boldParsed = escaped.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        String italicParsed = boldParsed.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
        String formatted = italicParsed.replace("\n", "<br/>");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return android.text.Html.fromHtml(formatted, android.text.Html.FROM_HTML_MODE_LEGACY);
        } else {
            return android.text.Html.fromHtml(formatted);
        }
    }

    // Precomputed chart data (set on background thread, read on main)
    private int[] dataByMonth  = new int[12];
    private int[] dataByDay    = new int[7];
    private int[] dataByYear   = new int[0];
    private String[] yearLabels = new String[0];

    private List<String> currentYearsInChips = new ArrayList<>();
    private String selectedYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedYear", selectedYear);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        isStoryExpanded = false;
        if (savedInstanceState != null) {
            selectedYear = savedInstanceState.getString("selectedYear", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        }
        root = inflater.inflate(R.layout.fragment_stats, container, false);

        barChart    = root.findViewById(R.id.bar_chart);
        pieChart    = root.findViewById(R.id.pie_chart);
        toggleGroup = root.findViewById(R.id.toggle_chart_type);

        // Bind Story Card views
        cardStoryStats      = root.findViewById(R.id.card_story_stats);
        storyTitleStats     = root.findViewById(R.id.story_title_stats);
        storyTextStats      = root.findViewById(R.id.story_text_stats);
        viewFadeOverlay     = root.findViewById(R.id.view_fade_overlay);
        btnExpandStory      = root.findViewById(R.id.btn_expand_story);
        btnRegenerateStoryIndividual = root.findViewById(R.id.btn_regenerate_story_individual);
        scrollStatsContent  = root.findViewById(R.id.scroll_stats_content);

        statsCarousel = root.findViewById(R.id.stats_carousel);
        if (statsCarousel != null) {
            statsCarousel.setLayoutManager(new CarouselLayoutManager());
            new CarouselSnapHelper().attachToRecyclerView(statsCarousel);
            carouselAdapter = new CarouselAdapter(visibleCarouselCardIds, carouselData);
            statsCarousel.setAdapter(carouselAdapter);
        }

        if (btnExpandStory != null) {
            btnExpandStory.setOnClickListener(v -> {
                isStoryExpanded = !isStoryExpanded;
                updateStoryExpandedState();
                if (!isStoryExpanded && scrollStatsContent != null) {
                    cardStoryStats.post(() -> scrollStatsContent.smoothScrollTo(0, cardStoryStats.getTop()));
                }
            });
        }

        // Set up individual year regeneration click listener
        btnRegenerateStoryIndividual.setOnClickListener(v -> {
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

            regenerateStoryWithAI(dialog, textView);
        });

        setupChart();

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_by_month) showChartMonth();
            else if (checkedId == R.id.btn_by_day)  showChartDay();
            else if (checkedId == R.id.btn_by_year) showChartYear();
        });
        toggleGroup.check(R.id.btn_by_month);

        applyCardVisibility();
        loadStats();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        applyCardVisibility();
        loadStats();
    }

    // ── Card visibility ───────────────────────────────────────────────────────

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS_CARDS, Context.MODE_PRIVATE);
    }

    private boolean isCardEnabled(String id) {
        return prefs().getBoolean(id, DEFAULT_ON.contains(id));
    }

    private int cardViewId(String id) {
        switch (id) {
            case "overview":     return R.id.card_overview;
            case "charts":       return R.id.card_charts;
            case "footprint":    return R.id.card_footprint;
            default:             return 0;
        }
    }

    private void applyCardVisibility() {
        if (root == null) return;
        
        // 1. Handle non-carousel cards
        String[] nonCarouselIds = {"overview", "charts", "footprint"};
        for (String id : nonCarouselIds) {
            int viewId = cardViewId(id);
            if (viewId != 0) {
                View card = root.findViewById(viewId);
                if (card != null) {
                    card.setVisibility(isCardEnabled(id) ? View.VISIBLE : View.GONE);
                }
            }
        }

        // 2. Handle carousel cards
        visibleCarouselCardIds.clear();
        if (isCardEnabled("distance")) visibleCarouselCardIds.add("distance");
        if (isCardEnabled("longest")) visibleCarouselCardIds.add("longest");
        if (isCardEnabled("time")) visibleCarouselCardIds.add("time");
        if (isCardEnabled("top_airports")) visibleCarouselCardIds.add("top_airports");
        if (isCardEnabled("top_routes")) visibleCarouselCardIds.add("top_routes");

        if (carouselAdapter != null) {
            carouselAdapter.notifyDataSetChanged();
        }

        // 3. Hide RecyclerView if empty
        if (statsCarousel != null) {
            statsCarousel.setVisibility(visibleCarouselCardIds.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void showEditDialog() {
        boolean[] checked = new boolean[CARD_IDS.length];
        for (int i = 0; i < CARD_IDS.length; i++) checked[i] = isCardEnabled(CARD_IDS[i]);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        for (int i = 0; i < CARD_IDS.length; i++) {
            MaterialCheckBox cb = new MaterialCheckBox(requireContext());
            cb.setText(CARD_LABELS[i]);
            cb.setChecked(checked[i]);
            final int idx = i;
            cb.setOnCheckedChangeListener((btn, isChecked) -> checked[idx] = isChecked);
            layout.addView(cb);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Dashboard Cards")
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    SharedPreferences.Editor ed = prefs().edit();
                    for (int i = 0; i < CARD_IDS.length; i++) ed.putBoolean(CARD_IDS[i], checked[i]);
                    ed.apply();
                    applyCardVisibility();
                    loadStats();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Chart setup ───────────────────────────────────────────────────────────

    private void setupChart() {
        int textColor = resolveColor(com.google.android.material.R.attr.colorOnSurface);

        // Use our custom renderer for pill-shaped bars
        float radiusPx = Utils.convertDpToPixel(20f);
        barChart.setRenderer(new RoundedBarChartRenderer(barChart, barChart.getAnimator(),
                barChart.getViewPortHandler(), radiusPx));

        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setTouchEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setFitBars(true);
        barChart.setExtraBottomOffset(4f);
        barChart.setExtraTopOffset(12f);
        barChart.setBackgroundColor(Color.TRANSPARENT);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textColor);
        xAxis.setTextSize(10f);
        xAxis.setGranularity(1f);

        YAxis left = barChart.getAxisLeft();
        left.setDrawGridLines(true);
        left.setGridColor(Color.argb(25, 127, 127, 127));
        left.setDrawAxisLine(false);
        left.setTextColor(textColor);
        left.setAxisMinimum(0f);
        left.setGranularity(1f);
        left.setSpaceTop(20f);

        barChart.getAxisRight().setEnabled(false);
    }

    private void showChartMonth() {
        String[] labels = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        renderChart(dataByMonth, labels);
    }

    private void showChartDay() {
        String[] labels = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        renderChart(dataByDay, labels);
    }

    private void showChartYear() {
        // For years, skip every other label to avoid crowding
        final int[] vals = dataByYear;
        final String[] lbls = yearLabels;
        if (vals == null || vals.length == 0) return;

        int primaryColor   = resolveColor(androidx.appcompat.R.attr.colorPrimary);
        int secondaryColor = resolveColor(com.google.android.material.R.attr.colorSecondaryContainer);
        int onSurface      = resolveColor(com.google.android.material.R.attr.colorOnSurface);

        int max = 0;
        for (int v : vals) if (v > max) max = v;

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors  = new ArrayList<>();
        for (int i = 0; i < vals.length; i++) {
            entries.add(new BarEntry(i, vals[i]));
            colors.add(vals[i] == max && max > 0 ? primaryColor : secondaryColor);
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(onSurface);
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override public String getBarLabel(BarEntry e) {
                return (int)e.getY() == 0 ? "" : String.valueOf((int)e.getY());
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.78f);

        // Show every other year label to avoid crowding
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int idx = Math.round(value);
                if (idx < 0 || idx >= lbls.length) return "";
                return idx % 2 == 0 ? lbls[idx] : "";
            }
        });
        barChart.getXAxis().setLabelCount(vals.length, true);
        barChart.setData(data);
        barChart.animateY(500, Easing.EaseOutCubic);
        barChart.invalidate();
    }

    private void renderChart(int[] values, String[] labels) {
        if (values == null || values.length == 0 || barChart == null) return;

        int primaryColor   = resolveColor(androidx.appcompat.R.attr.colorPrimary);
        int secondaryColor = resolveColor(com.google.android.material.R.attr.colorSecondaryContainer);
        int onPrimary      = resolveColor(com.google.android.material.R.attr.colorOnSurface);

        // Find max value to highlight the peak bar(s)
        int max = 0;
        for (int v : values) if (v > max) max = v;

        List<BarEntry> entries = new ArrayList<>();
        List<Integer> colors  = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            entries.add(new BarEntry(i, values[i]));
            // Peak bar gets primary color, others get secondary container
            colors.add(values[i] == max && max > 0 ? primaryColor : secondaryColor);
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextColor(onPrimary);
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                int v = (int) barEntry.getY();
                return v == 0 ? "" : String.valueOf(v);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.78f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.getXAxis().setLabelCount(labels.length);
        barChart.setData(data);
        barChart.animateY(500, Easing.EaseOutCubic);
        barChart.invalidate();
    }

    // ── Stats computation ─────────────────────────────────────────────────────

    private void loadStats() {
        if (!isAdded()) return;
        if (lastSelectedYear == null || !lastSelectedYear.equals(selectedYear)) {
            lastSelectedYear = selectedYear;
            isStoryExpanded = false;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            List<Flight> dbFlights = db.flightDao().getAllFlights();
            String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            List<Flight> allFlights = new ArrayList<>();
            for (Flight f : dbFlights) {
                if (f.date != null && f.date.compareTo(todayStr) < 0) {
                    allFlights.add(f);
                }
            }

            // Extract unique flight years dynamically from the full flight list
            Set<String> yearsSet = new HashSet<>();
            for (Flight f : allFlights) {
                if (f.date != null && f.date.length() >= 4) {
                    yearsSet.add(f.date.substring(0, 4));
                }
            }
            // Always ensure the current calendar year is in the list
            String currentCalendarYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
            yearsSet.add(currentCalendarYear);

            // Sort years in descending order
            List<String> sortedYears = new ArrayList<>(yearsSet);
            Collections.sort(sortedYears, Collections.reverseOrder());

            // Prepare list with "All Time" at the top
            List<String> displayYears = new ArrayList<>();
            displayYears.add("All Time");
            displayYears.addAll(sortedYears);

            // Filter flights list according to the selected year
            List<Flight> filteredFlights;
            if (selectedYear == null || selectedYear.equals("All Time")) {
                filteredFlights = allFlights;
            } else {
                filteredFlights = new ArrayList<>();
                for (Flight f : allFlights) {
                    if (f.date != null && f.date.startsWith(selectedYear)) {
                        filteredFlights.add(f);
                    }
                }
            }

            Set<String> airportSet = new HashSet<>(), routeSet = new HashSet<>(), countrySet = new HashSet<>();
            Map<String, Integer> airportCounts = new HashMap<>(), routeCounts = new HashMap<>();
            Map<String, Integer> countryCounts = new HashMap<>();
            double totalKm = 0;
            Flight longest = null;

            int springFlights = 0; // March, April, May
            int summerFlights = 0; // June, July, August
            int autumnFlights = 0; // September, October, November
            int winterFlights = 0; // December, January, February

            // Chart accumulators
            int[] byMonth = new int[12];
            int[] byDay   = new int[7];
            TreeMap<Integer, Integer> byYearMap = new TreeMap<>();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            for (Flight f : filteredFlights) {
                totalKm += f.distance;
                airportSet.add(f.origin);
                airportSet.add(f.destination);
                airportCounts.merge(f.origin, 1, Integer::sum);
                airportCounts.merge(f.destination, 1, Integer::sum);

                String[] pair = {f.origin, f.destination};
                Arrays.sort(pair);
                String routeKey = pair[0] + "-" + pair[1];
                routeSet.add(routeKey);
                routeCounts.merge(routeKey, 1, Integer::sum);

                if (longest == null || f.distance > longest.distance) longest = f;

                Airport o = db.airportDao().getByIata(f.origin);
                Airport d = db.airportDao().getByIata(f.destination);
                if (o != null) {
                    countrySet.add(o.country);
                    countryCounts.merge(o.country, 1, Integer::sum);
                }
                if (d != null) {
                    countrySet.add(d.country);
                    countryCounts.merge(d.country, 1, Integer::sum);
                }

                // Chart data
                if (f.date != null && f.date.length() >= 7) {
                    try {
                        int month = Integer.parseInt(f.date.substring(5, 7)) - 1;
                        if (month >= 0 && month < 12) byMonth[month]++;
                    } catch (NumberFormatException ignored) {}
                }
                if (f.date != null && f.date.length() >= 10) {
                    try {
                        Date d2 = sdf.parse(f.date);
                        if (d2 != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(d2);
                            int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun, 2=Mon...7=Sat
                            int idx = (dow + 5) % 7; // 0=Mon...6=Sun
                            byDay[idx]++;
                        }
                    } catch (Exception ignored) {}
                }
                if (f.date != null && f.date.length() >= 4) {
                    try {
                        int year = Integer.parseInt(f.date.substring(0, 4));
                        byYearMap.merge(year, 1, Integer::sum);
                    } catch (NumberFormatException ignored) {}
                }

                // Seasons breakdown
                if (f.date != null && f.date.length() >= 7) {
                    try {
                        int month = Integer.parseInt(f.date.substring(5, 7)) - 1;
                        if (month == Calendar.MARCH || month == Calendar.APRIL || month == Calendar.MAY) {
                            springFlights++;
                        } else if (month == Calendar.JUNE || month == Calendar.JULY || month == Calendar.AUGUST) {
                            summerFlights++;
                        } else if (month == Calendar.SEPTEMBER || month == Calendar.OCTOBER || month == Calendar.NOVEMBER) {
                            autumnFlights++;
                        } else {
                            winterFlights++;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Convert year map to arrays
            int[] byYearArr = new int[byYearMap.size()];
            String[] yearLbls = new String[byYearMap.size()];
            int yi = 0;
            for (Map.Entry<Integer, Integer> e : byYearMap.entrySet()) {
                yearLbls[yi] = String.valueOf(e.getKey());
                byYearArr[yi] = e.getValue();
                yi++;
            }

            // Top lists
            List<Map.Entry<String, Integer>> sortedAirports = new ArrayList<>(airportCounts.entrySet());
            sortedAirports.sort((a, b) -> b.getValue() - a.getValue());
            List<String> topAirports = new ArrayList<>();
            for (int i = 0; i < Math.min(5, sortedAirports.size()); i++) {
                Map.Entry<String, Integer> e = sortedAirports.get(i);
                Airport a = db.airportDao().getByIata(e.getKey());
                String city = a != null && a.city != null ? a.city : e.getKey();
                topAirports.add(e.getKey() + "  ·  " + city + "  (" + e.getValue() + ")");
            }

            // Pie Chart Data
            List<Map.Entry<String, Integer>> sortedCountries = new ArrayList<>(countryCounts.entrySet());
            sortedCountries.sort((a, b) -> b.getValue() - a.getValue());
            List<EdgeToEdgePieView.Slice> pieSlices = new ArrayList<>();
            int otherCount = 0;
            double pieTotal = 0;
            for (int count : countryCounts.values()) pieTotal += count;

            int[] palette = {
                    resolveColor(androidx.appcompat.R.attr.colorPrimary),
                    resolveColor(com.google.android.material.R.attr.colorTertiary),
                    resolveColor(com.google.android.material.R.attr.colorSecondary),
                    resolveColor(com.google.android.material.R.attr.colorPrimaryContainer),
                    resolveColor(com.google.android.material.R.attr.colorTertiaryContainer),
                    resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
            };

            for (int i = 0; i < sortedCountries.size(); i++) {
                if (i < 5) { // Top 5 countries
                    String code = sortedCountries.get(i).getKey();
                    float val = sortedCountries.get(i).getValue();
                    String pct = String.format(Locale.US, "%.1f%%", (val / pieTotal) * 100);
                    pieSlices.add(new EdgeToEdgePieView.Slice(
                            val, palette[i % palette.length], code, pct));
                } else {
                    otherCount += sortedCountries.get(i).getValue();
                }
            }
            if (otherCount > 0) {
                String pct = String.format(Locale.US, "%.1f%%", (otherCount / pieTotal) * 100);
                pieSlices.add(new EdgeToEdgePieView.Slice(otherCount, palette[5], "Other", pct));
            }

            List<Map.Entry<String, Integer>> sortedRoutes = new ArrayList<>(routeCounts.entrySet());
            sortedRoutes.sort((a, b) -> b.getValue() - a.getValue());
            List<String> topRoutes = new ArrayList<>();
            for (int i = 0; i < Math.min(5, sortedRoutes.size()); i++) {
                Map.Entry<String, Integer> e = sortedRoutes.get(i);
                String[] parts = e.getKey().split("-");
                topRoutes.add(parts[0] + "  →  " + (parts.length > 1 ? parts[1] : "?") + "  (" + e.getValue() + "×)");
            }

            final int finalFlights   = filteredFlights.size();
            final int finalCountries = countrySet.size();
            final int finalAirports  = airportSet.size();
            final int finalRoutes    = routeSet.size();
            final double km = totalKm;
            final Flight bestFlight = longest;
            final List<String> finalDisplayYears = displayYears;

            String topAirport = "N/A";
            int topAirportVal = 0;
            if (!sortedAirports.isEmpty()) {
                topAirport = sortedAirports.get(0).getKey();
                topAirportVal = sortedAirports.get(0).getValue();
            }

            String topRoute = "N/A";
            int topRouteVal = 0;
            if (!sortedRoutes.isEmpty()) {
                topRoute = sortedRoutes.get(0).getKey().replace("-", " ↔ ");
                topRouteVal = sortedRoutes.get(0).getValue();
            }
            SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            String savedStory = settingsPrefs.getString("saved_story_" + selectedYear, null);
            final boolean hasSavedStory = savedStory != null && !savedStory.isEmpty();
            final String storyTextContent = hasSavedStory ? savedStory : null;

            final boolean aiOverviewsEnabled = settingsPrefs.getBoolean("enable_ai_overviews", true);

            // Store chart arrays for toggle switching
            dataByMonth = byMonth;
            dataByDay   = byDay;
            dataByYear  = byYearArr;
            yearLabels  = yearLbls;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (!isAdded() || root == null) return;

                // Update dynamic year selection chips
                updateYearChips(finalDisplayYears);

                setText(R.id.stat_flights,   String.valueOf(finalFlights));
                setText(R.id.stat_countries, String.valueOf(finalCountries));
                setText(R.id.stat_airports,  String.valueOf(finalAirports));
                setText(R.id.stat_routes,    String.valueOf(finalRoutes));

                String preferredUnit = settingsPrefs.getString("preferred_unit", "km");

                // Update in-memory carousel data
                carouselData.km = km;
                carouselData.bestFlight = bestFlight;
                carouselData.topAirports = topAirports;
                carouselData.topRoutes = topRoutes;
                carouselData.preferredUnit = preferredUnit;

                // Notify adapter that carousel data has updated
                if (carouselAdapter != null) {
                    carouselAdapter.notifyDataSetChanged();
                }

                // Show or hide the Year toggle button on the patterns card based on selectedYear filter
                boolean isAllTime = selectedYear == null || selectedYear.equals("All Time");
                View btnByYear = root.findViewById(R.id.btn_by_year);
                if (btnByYear != null) {
                    if (isAllTime) {
                        btnByYear.setVisibility(View.VISIBLE);
                    } else {
                        btnByYear.setVisibility(View.GONE);
                        if (toggleGroup.getCheckedButtonId() == R.id.btn_by_year) {
                            toggleGroup.check(R.id.btn_by_month);
                        }
                    }
                }

                // Refresh chart with currently selected tab
                int checkedId = toggleGroup.getCheckedButtonId();
                if (checkedId == R.id.btn_by_day)       showChartDay();
                else if (checkedId == R.id.btn_by_year) showChartYear();
                else                                     showChartMonth();

                // Render Pie Chart
                if (pieChart != null) {
                    pieChart.setSlices(pieSlices);
                }

                // Show or hide collapsible story card dynamically
                if (aiOverviewsEnabled && finalFlights > 0 && cardStoryStats != null) {
                    cardStoryStats.setVisibility(View.VISIBLE);
                    storyTitleStats.setText(selectedYear.equals("All Time") ? "Travel Summary" : selectedYear + " Travel Summary");
                    
                    if (hasSavedStory) {
                        storyTextStats.setVisibility(View.VISIBLE);
                        storyTextStats.setText(parseMarkdown(storyTextContent));
                        
                        // Set up gradient overlay color based on the card background theme color
                        if (viewFadeOverlay != null) {
                            int surfaceColor = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh);
                            GradientDrawable gd = new GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    new int[] { Color.TRANSPARENT, surfaceColor }
                            );
                            viewFadeOverlay.setBackground(gd);
                        }

                        if (btnRegenerateStoryIndividual != null) {
                            btnRegenerateStoryIndividual.setText("Regenerate Summary");
                        }

                        // Apply the collapsed/expanded state
                        updateStoryExpandedState();
                        
                        if (btnExpandStory != null) {
                            btnExpandStory.setVisibility(View.VISIBLE);
                        }
                    } else {
                        storyTextStats.setVisibility(View.GONE);
                        if (viewFadeOverlay != null) {
                            viewFadeOverlay.setVisibility(View.GONE);
                        }
                        if (btnExpandStory != null) {
                            btnExpandStory.setVisibility(View.GONE);
                        }
                        if (btnRegenerateStoryIndividual != null) {
                            btnRegenerateStoryIndividual.setText("Generate AI Summary");
                            btnRegenerateStoryIndividual.setVisibility(View.VISIBLE);
                        }
                    }
                } else if (cardStoryStats != null) {
                    cardStoryStats.setVisibility(View.GONE);
                }
            });
        });
    }

    private void updateYearChips(List<String> displayYears) {
        ChipGroup chipGroup = root.findViewById(R.id.chip_group_years);
        if (chipGroup == null) return;

        // Check if the displayed chips match the new years list
        if (currentYearsInChips.equals(displayYears) && chipGroup.getChildCount() == displayYears.size()) {
            ensureSelectedChipChecked(chipGroup);
            return;
        }

        chipGroup.removeAllViews();
        currentYearsInChips = new ArrayList<>(displayYears);

        for (String year : displayYears) {
            Chip chip = new Chip(requireContext());
            chip.setText(year);
            chip.setCheckable(true);
            chip.setChecked(year.equals(selectedYear));

            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedYear = year;
                    centerChip(chip);
                    loadStats();
                }
            });

            chipGroup.addView(chip);

            if (year.equals(selectedYear)) {
                centerChip(chip);
            }
        }
    }

    private void ensureSelectedChipChecked(ChipGroup chipGroup) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                String chipText = chip.getText().toString();
                if (chipText.equals(selectedYear)) {
                    if (!chip.isChecked()) {
                        chip.setOnCheckedChangeListener(null);
                        chip.setChecked(true);
                        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                selectedYear = chipText;
                                centerChip(chip);
                                loadStats();
                            }
                        });
                    }
                    centerChip(chip);
                    break;
                }
            }
        }
    }

    private void centerChip(Chip chip) {
        HorizontalScrollView scrollView = root.findViewById(R.id.scroll_year_chips);
        if (scrollView == null || chip == null) return;

        scrollView.post(() -> {
            int chipLeft = chip.getLeft();
            int chipWidth = chip.getWidth();
            int scrollWidth = scrollView.getWidth();
            int scrollX = chipLeft - (scrollWidth - chipWidth) / 2;
            scrollView.smoothScrollTo(scrollX, 0);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setText(int viewId, String text) {
        TextView tv = root.findViewById(viewId);
        if (tv != null) tv.setText(text);
    }

    private void populateList(int containerId, List<String> items) {
        LinearLayout container = root.findViewById(containerId);
        if (container == null) return;
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int primaryColor = resolveColor(androidx.appcompat.R.attr.colorPrimary);
        // Detect which container to pick right text color
        boolean onSecondary = (containerId == R.id.list_top_routes);
        int textColor = onSecondary
                ? resolveColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
                : resolveColor(com.google.android.material.R.attr.colorOnSurface);

        for (int i = 0; i < items.size(); i++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int vPad = (int)(6 * density);
            row.setPadding(0, vPad, 0, vPad);

            // Rank badge — square shape (matches MaterialShapes.SQUARE: all corners ~30% rounded)
            TextView rank = new TextView(requireContext());
            rank.setText(String.valueOf(i + 1));
            rank.setTextSize(11);
            rank.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimary));
            rank.setTypeface(null, android.graphics.Typeface.BOLD);
            int badgeSize = (int)(24 * density);
            float cornerRadius = badgeSize * 0.30f; // ~30% = MaterialShapes.SQUARE rounding
            MaterialShapeDrawable squareBg = new MaterialShapeDrawable(
                    ShapeAppearanceModel.builder()
                            .setAllCornerSizes(cornerRadius)
                            .build());
            squareBg.setFillColor(android.content.res.ColorStateList.valueOf(primaryColor));
            rank.setBackground(squareBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            lp.setMarginEnd((int)(10 * density));
            rank.setLayoutParams(lp);
            rank.setGravity(android.view.Gravity.CENTER);
            rank.setPadding(0, 0, 0, 0);

            // Content
            TextView content = new TextView(requireContext());
            content.setText(items.get(i));
            content.setTextSize(14);
            content.setTextColor(textColor);

            row.addView(rank);
            row.addView(content);
            container.addView(row);
        }
    }

    private String formatKm(double km) {
        if (km >= 1_000_000) return String.format("%.2fM", km / 1_000_000);
        if (km >= 1_000)     return String.format(Locale.US, "%,.0f", km);
        return String.valueOf((int) km);
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private int resolveColor(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void updateStoryExpandedState() {
        if (storyTextStats == null || btnExpandStory == null || viewFadeOverlay == null) return;
        if (isStoryExpanded) {
            storyTextStats.setMaxLines(Integer.MAX_VALUE);
            viewFadeOverlay.setVisibility(View.GONE);
            btnExpandStory.setText("Show Less");
            if (btnRegenerateStoryIndividual != null) {
                btnRegenerateStoryIndividual.setVisibility(View.VISIBLE);
            }
        } else {
            storyTextStats.setMaxLines(3);
            viewFadeOverlay.setVisibility(View.VISIBLE);
            btnExpandStory.setText("Read Travel Summary");
            if (btnRegenerateStoryIndividual != null) {
                btnRegenerateStoryIndividual.setVisibility(View.GONE);
            }
        }
    }

    private void regenerateStoryWithAI(androidx.appcompat.app.AlertDialog dialog, TextView textView) {
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
                List<Flight> filteredFlights;
                if (selectedYear == null || selectedYear.equals("All Time")) {
                    filteredFlights = allFlights;
                } else {
                    filteredFlights = new ArrayList<>();
                    for (Flight f : allFlights) {
                        if (f.date != null && f.date.startsWith(selectedYear)) {
                            filteredFlights.add(f);
                        }
                    }
                }
                
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

                SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
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

                String prompt = "Write a short travel summary for " + selectedYear + ", addressing the reader as 'you'. " +
                    toneInstruction + " " +
                    "Stick to the facts below — do not invent destinations or activities. " +
                    "Stats: " + flights + " flights, " + (int)distanceVal + " " + unitName + " total. " +
                    "Countries: " + countryList + ". " +
                    "Flights in order: " + tripList + ". " +
                    "Group legs into trips where logical. Bold place names and key numbers. No emojis.";

                GenerativeModelFutures generativeModelFutures = GenerativeModelFutures.from(Generation.INSTANCE.getClient());

                GenerateContentRequest.Builder requestBuilder = new GenerateContentRequest.Builder(new TextPart(prompt));
                requestBuilder.setTemperature(0.7f);
                requestBuilder.setMaxOutputTokens(256);
                GenerateContentRequest request = requestBuilder.build();
                
                ListenableFuture<GenerateContentResponse> future = generativeModelFutures.generateContent(request);
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    textView.setText("Generating summary with Gemini Nano...");
                });
                
                com.google.common.util.concurrent.Futures.addCallback(future, new com.google.common.util.concurrent.FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        String generatedStory = result.getCandidates().get(0).getText();
                        SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                        settingsPrefs.edit().putString("saved_story_" + selectedYear, generatedStory).apply();
                        
                        new Handler(Looper.getMainLooper()).post(() -> {
                            dialog.dismiss();
                            loadStats();
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        fallbackToOrganic(dialog, t.getMessage() != null ? t.getMessage() : t.toString());
                    }
                }, Executors.newSingleThreadExecutor());

            } catch (Exception e) {
                fallbackToOrganic(dialog, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        });
    }

    private void fallbackToOrganic(androidx.appcompat.app.AlertDialog dialog, String errorMsg) {
        SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int currentVersion = settingsPrefs.getInt("story_regen_version_" + selectedYear, 0);
        settingsPrefs.edit().putInt("story_regen_version_" + selectedYear, currentVersion + 1).apply();
        settingsPrefs.edit().remove("saved_story_" + selectedYear).apply();
        
        new Handler(Looper.getMainLooper()).post(() -> {
            dialog.dismiss();
            if (getView() != null && errorMsg != null) {
                com.google.android.material.snackbar.Snackbar.make(getView(), "AI Generation error: " + errorMsg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
            }
            loadStats();
        });
    }

    private void populateViewHolderList(LinearLayout container, List<String> items, boolean onSecondary) {
        if (container == null) return;
        container.removeAllViews();
        float density = container.getContext().getResources().getDisplayMetrics().density;
        int primaryColor = resolveColor(androidx.appcompat.R.attr.colorPrimary);
        int textColor = resolveColor(com.google.android.material.R.attr.colorOnSurface);
        int surfaceVariantColor = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant);
        int outlineVariantColor = resolveColor(com.google.android.material.R.attr.colorOutlineVariant);

        for (int i = 0; i < Math.min(3, items.size()); i++) {
            com.google.android.material.card.MaterialCardView subcard = new com.google.android.material.card.MaterialCardView(container.getContext());
            subcard.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(surfaceVariantColor));
            subcard.setStrokeColor(android.content.res.ColorStateList.valueOf(outlineVariantColor));
            subcard.setStrokeWidth(Math.round(1 * density));
            subcard.setRadius(10 * density);
            subcard.setCardElevation(0f);
            
            LinearLayout.LayoutParams subcardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(32 * density)
            );
            subcardLp.setMargins(0, 0, 0, Math.round(4 * density));
            subcard.setLayoutParams(subcardLp);

            LinearLayout row = new LinearLayout(container.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(10 * density), 0, Math.round(10 * density), 0);

            TextView rank = new TextView(container.getContext());
            rank.setText(String.valueOf(i + 1));
            rank.setTextSize(10f);
            rank.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnPrimary));
            rank.setTypeface(null, android.graphics.Typeface.BOLD);
            int badgeSize = Math.round(20 * density);
            float cornerRadius = badgeSize * 0.30f;
            MaterialShapeDrawable squareBg = new MaterialShapeDrawable(
                    ShapeAppearanceModel.builder()
                            .setAllCornerSizes(cornerRadius)
                            .build());
            squareBg.setFillColor(android.content.res.ColorStateList.valueOf(primaryColor));
            rank.setBackground(squareBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
            lp.setMarginEnd(Math.round(8 * density));
            rank.setLayoutParams(lp);
            rank.setGravity(android.view.Gravity.CENTER);
            rank.setPadding(0, 0, 0, 0);

            TextView content = new TextView(container.getContext());
            content.setText(items.get(i));
            content.setTextSize(11f);
            content.setTextColor(textColor);
            content.setSingleLine(true);
            content.setEllipsize(android.text.TextUtils.TruncateAt.END);

            row.addView(rank);
            row.addView(content);
            subcard.addView(row);
            container.addView(subcard);
        }
    }

    private static class CarouselData {
        double km = 0;
        Flight bestFlight = null;
        List<String> topAirports = new ArrayList<>();
        List<String> topRoutes = new ArrayList<>();
        String preferredUnit = "km";
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.ViewHolder> {
        private final List<String> cardIds;
        private final CarouselData data;

        CarouselAdapter(List<String> cardIds, CarouselData data) {
            this.cardIds = cardIds;
            this.data = data;
        }

        @Override
        public int getItemViewType(int position) {
            String id = cardIds.get(position);
            switch (id) {
                case "distance": return 0;
                case "longest": return 1;
                case "time": return 2;
                case "top_airports": return 3;
                case "top_routes": return 4;
                default: return 0;
            }
        }

        private int getLayoutForViewType(int viewType) {
            switch (viewType) {
                case 0: return R.layout.item_carousel_distance;
                case 1: return R.layout.item_carousel_longest;
                case 2: return R.layout.item_carousel_time;
                case 3: return R.layout.item_carousel_airports;
                case 4: return R.layout.item_carousel_routes;
                default: return R.layout.item_carousel_distance;
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = getLayoutForViewType(viewType);
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            
            MaskableFrameLayout container = new MaskableFrameLayout(parent.getContext());
            
            float density = parent.getContext().getResources().getDisplayMetrics().density;
            int widthPx = Math.round(220 * density);
            int heightPx = Math.round(220 * density);
            int marginHorizontalPx = 0;
            
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(widthPx, heightPx);
            lp.setMargins(marginHorizontalPx, 0, marginHorizontalPx, 0);
            container.setLayoutParams(lp);

            // Set shape appearance model to rectangular so the card's own rounded corners and outlines are not clipped
            ShapeAppearanceModel shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(0f)
                .build();
            container.setShapeAppearanceModel(shapeAppearanceModel);

            // Resizes and centers card content within the mask bounds as card scrolls away (preserving corners and outlines)
            container.setOnMaskChangedListener(new com.google.android.material.carousel.OnMaskChangedListener() {
                @Override
                public void onMaskChanged(android.graphics.RectF maskRect) {
                    float containerWidth = container.getWidth();
                    if (containerWidth == 0) {
                        containerWidth = 220f * density;
                    }
                    float maskWidth = maskRect.width();
                    
                    // Maintain a consistent gap/padding of 8dp between items by making the visual card width slightly smaller than the mask
                    float gapPx = 8f * density;
                    float targetWidth = Math.max(0f, maskWidth - gapPx);

                    float scale = targetWidth / containerWidth;
                    if (scale < 0f) scale = 0f;
                    if (scale > 1f) scale = 1f;

                    float maskCenter = maskRect.centerX();
                    float containerCenter = containerWidth / 2f;
                    float translationX = maskCenter - containerCenter;

                    // Scale horizontally (squish) and keep vertical scale at 1f (same height)
                    view.setScaleX(scale);
                    view.setScaleY(1f);
                    view.setTranslationX(translationX);
                }
            });
            
            container.addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            
            return new ViewHolder(container, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            View card = holder.container.getChildAt(0);
            if (card == null) return;
            
            String preferredUnit = data.preferredUnit;
            double km = data.km;
            Flight bestFlight = data.bestFlight;
            
            switch (viewType) {
                case 0: {
                    TextView tvKm = card.findViewById(R.id.stat_km);
                    TextView tvMiles = card.findViewById(R.id.stat_miles);
                    TextView tvLabelMiles = card.findViewById(R.id.label_miles);
                    TextView tvCirc = card.findViewById(R.id.stat_circumnavigations);
                    TextView tvMoon = card.findViewById(R.id.stat_moon);
                    
                    if ("mi".equals(preferredUnit)) {
                        if (tvKm != null) tvKm.setText(formatKm(km * 0.621371) + " mi");
                        if (tvMiles != null) tvMiles.setText(formatKm(km) + " km");
                        if (tvLabelMiles != null) tvLabelMiles.setText("kilometers");
                    } else {
                        if (tvKm != null) tvKm.setText(formatKm(km) + " km");
                        if (tvMiles != null) tvMiles.setText(formatKm(km * 0.621371) + " mi");
                        if (tvLabelMiles != null) tvLabelMiles.setText("miles");
                    }
                    if (tvCirc != null) tvCirc.setText(String.format(Locale.US, "%.2f×", km / 40075.0));
                    if (tvMoon != null) tvMoon.setText(String.format(Locale.US, "%.1f%%", (km / 384400.0) * 100.0));
                    break;
                }
                case 1: {
                    TextView tvOrigin = card.findViewById(R.id.stat_longest_origin);
                    TextView tvDest = card.findViewById(R.id.stat_longest_dest);
                    TextView tvDetail = card.findViewById(R.id.stat_longest_detail);
                    
                    if (bestFlight != null) {
                        if (tvOrigin != null) tvOrigin.setText(bestFlight.origin);
                        if (tvDest != null) tvDest.setText(bestFlight.destination);
                        if (tvDetail != null) {
                            if ("mi".equals(preferredUnit)) {
                                tvDetail.setText(Math.round(bestFlight.distance * 0.621371) + " mi  ·  " + bestFlight.date);
                            } else {
                                tvDetail.setText(Math.round(bestFlight.distance) + " km  ·  " + bestFlight.date);
                            }
                        }
                    } else {
                        if (tvOrigin != null) tvOrigin.setText("—");
                        if (tvDest != null) tvDest.setText("—");
                        if (tvDetail != null) tvDetail.setText("");
                    }
                    break;
                }
                case 2: {
                    TextView tvHours = card.findViewById(R.id.stat_hours);
                    TextView tvDays = card.findViewById(R.id.stat_days);
                    
                    int hours = (int)(km / 800.0);
                    if (tvHours != null) tvHours.setText(hours + " hours");
                    if (tvDays != null) tvDays.setText(String.valueOf(hours / 24));
                    break;
                }
                case 3: {
                    LinearLayout listAirports = card.findViewById(R.id.list_top_airports);
                    populateViewHolderList(listAirports, data.topAirports, false);
                    break;
                }
                case 4: {
                    LinearLayout listRoutes = card.findViewById(R.id.list_top_routes);
                    populateViewHolderList(listRoutes, data.topRoutes, true);
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return cardIds.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaskableFrameLayout container;
            int viewType;
            ViewHolder(MaskableFrameLayout container, int viewType) {
                super(container);
                this.container = container;
                this.viewType = viewType;
            }
        }
    }
}

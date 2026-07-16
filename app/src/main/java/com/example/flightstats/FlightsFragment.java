package com.example.flightstats;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class FlightsFragment extends Fragment {

    private FlightAdapter adapter;
    private boolean showUpcoming = true; // Default to showing upcoming trips
    private boolean isInitialLoad = true; // Flag to set startup default based on presence of upcoming trips
    private TextView tvFlightsTitle;
    private TextView tvEmptyState;
    private MaterialButton btnTogglePastFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_flights, container, false);

        tvFlightsTitle = view.findViewById(R.id.tv_flights_title);
        btnTogglePastFuture = view.findViewById(R.id.btn_toggle_past_future);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);

        RecyclerView rv = view.findViewById(R.id.flights_recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FlightAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        updateHeaderUI();
        com.example.flightstats.data.AirportImporter.importIfNeeded(requireContext(), ignored -> {
            com.example.flightstats.data.CsvImporter.importIfNeeded(requireContext(), ignored2 -> {
                loadFlights();
            });
        });

        btnTogglePastFuture.setOnClickListener(v -> {
            showUpcoming = !showUpcoming;
            updateHeaderUI();
            loadFlights();
        });

        FloatingActionButton fabAddFlight = view.findViewById(R.id.fab_add_flight);
        fabAddFlight.setOnClickListener(v -> {
            AddFlightBottomSheet sheet = new AddFlightBottomSheet();
            sheet.setOnFlightAddedListener(this::loadFlights);
            sheet.show(getChildFragmentManager(), "add_flight");
        });

        View headerContainer = view.findViewById(R.id.flights_header_container);
        if (headerContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerContainer, (v, insets) -> {
                int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(v.getPaddingLeft(), (int) (16 * getResources().getDisplayMetrics().density) + topInset, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        if (rv != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rv, (v, insets) -> {
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                float density = getResources().getDisplayMetrics().density;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), (int) (80 * density) + navBarHeight);
                return insets;
            });
        }

        if (fabAddFlight != null) {
            ViewCompat.setOnApplyWindowInsetsListener(fabAddFlight, (v, insets) -> {
                int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                float density = getResources().getDisplayMetrics().density;
                CoordinatorLayout.LayoutParams lp = (CoordinatorLayout.LayoutParams) v.getLayoutParams();
                lp.bottomMargin = (int) ((16 + 80) * density) + navBarHeight;
                v.setLayoutParams(lp);
                return insets;
            });
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        LayoutShapeHelper.applyToView(getView());
    }

    private void updateHeaderUI() {
        if (showUpcoming) {
            tvFlightsTitle.setText("Upcoming Trips");
            btnTogglePastFuture.setText("Past Flights");
        } else {
            tvFlightsTitle.setText("Past Flights");
            btnTogglePastFuture.setText("Upcoming Trips");
        }
    }

    private void loadFlights() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            List<Flight> allFlights = db.flightDao().getAllFlights();
            
            // Get today's date formatted as YYYY-MM-DD
            String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

            // On initial load, default to showing past flights if there are no upcoming ones
            if (isInitialLoad) {
                int upcomingCount = 0;
                for (Flight f : allFlights) {
                    if (f.date != null && f.date.compareTo(todayStr) >= 0) {
                        upcomingCount++;
                    }
                }
                showUpcoming = (upcomingCount > 0);
                isInitialLoad = false;

                // Update the Header UI on the main thread
                new Handler(Looper.getMainLooper()).post(this::updateHeaderUI);
            }

            // Partition and sort flights
            List<Flight> filteredFlights = new ArrayList<>();
            for (Flight f : allFlights) {
                if (f.date != null) {
                    if (showUpcoming) {
                        if (f.date.compareTo(todayStr) >= 0) {
                            filteredFlights.add(f);
                        }
                    } else {
                        if (f.date.compareTo(todayStr) < 0) {
                            filteredFlights.add(f);
                        }
                    }
                }
            }

            // Sort logic
            filteredFlights.sort((a, b) -> {
                int dateCompare = a.date.compareTo(b.date);
                if (dateCompare != 0) {
                    // Upcoming: chronological ascending (soonest first)
                    // Past: chronological descending (most recent first)
                    return showUpcoming ? dateCompare : -dateCompare;
                }
                // If dates are equal, compare departure times
                String depA = a.departureTime != null ? a.departureTime : "";
                String depB = b.departureTime != null ? b.departureTime : "";
                int timeCompare = depA.compareTo(depB);
                return showUpcoming ? timeCompare : -timeCompare;
            });

            List<FlightListItem> items = new ArrayList<>();

            for (Flight f : filteredFlights) {
                FlightListItem item = new FlightListItem();
                item.id            = f.id;
                item.origin        = f.origin;
                item.destination   = f.destination;
                item.date          = f.date;
                item.distance      = f.distance;
                item.flightNumber  = f.flightNumber;
                item.airline       = f.airline;
                item.seat          = f.seat;
                item.seatClass     = f.seatClass;
                item.notes         = f.notes;
                item.departureTime = f.departureTime;
                item.arrivalTime   = f.arrivalTime;

                // Enrich with airport data
                Airport originAirport = db.airportDao().getByIata(f.origin);
                Airport destAirport   = db.airportDao().getByIata(f.destination);
                if (originAirport != null) {
                    item.originCity    = originAirport.city;
                    item.originCountry = originAirport.country;
                }
                if (destAirport != null) {
                    item.destCity      = destAirport.city;
                    item.destCountry    = destAirport.country;
                }

                items.add(item);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (adapter != null) adapter.setFlights(items);
                if (tvEmptyState != null) {
                    if (items.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        if (showUpcoming) {
                            tvEmptyState.setText("No upcoming trips\n\nTap 'Past Flights' to see your history");
                        } else {
                            tvEmptyState.setText("No past flights found");
                        }
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                    }
                }
            });
        });
    }
}

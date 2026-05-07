package com.example.flightstats;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class FlightsFragment extends Fragment {

    private FlightAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_flights, container, false);

        RecyclerView rv = view.findViewById(R.id.flights_recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FlightAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        loadFlights();

        FloatingActionButton fabAddFlight = view.findViewById(R.id.fab_add_flight);
        fabAddFlight.setOnClickListener(v -> {
            AddFlightBottomSheet sheet = new AddFlightBottomSheet();
            sheet.setOnFlightAddedListener(this::loadFlights);
            sheet.show(getChildFragmentManager(), "add_flight");
        });

        return view;
    }

    private void loadFlights() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            List<Flight> flights = db.flightDao().getAllFlights();
            List<FlightListItem> items = new ArrayList<>();

            for (Flight f : flights) {
                FlightListItem item = new FlightListItem();
                item.id           = f.id;
                item.origin       = f.origin;
                item.destination  = f.destination;
                item.date         = f.date;
                item.distance     = f.distance;
                item.flightNumber = f.flightNumber;
                item.airline      = f.airline;
                item.seat         = f.seat;
                item.seatClass    = f.seatClass;
                item.notes        = f.notes;

                // Enrich with airport data
                Airport originAirport = db.airportDao().getByIata(f.origin);
                Airport destAirport   = db.airportDao().getByIata(f.destination);
                if (originAirport != null) {
                    item.originCity    = originAirport.city;
                    item.originCountry = originAirport.country;
                }
                if (destAirport != null) {
                    item.destCity    = destAirport.city;
                    item.destCountry = destAirport.country;
                }

                items.add(item);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (adapter != null) adapter.setFlights(items);
            });
        });
    }
}

package com.example.flightstats;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.transition.TransitionManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.Executors;

public class FlightAdapter extends RecyclerView.Adapter<FlightAdapter.ViewHolder> {

    private static final String[] SEAT_CLASSES = {"Economy", "Premium Economy", "Business", "First"};

    private List<FlightListItem> flights;
    private int expandedId = -1; // only one item expanded at a time (accordion)

    public FlightAdapter(List<FlightListItem> flights) {
        this.flights = flights;
    }

    public void setFlights(List<FlightListItem> flights) {
        this.flights = flights;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flight, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        FlightListItem item = flights.get(position);
        boolean expanded = (expandedId == item.id);

        // ── Collapsed row ──
        h.tvOriginFlag.setText(FlightListItem.countryToFlag(item.originCountry));
        h.tvOrigin.setText(item.origin);
        h.tvDestFlag.setText(FlightListItem.countryToFlag(item.destCountry));
        h.tvDestination.setText(item.destination);
        h.tvDate.setText(item.date != null ? item.date : "");
        h.tvDistance.setText(item.distance > 0 ? Math.round(item.distance) + " km" : "");

        // ── Expanded section visibility ──
        h.sectionExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (expanded) {
            // City names line
            String originCity = item.originCity != null ? item.originCity : item.origin;
            String destCity   = item.destCity   != null ? item.destCity   : item.destination;
            h.tvCities.setText(originCity + "  →  " + destCity);

            // Pre-fill editable fields
            setText(h.inputAirline,      item.airline);
            setText(h.inputFlightNumber, item.flightNumber);
            setText(h.inputSeat,         item.seat);
            setText(h.inputNotes,        item.notes);

            // Seat class dropdown
            ArrayAdapter<String> classAdapter = new ArrayAdapter<>(
                    h.itemView.getContext(), android.R.layout.simple_dropdown_item_1line, SEAT_CLASSES);
            h.inputSeatClass.setAdapter(classAdapter);
            if (item.seatClass != null) h.inputSeatClass.setText(item.seatClass, false);

            h.btnSave.setVisibility(View.GONE);

            // Watch for changes to show save button
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    h.btnSave.setVisibility(View.VISIBLE);
                }
            };
            h.inputAirline.addTextChangedListener(watcher);
            h.inputFlightNumber.addTextChangedListener(watcher);
            h.inputSeat.addTextChangedListener(watcher);
            h.inputNotes.addTextChangedListener(watcher);
            h.inputSeatClass.addTextChangedListener(watcher);

            // Delete button
            h.btnDelete.setOnClickListener(v -> {
                Flight f = new Flight();
                f.id = item.id; // DAO uses ID to delete
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getDatabase(v.getContext()).flightDao().deleteFlight(f);
                });
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    flights.remove(pos);
                    notifyItemRemoved(pos);
                }
            });

            // Save button
            h.btnSave.setOnClickListener(v -> {
                Flight f = new Flight();
                f.id          = item.id;
                f.origin      = item.origin;
                f.destination = item.destination;
                f.date        = item.date;
                f.distance    = item.distance;
                f.airline     = text(h.inputAirline);
                f.flightNumber= text(h.inputFlightNumber);
                f.seat        = text(h.inputSeat);
                f.seatClass   = h.inputSeatClass.getText().toString().trim();
                f.notes       = text(h.inputNotes);

                // Update item cache too
                item.airline      = f.airline;
                item.flightNumber = f.flightNumber;
                item.seat         = f.seat;
                item.seatClass    = f.seatClass;
                item.notes        = f.notes;

                Executors.newSingleThreadExecutor().execute(() ->
                        AppDatabase.getDatabase(v.getContext()).flightDao().updateFlight(f));

                // Collapse after save
                expandedId = -1;
                notifyDataSetChanged();
            });
        }

        h.itemView.setOnClickListener(v -> {
            int prev = expandedId;
            expandedId = (expandedId == item.id) ? -1 : item.id;
            // Notify old and new positions so both animate
            notifyDataSetChanged();
        });
    }

    @Override
    public int getItemCount() {
        return flights == null ? 0 : flights.size();
    }

    private void setText(TextInputEditText field, String value) {
        field.setText(value != null ? value : "");
    }

    private String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOriginFlag, tvOrigin, tvDestFlag, tvDestination, tvDate, tvDistance, tvCities;
        View sectionExpanded;
        TextInputEditText inputAirline, inputFlightNumber, inputSeat, inputNotes;
        AutoCompleteTextView inputSeatClass;
        View btnSave, btnDelete;

        ViewHolder(View v) {
            super(v);
            tvOriginFlag      = v.findViewById(R.id.tv_origin_flag);
            tvOrigin          = v.findViewById(R.id.tv_origin);
            tvDestFlag        = v.findViewById(R.id.tv_dest_flag);
            tvDestination     = v.findViewById(R.id.tv_destination);
            tvDate            = v.findViewById(R.id.tv_date);
            tvDistance        = v.findViewById(R.id.tv_distance);
            tvCities          = v.findViewById(R.id.tv_cities);
            sectionExpanded   = v.findViewById(R.id.section_expanded);
            inputAirline      = v.findViewById(R.id.input_airline);
            inputFlightNumber = v.findViewById(R.id.input_flight_number);
            inputSeat         = v.findViewById(R.id.input_seat);
            inputSeatClass    = v.findViewById(R.id.input_seat_class);
            inputNotes        = v.findViewById(R.id.input_notes);
            btnSave           = v.findViewById(R.id.btn_save_details);
            btnDelete         = v.findViewById(R.id.btn_delete_flight);
        }
    }
}

package com.example.flightstats;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
        h.tvTripDateHeader.setText(getRelativeDateString(item.date));
        h.tvDistance.setText(item.distance > 0 ? Math.round(item.distance) + " km" : "");
        h.tvDepartureTime.setText(item.departureTime != null && !item.departureTime.isEmpty() ? item.departureTime : "—:—");
        h.tvArrivalTime.setText(item.arrivalTime != null && !item.arrivalTime.isEmpty() ? item.arrivalTime : "—:—");

        // ── Expanded section visibility ──
        h.sectionExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);

        if (expanded) {
            // City names line
            String originCity = item.originCity != null ? item.originCity : item.origin;
            String destCity   = item.destCity   != null ? item.destCity   : item.destination;
            h.tvCities.setText(originCity + "  →  " + destCity);

            // Pre-fill editable fields if they are not currently focused
            setText(h.inputAirline,            item.airline);
            setText(h.inputFlightNumber,       item.flightNumber);
            setText(h.inputSeat,               item.seat);
            setText(h.inputNotes,              item.notes);
            setText(h.inputEditDepartureTime,  item.departureTime);
            setText(h.inputEditArrivalTime,    item.arrivalTime);

            // Seat class dropdown
            ArrayAdapter<String> classAdapter = new ArrayAdapter<>(
                    h.itemView.getContext(), android.R.layout.simple_dropdown_item_1line, SEAT_CLASSES);
            h.inputSeatClass.setAdapter(classAdapter);
            if (item.seatClass != null) h.inputSeatClass.setText(item.seatClass, false);

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
                f.id            = item.id;
                f.origin        = item.origin;
                f.destination   = item.destination;
                f.date          = item.date;
                f.distance      = item.distance;
                f.airline       = text(h.inputAirline);
                f.flightNumber  = text(h.inputFlightNumber);
                f.seat          = text(h.inputSeat);
                f.seatClass     = h.inputSeatClass.getText().toString().trim();
                f.notes         = text(h.inputNotes);
                f.departureTime = text(h.inputEditDepartureTime);
                f.arrivalTime   = text(h.inputEditArrivalTime);

                // Update item cache too
                item.airline       = f.airline;
                item.flightNumber  = f.flightNumber;
                item.seat          = f.seat;
                item.seatClass     = f.seatClass;
                item.notes         = f.notes;
                item.departureTime = f.departureTime;
                item.arrivalTime   = f.arrivalTime;

                Executors.newSingleThreadExecutor().execute(() ->
                        AppDatabase.getDatabase(v.getContext()).flightDao().updateFlight(f));

                // Collapse after save
                expandedId = -1;
                notifyDataSetChanged();
            });
        }

        // Set click listeners on the collapsed row and date header instead of root view to prevent keyboard collapse bugs
        View.OnClickListener toggleExpandListener = v -> {
            expandedId = (expandedId == item.id) ? -1 : item.id;
            notifyDataSetChanged();
        };
        h.rowCollapsed.setOnClickListener(toggleExpandListener);
        h.tvTripDateHeader.setOnClickListener(toggleExpandListener);
        h.itemView.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return flights == null ? 0 : flights.size();
    }

    private void setText(TextInputEditText field, String value) {
        if (!field.isFocused()) {
            field.setText(value != null ? value : "");
        }
    }

    private String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    public static String getRelativeDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date flightDate = sdf.parse(dateStr);
            if (flightDate == null) return dateStr;

            SimpleDateFormat formatOut = new SimpleDateFormat("MMM d, yyyy", Locale.US);
            String absoluteDate = formatOut.format(flightDate);

            Calendar calToday = Calendar.getInstance();
            calToday.set(Calendar.HOUR_OF_DAY, 0);
            calToday.set(Calendar.MINUTE, 0);
            calToday.set(Calendar.SECOND, 0);
            calToday.set(Calendar.MILLISECOND, 0);

            Calendar calFlight = Calendar.getInstance();
            calFlight.setTime(flightDate);
            calFlight.set(Calendar.HOUR_OF_DAY, 0);
            calFlight.set(Calendar.MINUTE, 0);
            calFlight.set(Calendar.SECOND, 0);
            calFlight.set(Calendar.MILLISECOND, 0);

            long diffMs = calFlight.getTimeInMillis() - calToday.getTimeInMillis();
            long diffDays = diffMs / (24 * 60 * 60 * 1000);

            String relative;
            if (diffDays == 0) {
                relative = "Today";
            } else if (diffDays == 1) {
                relative = "Tomorrow";
            } else if (diffDays == -1) {
                relative = "Yesterday";
            } else if (diffDays > 1) {
                if (diffDays < 7) {
                    relative = "In " + diffDays + " days";
                } else if (diffDays < 30) {
                    long weeks = diffDays / 7;
                    relative = "In " + weeks + (weeks == 1 ? " week" : " weeks");
                } else if (diffDays < 365) {
                    long months = diffDays / 30;
                    relative = "In " + months + (months == 1 ? " month" : " months");
                } else {
                    long years = diffDays / 365;
                    relative = "In " + years + (years == 1 ? " year" : " years");
                }
            } else {
                long absDays = Math.abs(diffDays);
                if (absDays < 7) {
                    relative = absDays + " days ago";
                } else if (absDays < 30) {
                    long weeks = absDays / 7;
                    relative = weeks + (weeks == 1 ? " week" : " weeks") + " ago";
                } else if (absDays < 365) {
                    long months = absDays / 30;
                    relative = months + (months == 1 ? " month" : " months") + " ago";
                } else {
                    long years = absDays / 365;
                    relative = years + (years == 1 ? " year" : " years") + " ago";
                }
            }

            return relative + " · " + absoluteDate;
        } catch (Exception e) {
            return dateStr;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOriginFlag, tvOrigin, tvDestFlag, tvDestination, tvTripDateHeader, tvDistance, tvCities, tvDepartureTime, tvArrivalTime;
        View sectionExpanded, rowCollapsed;
        TextInputEditText inputAirline, inputFlightNumber, inputSeat, inputNotes, inputEditDepartureTime, inputEditArrivalTime;
        AutoCompleteTextView inputSeatClass;
        View btnSave, btnDelete;
        TextWatcher activeWatcher;

        ViewHolder(View v) {
            super(v);
            tvOriginFlag           = v.findViewById(R.id.tv_origin_flag);
            tvOrigin               = v.findViewById(R.id.tv_origin);
            tvDestFlag             = v.findViewById(R.id.tv_dest_flag);
            tvDestination          = v.findViewById(R.id.tv_destination);
            tvTripDateHeader       = v.findViewById(R.id.tv_trip_date_header);
            tvDistance             = v.findViewById(R.id.tv_distance);
            tvCities               = v.findViewById(R.id.tv_cities);
            tvDepartureTime        = v.findViewById(R.id.tv_departure_time);
            tvArrivalTime          = v.findViewById(R.id.tv_arrival_time);
            sectionExpanded        = v.findViewById(R.id.section_expanded);
            rowCollapsed           = v.findViewById(R.id.row_collapsed);
            inputEditDepartureTime = v.findViewById(R.id.input_edit_departure_time);
            inputEditArrivalTime   = v.findViewById(R.id.input_edit_arrival_time);
            inputAirline           = v.findViewById(R.id.input_airline);
            inputFlightNumber      = v.findViewById(R.id.input_flight_number);
            inputSeat              = v.findViewById(R.id.input_seat);
            inputSeatClass         = v.findViewById(R.id.input_seat_class);
            inputNotes             = v.findViewById(R.id.input_notes);
            btnSave                = v.findViewById(R.id.btn_save_details);
            btnDelete              = v.findViewById(R.id.btn_delete_flight);
        }
    }
}

package com.example.flightstats;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.flightstats.data.Airport;
import com.example.flightstats.data.AppDatabase;
import com.example.flightstats.data.Flight;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AddFlightBottomSheet extends BottomSheetDialogFragment {

    private static final String[] SEAT_CLASSES = {"Economy", "Premium Economy", "Business", "First"};

    public interface OnFlightAddedListener { void onFlightAdded(); }
    private OnFlightAddedListener listener;

    private TextInputEditText inputDate, inputOrigin, inputDestination,
                              inputFlightNumber, inputAirline, inputSeat, inputNotes,
                              inputDepartureTime, inputArrivalTime;
    private AutoCompleteTextView inputSeatClass;
    private long selectedDateMs = System.currentTimeMillis();

    private final ActivityResultLauncher<android.content.Intent> scannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    android.content.Intent data = result.getData();
                    inputOrigin.setText(data.getStringExtra(BarcodeScannerActivity.EXTRA_ORIGIN));
                    inputDestination.setText(data.getStringExtra(BarcodeScannerActivity.EXTRA_DESTINATION));
                    inputAirline.setText(data.getStringExtra(BarcodeScannerActivity.EXTRA_AIRLINE));
                    inputFlightNumber.setText(data.getStringExtra(BarcodeScannerActivity.EXTRA_FLIGHT_NUM));
                    inputSeat.setText(data.getStringExtra(BarcodeScannerActivity.EXTRA_SEAT));
                    
                    String dateStr = data.getStringExtra(BarcodeScannerActivity.EXTRA_DATE);
                    if (dateStr != null && !dateStr.isEmpty()) {
                        inputDate.setText(dateStr);
                        try {
                            selectedDateMs = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr).getTime();
                        } catch (Exception e) {}
                    }
                    
                    String cls = data.getStringExtra(BarcodeScannerActivity.EXTRA_CLASS);
                    if (cls != null && !cls.isEmpty()) {
                        inputSeatClass.setText(cls, false);
                    }
                    
                    Toast.makeText(requireContext(), "Scanned flight " + data.getStringExtra(BarcodeScannerActivity.EXTRA_FLIGHT_NUM), Toast.LENGTH_SHORT).show();
                }
            });

    public void setOnFlightAddedListener(OnFlightAddedListener l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_add_flight, container, false);

        inputDate         = view.findViewById(R.id.input_date);
        inputOrigin       = view.findViewById(R.id.input_origin);
        inputDestination  = view.findViewById(R.id.input_destination);
        inputFlightNumber = view.findViewById(R.id.input_flight_number);
        inputAirline      = view.findViewById(R.id.input_airline);
        inputSeat         = view.findViewById(R.id.input_seat);
        inputSeatClass    = view.findViewById(R.id.input_seat_class);
        inputNotes        = view.findViewById(R.id.input_notes);
        inputDepartureTime = view.findViewById(R.id.input_departure_time);
        inputArrivalTime   = view.findViewById(R.id.input_arrival_time);

        // Seat class dropdown
        ArrayAdapter<String> classAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, SEAT_CLASSES);
        inputSeatClass.setAdapter(classAdapter);

        updateDateField(System.currentTimeMillis());

        inputDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select flight date")
                    .setSelection(selectedDateMs)
                    .build();
            picker.addOnPositiveButtonClickListener(sel -> {
                selectedDateMs = sel;
                updateDateField(sel);
            });
            picker.show(getParentFragmentManager(), "date_picker");
        });

        view.findViewById(R.id.btn_scan_pass).setOnClickListener(v -> {
            scannerLauncher.launch(new android.content.Intent(requireContext(), BarcodeScannerActivity.class));
        });

        view.findViewById(R.id.btn_save_flight).setOnClickListener(v -> saveFlight());
        return view;
    }

    private void updateDateField(long ms) {
        inputDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms)));
    }

    private void saveFlight() {
        String origin = str(inputOrigin).toUpperCase();
        String dest   = str(inputDestination).toUpperCase();
        String date   = str(inputDate);

        if (origin.length() < 3) { inputOrigin.setError("Enter a valid IATA code"); return; }
        if (dest.length() < 3)   { inputDestination.setError("Enter a valid IATA code"); return; }

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(requireContext());
            Airport oAirport = db.airportDao().getByIata(origin);
            Airport dAirport = db.airportDao().getByIata(dest);

            if (oAirport == null) { requireActivity().runOnUiThread(() -> inputOrigin.setError("Airport not found: " + origin)); return; }
            if (dAirport == null) { requireActivity().runOnUiThread(() -> inputDestination.setError("Airport not found: " + dest)); return; }

            double distance = haversine(oAirport.lat, oAirport.lng, dAirport.lat, dAirport.lng);

            Flight f = new Flight();
            f.origin        = origin;
            f.destination   = dest;
            f.date          = date;
            f.distance      = distance;
            f.flightNumber  = str(inputFlightNumber);
            f.airline       = str(inputAirline);
            f.seat          = str(inputSeat);
            f.seatClass     = inputSeatClass.getText().toString().trim();
            f.notes         = str(inputNotes);
            f.departureTime = str(inputDepartureTime);
            f.arrivalTime   = str(inputArrivalTime);

            db.flightDao().insertFlight(f);

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        "Added: " + origin + " → " + dest + " (" + Math.round(distance) + " km)",
                        Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onFlightAdded();
                dismiss();
            });
        });
    }

    private String str(TextInputEditText field) {
        return field != null && field.getText() != null ? field.getText().toString().trim() : "";
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

package com.example.flightstats.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads local_flights.csv from assets and imports into Room on first launch.
 * Import is keyed by DB version — if the DB was wiped (version bump), re-imports automatically.
 */
public class CsvImporter {
    private static final String TAG = "CsvImporter";
    private static final String PREFS_NAME = "FlightStatsPrefs";
    private static final String KEY_FLIGHTS_DB_VERSION = "flights_db_version";
    // Bump this whenever AppDatabase.version changes to force a re-import
    private static final int CURRENT_DB_VERSION = 6; // Bumped to 6 to fix duplicates

    public interface ImportCallback {
        void onComplete(int count);
    }

    public static void importIfNeeded(Context context, ImportCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int importedVersion = prefs.getInt(KEY_FLIGHTS_DB_VERSION, -1);

        if (importedVersion == CURRENT_DB_VERSION) {
            if (callback != null) callback.onComplete(0);
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int count = 0;
            try {
                AppDatabase db = AppDatabase.getDatabase(context);
                FlightDao dao = db.flightDao();

                List<Flight> flights = parseCsv(context);
                if (!flights.isEmpty()) {
                    dao.deleteAll(); // Clear existing flights to prevent duplicates on forced re-imports
                    dao.insertAll(flights);
                    count = flights.size();
                }

                prefs.edit().putInt(KEY_FLIGHTS_DB_VERSION, CURRENT_DB_VERSION).apply();
                Log.d(TAG, "Imported " + count + " flights from CSV.");
            } catch (Exception e) {
                Log.e(TAG, "CSV import failed", e);
            }

            final int finalCount = count;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onComplete(finalCount));
            }
        });
    }

    private static List<Flight> parseCsv(Context context) throws Exception {
        List<Flight> flights = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("local_flights.csv")));

        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) { firstLine = false; continue; }
            if (line.trim().isEmpty()) continue;

            String[] cols = line.split(",", -1);
            if (cols.length < 5) continue;

            Flight f = new Flight();
            f.origin      = cols[1].trim();
            f.destination = cols[2].trim();
            f.date        = cols[3].trim();
            try { f.distance = Double.parseDouble(cols[4].trim()); } catch (NumberFormatException ignored) {}
            flights.add(f);
        }
        reader.close();
        return flights;
    }
}

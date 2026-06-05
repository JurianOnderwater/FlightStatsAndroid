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

public class AirportImporter {
    private static final String TAG = "AirportImporter";
    private static final String PREFS_NAME = "FlightStatsPrefs";
    private static final String KEY_AIRPORTS_DB_VERSION = "airports_db_version";
    private static final int CURRENT_DB_VERSION = 7; // Bumped to force re-import

    public interface ImportCallback {
        void onComplete(int count);
    }

    public static void importIfNeeded(Context context, ImportCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int count = 0;
            try {
                AppDatabase db = AppDatabase.getDatabase(context);
                AirportDao dao = db.airportDao();
                int existingCount = dao.count();

                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int importedVersion = prefs.getInt(KEY_AIRPORTS_DB_VERSION, -1);

                if (existingCount == 0 || importedVersion != CURRENT_DB_VERSION) {
                    List<Airport> airports = parseCsv(context);
                    if (!airports.isEmpty()) {
                        // Insert in chunks
                        int chunkSize = 500;
                        for (int i = 0; i < airports.size(); i += chunkSize) {
                            dao.insertAll(airports.subList(i, Math.min(i + chunkSize, airports.size())));
                        }
                        count = airports.size();
                    }
                    prefs.edit().putInt(KEY_AIRPORTS_DB_VERSION, CURRENT_DB_VERSION).apply();
                    Log.d(TAG, "Imported " + count + " airports.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Airport import failed", e);
            }

            final int finalCount = count;
            if (callback != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onComplete(finalCount));
            }
        });
    }

    private static List<Airport> parseCsv(Context context) throws Exception {
        List<Airport> airports = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("airports_compact.csv")));

        String line;
        boolean firstLine = true;
        while ((line = reader.readLine()) != null) {
            if (firstLine) { firstLine = false; continue; }
            if (line.trim().isEmpty()) continue;

            String[] cols = line.split(",", 6);
            if (cols.length < 6 || cols[0].trim().isEmpty()) continue;

            Airport a = new Airport();
            a.iata    = cols[0].trim();
            try { a.lat = Double.parseDouble(cols[1].trim()); } catch (NumberFormatException ignored) { continue; }
            try { a.lng = Double.parseDouble(cols[2].trim()); } catch (NumberFormatException ignored) { continue; }
            a.country = cols[3].trim();
            a.name    = cols[4].trim();
            a.city    = cols[5].trim();
            airports.add(a);
        }
        reader.close();
        return airports;
    }
}

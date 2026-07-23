package com.example.flightstats.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object AirportImporter {
    private const val TAG = "AirportImporter"
    private const val PREFS_NAME = "FlightStatsPrefs"
    private const val KEY_AIRPORTS_DB_VERSION = "airports_db_version"
    private const val CURRENT_DB_VERSION = 7

    suspend fun importIfNeeded(context: Context, repository: FlightRepository) = withContext(Dispatchers.IO) {
        try {
            val existingCount = repository.countAirports()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val importedVersion = prefs.getInt(KEY_AIRPORTS_DB_VERSION, -1)

            if (existingCount == 0 || importedVersion != CURRENT_DB_VERSION) {
                val airports = parseCsv(context)
                if (airports.isNotEmpty()) {
                    // Insert in chunks to prevent SQLite limits/memory issues
                    val chunkSize = 500
                    for (i in airports.indices step chunkSize) {
                        val chunk = airports.subList(i, minOf(i + chunkSize, airports.size))
                        repository.insertAirports(chunk)
                    }
                    Log.d(TAG, "Imported ${airports.size} airports.")
                }
                prefs.edit().putInt(KEY_AIRPORTS_DB_VERSION, CURRENT_DB_VERSION).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Airport import failed", e)
        }
    }

    private fun parseCsv(context: Context): List<Airport> {
        val airports = mutableListOf<Airport>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("airports_compact.csv")))
            var line: String?
            var firstLine = true
            while (reader.readLine().also { line = it } != null) {
                if (firstLine) {
                    firstLine = false
                    continue
                }
                val currentLine = line ?: continue
                if (currentLine.trim().isEmpty()) continue

                val cols = currentLine.split(",", limit = 6)
                if (cols.size < 6 || cols[0].trim().isEmpty()) continue

                val lat = cols[1].trim().toDoubleOrNull() ?: continue
                val lng = cols[2].trim().toDoubleOrNull() ?: continue

                val airport = Airport(
                    iata = cols[0].trim(),
                    lat = lat,
                    lng = lng,
                    country = cols[3].trim(),
                    name = cols[4].trim(),
                    city = cols[5].trim()
                )
                airports.add(airport)
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Parsing airport CSV failed", e)
        }
        return airports
    }
}

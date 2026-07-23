package com.example.flightstats.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object CsvImporter {
    private const val TAG = "CsvImporter"
    private const val PREFS_NAME = "FlightStatsPrefs"
    private const val KEY_FLIGHTS_DB_VERSION = "flights_db_version"
    private const val CURRENT_DB_VERSION = 7

    suspend fun importIfNeeded(context: Context, repository: FlightRepository) = withContext(Dispatchers.IO) {
        try {
            val existing = repository.getAllFlights()
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val importedVersion = prefs.getInt(KEY_FLIGHTS_DB_VERSION, -1)

            if (existing.isEmpty() || importedVersion != CURRENT_DB_VERSION) {
                val flights = parseCsv(context)
                if (flights.isNotEmpty()) {
                    repository.deleteAllFlights() // Clear existing flights to prevent duplicates
                    repository.insertAllFlights(flights)
                    Log.d(TAG, "Imported ${flights.size} flights from CSV.")
                }
                prefs.edit().putInt(KEY_FLIGHTS_DB_VERSION, CURRENT_DB_VERSION).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV import failed", e)
        }
    }

    private fun parseCsv(context: Context): List<Flight> {
        val flights = mutableListOf<Flight>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("local_flights.csv")))
            var line: String?
            var firstLine = true
            while (reader.readLine().also { line = it } != null) {
                if (firstLine) {
                    firstLine = false
                    continue
                }
                val currentLine = line ?: continue
                if (currentLine.trim().isEmpty()) continue

                val cols = currentLine.split(",", limit = -1)
                if (cols.size < 5) continue

                val distance = cols[4].trim().toDoubleOrNull() ?: 0.0
                val flight = Flight(
                    origin = cols[1].trim(),
                    destination = cols[2].trim(),
                    date = cols[3].trim(),
                    distance = distance,
                    flightNumber = null,
                    airline = null,
                    seat = null,
                    seatClass = null,
                    notes = null,
                    departureTime = null,
                    arrivalTime = null
                )
                flights.add(flight)
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Parsing CSV failed", e)
        }
        return flights
    }
}

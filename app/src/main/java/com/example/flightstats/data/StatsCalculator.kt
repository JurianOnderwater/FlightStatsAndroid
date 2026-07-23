package com.example.flightstats.data

import java.util.Arrays

data class StatsCalculator(
    val totalFlights: Int = 0,
    val uniqueCountries: Int = 0,
    val uniqueAirports: Int = 0,
    val uniqueRoutes: Int = 0,
    val totalDistanceKm: Double = 0.0
) {
    companion object {
        fun compute(flights: List<Flight>, airports: AirportRepository?): StatsCalculator {
            val airportSet = mutableSetOf<String>()
            val routeSet = mutableSetOf<String>()
            val countrySet = mutableSetOf<String>()
            var distance = 0.0

            for (f in flights) {
                val o = f.origin.orEmpty()
                val d = f.destination.orEmpty()
                airportSet.add(o)
                airportSet.add(d)

                // Canonical route (sorted so AMS-OSL == OSL-AMS)
                val pair = arrayOf(o, d)
                pair.sort()
                routeSet.add("${pair[0]}-${pair[1]}")
                distance += f.distance

                if (airports != null) {
                    airports.getCountry(o)?.let { countrySet.add(it) }
                    airports.getCountry(d)?.let { countrySet.add(it) }
                }
            }

            return StatsCalculator(
                totalFlights = flights.size,
                uniqueAirports = airportSet.size,
                uniqueRoutes = routeSet.size,
                uniqueCountries = countrySet.size,
                totalDistanceKm = distance
            )
        }
    }
}

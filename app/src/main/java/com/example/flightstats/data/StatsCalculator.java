package com.example.flightstats.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates hero stats from a list of Flight objects.
 */
public class StatsCalculator {

    public int totalFlights;
    public int uniqueCountries;
    public int uniqueAirports;
    public int uniqueRoutes;
    public double totalDistanceKm;

    // Airport → country mapping is looked up from the WebView's airport data.
    // For the native hero stats we track airports and routes only.
    public static StatsCalculator compute(List<Flight> flights, AirportRepository airports) {
        StatsCalculator s = new StatsCalculator();
        s.totalFlights = flights.size();

        Set<String> airportSet = new HashSet<>();
        Set<String> routeSet = new HashSet<>();
        Set<String> countrySet = new HashSet<>();

        for (Flight f : flights) {
            airportSet.add(f.origin);
            airportSet.add(f.destination);
            // Canonical route (sorted so AMS-OSL == OSL-AMS)
            String[] pair = new String[]{f.origin, f.destination};
            java.util.Arrays.sort(pair);
            routeSet.add(pair[0] + "-" + pair[1]);
            s.totalDistanceKm += f.distance;

            if (airports != null) {
                String originCountry = airports.getCountry(f.origin);
                String destCountry = airports.getCountry(f.destination);
                if (originCountry != null) countrySet.add(originCountry);
                if (destCountry != null) countrySet.add(destCountry);
            }
        }

        s.uniqueAirports = airportSet.size();
        s.uniqueRoutes = routeSet.size();
        s.uniqueCountries = countrySet.size();
        return s;
    }
}

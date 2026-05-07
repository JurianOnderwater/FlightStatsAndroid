package com.example.flightstats.data;

/**
 * Lightweight interface for looking up a country code by IATA airport code.
 * Implemented by MapFragment once it has loaded the airport CSV via WebView JS bridge.
 */
public interface AirportRepository {
    /** Returns the ISO 2-letter country code for the given IATA code, or null if unknown. */
    String getCountry(String iataCode);
}

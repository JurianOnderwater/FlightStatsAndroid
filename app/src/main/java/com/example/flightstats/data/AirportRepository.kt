package com.example.flightstats.data

/**
 * Lightweight interface for looking up a country code by IATA airport code.
 */
interface AirportRepository {
    /** Returns the ISO 2-letter country code for the given IATA code, or null if unknown. */
    fun getCountry(iataCode: String): String?
}

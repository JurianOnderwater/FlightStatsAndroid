package com.example.flightstats.data

/**
 * Lightweight interface for looking up a country code by IATA airport code.
 */
interface AirportRepository {
    /** Returns the ISO 2-letter country code for the given IATA code, or null if unknown. */
    suspend fun getCountry(iataCode: String): String?

    /** Returns a map of IATA code to Airport for a given list of IATA codes. */
    suspend fun getAirportsByIatas(iatas: Collection<String>): Map<String, Airport>
}

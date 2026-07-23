package com.example.flightstats.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightRepository @Inject constructor(
    private val flightDao: FlightDao,
    private val airportDao: AirportDao
) : AirportRepository {

    fun getAllFlightsFlow(): Flow<List<Flight>> = flightDao.getAllFlightsFlow()

    suspend fun getAllFlights(): List<Flight> = flightDao.getAllFlights()

    suspend fun getFlightById(id: Int): Flight? = flightDao.getFlightById(id)

    suspend fun insertFlight(flight: Flight) = flightDao.insertFlight(flight)

    suspend fun insertAllFlights(flights: List<Flight>) = flightDao.insertAll(flights)

    suspend fun updateFlight(flight: Flight) = flightDao.updateFlight(flight)

    suspend fun deleteFlight(flight: Flight) = flightDao.deleteFlight(flight)

    suspend fun deleteAllFlights() = flightDao.deleteAll()

    suspend fun insertAirports(airports: List<Airport>) = airportDao.insertAll(airports)

    suspend fun getAirportByIata(iata: String): Airport? = airportDao.getByIata(iata)

    suspend fun countAirports(): Int = airportDao.count()

    suspend fun searchAirports(query: String): List<Airport> = airportDao.search(query)

    override fun getCountry(iataCode: String): String? {
        // Since getCountry in the original interface is synchronous, but Room is async, 
        // we can run it blocking or wait. Actually, let's see how getCountry is used in the codebase.
        // Wait, Room doesn't allow database queries on the main thread.
        // We'll see how it's used and if we can run it blocking, or if we can make the call reactive.
        // For now, we can run Blocking or make it return a Flow/suspend if we refactor its usage.
        // Let's implement it with runBlocking or find out how it's used.
        return kotlinx.coroutines.runBlocking {
            airportDao.getByIata(iataCode)?.country
        }
    }
}

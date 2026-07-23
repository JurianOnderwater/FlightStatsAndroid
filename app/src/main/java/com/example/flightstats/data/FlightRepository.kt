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

    override suspend fun getCountry(iataCode: String): String? {
        return airportDao.getByIata(iataCode)?.country
    }

    override suspend fun getAirportsByIatas(iatas: Collection<String>): Map<String, Airport> {
        if (iatas.isEmpty()) return emptyMap()
        return airportDao.getByIatas(iatas.distinct()).associateBy { it.iata }
    }
}

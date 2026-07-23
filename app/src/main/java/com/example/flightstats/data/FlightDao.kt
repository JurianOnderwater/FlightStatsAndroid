package com.example.flightstats.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightDao {

    @Insert
    suspend fun insertFlight(flight: Flight): Long
    
    @Insert
    suspend fun insertAll(flights: List<Flight>): List<Long>

    @Update
    suspend fun updateFlight(flight: Flight): Int

    @Delete
    suspend fun deleteFlight(flight: Flight): Int

    @Query("DELETE FROM flights")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM flights ORDER BY date DESC")
    fun getAllFlightsFlow(): Flow<List<Flight>>

    @Query("SELECT * FROM flights ORDER BY date DESC")
    suspend fun getAllFlights(): List<Flight>

    @Query("SELECT * FROM flights WHERE id = :flightId")
    suspend fun getFlightById(flightId: Int): Flight?
}

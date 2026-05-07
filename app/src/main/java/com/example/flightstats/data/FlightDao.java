package com.example.flightstats.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface FlightDao {

    @Insert
    void insertFlight(Flight flight);
    
    @Insert
    void insertAll(List<Flight> flights);

    @Update
    void updateFlight(Flight flight);

    @Delete
    void deleteFlight(Flight flight);

    @Query("SELECT * FROM flights ORDER BY date DESC")
    List<Flight> getAllFlights();

    @Query("SELECT * FROM flights WHERE id = :flightId")
    Flight getFlightById(int flightId);
}

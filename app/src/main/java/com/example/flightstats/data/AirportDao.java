package com.example.flightstats.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AirportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Airport> airports);

    @Query("SELECT * FROM airports WHERE iata = :iata LIMIT 1")
    Airport getByIata(String iata);

    @Query("SELECT COUNT(*) FROM airports")
    int count();

    /** Full-text search by IATA, name, or city for autocomplete */
    @Query("SELECT * FROM airports WHERE iata LIKE :q OR name LIKE :q OR city LIKE :q LIMIT 20")
    List<Airport> search(String q);
}

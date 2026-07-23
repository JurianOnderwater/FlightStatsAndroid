package com.example.flightstats.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AirportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(airports: List<Airport>): List<Long>

    @Query("SELECT * FROM airports WHERE iata = :iata LIMIT 1")
    suspend fun getByIata(iata: String): Airport?

    @Query("SELECT COUNT(*) FROM airports")
    suspend fun count(): Int

    @Query("SELECT * FROM airports WHERE iata LIKE :q OR name LIKE :q OR city LIKE :q LIMIT 20")
    suspend fun search(q: String): List<Airport>
}

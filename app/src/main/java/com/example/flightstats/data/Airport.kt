package com.example.flightstats.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "airports")
data class Airport(
    @PrimaryKey
    val iata: String, // e.g. "AMS"
    val lat: Double,
    val lng: Double,
    val country: String?,  // ISO 2-letter e.g. "NL"
    val name: String?,     // "Amsterdam Airport Schiphol"
    val city: String?      // "Amsterdam"
)

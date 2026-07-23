package com.example.flightstats.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flights")
data class Flight(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String?,         // YYYY-MM-DD
    val origin: String?,        // IATA code e.g. "AMS"
    val destination: String?,   // IATA code e.g. "JFK"
    val distance: Double,      // km
    val flightNumber: String?, // e.g. "KL871"
    val airline: String?,      // e.g. "KLM"
    val seat: String?,         // e.g. "23A"
    val seatClass: String?,    // "Economy", "Business", "First", "Premium Economy"
    val notes: String?,        // free-form notes
    val departureTime: String?, // e.g. "14:30"
    val arrivalTime: String?   // e.g. "21:45"
)

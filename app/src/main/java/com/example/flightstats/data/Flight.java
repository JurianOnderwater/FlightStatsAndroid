package com.example.flightstats.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "flights")
public class Flight {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String date;        // YYYY-MM-DD
    public String origin;      // IATA code e.g. "AMS"
    public String destination; // IATA code e.g. "JFK"
    public double distance;    // km

    // Optional user-entered details
    public String flightNumber; // e.g. "KL871"
    public String airline;      // e.g. "KLM"
    public String seat;         // e.g. "23A"
    public String seatClass;    // "Economy", "Business", "First", "Premium Economy"
    public String notes;        // free-form notes
}

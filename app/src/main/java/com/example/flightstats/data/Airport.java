package com.example.flightstats.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "airports")
public class Airport {
    @PrimaryKey
    @NonNull
    public String iata = ""; // e.g. "AMS"

    public double lat;
    public double lng;
    public String country;  // ISO 2-letter e.g. "NL"
    public String name;     // "Amsterdam Airport Schiphol"
    public String city;     // "Amsterdam"
}

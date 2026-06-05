package com.example.flightstats;

/**
 * Enriched flight item for display — combines Room Flight with airport city/country lookups.
 */
public class FlightListItem {
    public int id;
    public String origin;
    public String destination;
    public String date;
    public double distance;
    public String flightNumber;
    public String airline;
    public String seat;
    public String seatClass;
    public String notes;
    public String departureTime;
    public String arrivalTime;

    // Resolved from Airport table
    public String originCity;
    public String originCountry;   // ISO 2-letter e.g. "NL"
    public String destCity;
    public String destCountry;

    public static String countryToFlag(String isoCode) {
        return "";
    }
}

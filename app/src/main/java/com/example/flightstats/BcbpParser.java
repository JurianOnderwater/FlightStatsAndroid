package com.example.flightstats;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Parses IATA BCBP (Bar Coded Boarding Pass) strings.
 * Supports QR, Aztec and PDF417 encoded boarding passes (all contain the same text payload).
 * Spec: IATA Resolution 792 — mandatory section only.
 */
public class BcbpParser {

    public static class Result {
        public boolean success;
        public String error;
        public String origin;
        public String destination;
        public String airline;
        public String flightNumber;
        public String seat;
        public String seatClass;
        public String date; // yyyy-MM-dd
    }

    public static Result parse(String raw) {
        Result r = new Result();
        if (raw == null || raw.length() < 58) {
            r.error = "Barcode too short to be a boarding pass";
            return r;
        }
        if (raw.charAt(0) != 'M') {
            r.error = "Not a BCBP boarding pass barcode";
            return r;
        }

        try {
            // Mandatory section fixed-offset fields (0-indexed):
            // [30-32] From city  [33-35] To city  [36-38] Carrier
            // [39-43] Flight no  [44-46] Julian date  [47] Class  [48-51] Seat
            r.origin      = raw.substring(30, 33).trim().toUpperCase();
            r.destination = raw.substring(33, 36).trim().toUpperCase();
            r.airline     = raw.substring(36, 39).trim().toUpperCase();

            String flightRaw = raw.substring(39, 44).trim().replaceAll("^0+", "");
            r.flightNumber = r.airline + flightRaw;

            r.date = julianToDate(raw.substring(44, 47).trim());

            char cls = raw.charAt(47);
            r.seatClass = compartmentToClass(cls);

            r.seat = raw.substring(48, 52).trim().replaceAll("^0+", "");

            r.success = r.origin.length() == 3 && r.destination.length() == 3;
            if (!r.success) r.error = "Could not extract airport codes";
        } catch (Exception e) {
            r.error = "Parse error: " + e.getMessage();
        }
        return r;
    }

    private static String julianToDate(String julianStr) {
        try {
            int doy = Integer.parseInt(julianStr);
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            // Try current year first; if already past, try next year
            cal.set(Calendar.DAY_OF_YEAR, doy);
            cal.set(Calendar.YEAR, year);
            if (cal.getTimeInMillis() < System.currentTimeMillis() - 86_400_000L) {
                cal.set(Calendar.YEAR, year + 1);
                cal.set(Calendar.DAY_OF_YEAR, doy);
            }
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
        } catch (Exception e) {
            return "";
        }
    }

    private static String compartmentToClass(char c) {
        switch (Character.toUpperCase(c)) {
            case 'F': case 'A': return "First";
            case 'J': case 'C': case 'D': case 'Z': case 'P': return "Business";
            case 'W': case 'S': return "Premium Economy";
            default: return "Economy";
        }
    }
}

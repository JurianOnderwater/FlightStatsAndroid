package com.example.flightstats

data class FlightListItem(
    val id: Int,
    val origin: String,
    val destination: String,
    val date: String?,
    val distance: Double,
    val flightNumber: String?,
    val airline: String?,
    val seat: String?,
    val seatClass: String?,
    val notes: String?,
    val departureTime: String?,
    val arrivalTime: String?,
    val originCity: String?,
    val originCountry: String?,
    val destCity: String?,
    val destCountry: String?
) {
    companion object {
        fun countryToFlag(isoCode: String?): String {
            if (isoCode == null || isoCode.length != 2) return ""
            val firstChar = Character.codePointAt(isoCode.uppercase(), 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(isoCode.uppercase(), 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        }
    }
}

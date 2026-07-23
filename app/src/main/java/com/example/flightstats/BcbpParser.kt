package com.example.flightstats

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object BcbpParser {

    class Result {
        var success: Boolean = false
        var error: String? = null
        var origin: String = ""
        var destination: String = ""
        var airline: String = ""
        var flightNumber: String = ""
        var seat: String = ""
        var seatClass: String = "Economy"
        var date: String = ""
    }

    fun parse(raw: String?): Result {
        val r = Result()
        if (raw == null || raw.length < 58) {
            r.error = "Barcode too short to be a boarding pass"
            return r
        }
        if (raw[0] != 'M') {
            r.error = "Not a BCBP boarding pass barcode"
            return r
        }

        try {
            r.origin = raw.substring(30, 33).trim().uppercase()
            r.destination = raw.substring(33, 36).trim().uppercase()
            r.airline = raw.substring(36, 39).trim().uppercase()

            val flightRaw = raw.substring(39, 44).trim().replace("^0+".toRegex(), "")
            r.flightNumber = r.airline + flightRaw

            r.date = julianToDate(raw.substring(44, 47).trim())

            val cls = raw[47]
            r.seatClass = compartmentToClass(cls)

            r.seat = raw.substring(48, 52).trim().replace("^0+".toRegex(), "")

            r.success = r.origin.length == 3 && r.destination.length == 3
            if (!r.success) r.error = "Could not extract airport codes"
        } catch (e: Exception) {
            r.error = "Parse error: " + e.message
        }
        return r
    }

    private fun julianToDate(julianStr: String): String {
        return try {
            val doy = julianStr.toInt()
            if (doy < 1 || doy > 366) return ""

            val calToday = Calendar.getInstance()
            val todayMs = calToday.timeInMillis
            val currentYear = calToday.get(Calendar.YEAR)

            var bestDiff = Long.MAX_VALUE
            var bestCal: Calendar? = null

            for (y in currentYear - 1..currentYear + 1) {
                val testCal = Calendar.getInstance().apply {
                    setLenient(true)
                    set(Calendar.YEAR, y)
                    set(Calendar.DAY_OF_YEAR, doy)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val diff = Math.abs(testCal.timeInMillis - todayMs)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestCal = testCal
                }
            }

            if (bestCal != null) {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(bestCal.time)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun compartmentToClass(c: Char): String {
        return when (c.uppercaseChar()) {
            'F', 'A' -> "First"
            'J', 'C', 'D', 'Z', 'P' -> "Business"
            'W', 'S' -> "Premium Economy"
            else -> "Economy"
        }
    }
}

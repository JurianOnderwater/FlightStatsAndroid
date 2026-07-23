package com.example.flightstats

import org.osmdroid.util.GeoPoint

object GeodesicHelper {

    fun greatCircleArc(from: GeoPoint, to: GeoPoint, steps: Int): List<GeoPoint> {
        val pts = mutableListOf<GeoPoint>()

        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        // Angular distance between two points
        val d = 2 * Math.asin(
            Math.sqrt(
                Math.pow(Math.sin((lat2 - lat1) / 2), 2.0) +
                        Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon2 - lon1) / 2), 2.0)
            )
        )

        if (d < 0.001) {
            pts.add(from)
            pts.add(to)
            return pts
        }

        for (i in 0..steps) {
            val f = i.toDouble() / steps
            val a = Math.sin((1 - f) * d) / Math.sin(d)
            val b = Math.sin(f * d) / Math.sin(d)

            val x = a * Math.cos(lat1) * Math.cos(lon1) + b * Math.cos(lat2) * Math.cos(lon2)
            val y = a * Math.cos(lat1) * Math.sin(lon1) + b * Math.cos(lat2) * Math.sin(lon2)
            val z = a * Math.sin(lat1) + b * Math.sin(lat2)

            val lat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)))
            val lon = Math.toDegrees(Math.atan2(y, x))
            pts.add(newGeoPoint(lat, lon))
        }
        return pts
    }

    private fun newGeoPoint(lat: Double, lon: Double): GeoPoint {
        return GeoPoint(lat, lon)
    }
}

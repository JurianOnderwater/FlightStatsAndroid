package com.example.flightstats;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a geodesic (great-circle) arc between two GeoPoints.
 * This creates the curved flight path appearance.
 */
public class GeodesicHelper {

    /**
     * Returns a list of GeoPoints forming a smooth great-circle arc.
     * @param from  origin GeoPoint
     * @param to    destination GeoPoint
     * @param steps number of intermediate points (higher = smoother curve)
     */
    public static List<GeoPoint> greatCircleArc(GeoPoint from, GeoPoint to, int steps) {
        List<GeoPoint> pts = new ArrayList<>();

        double lat1 = Math.toRadians(from.getLatitude());
        double lon1 = Math.toRadians(from.getLongitude());
        double lat2 = Math.toRadians(to.getLatitude());
        double lon2 = Math.toRadians(to.getLongitude());

        // Angular distance between two points
        double d = 2 * Math.asin(Math.sqrt(
                Math.pow(Math.sin((lat2 - lat1) / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lon2 - lon1) / 2), 2)));

        if (d < 0.001) {
            // Points are essentially the same — just return straight line
            pts.add(from);
            pts.add(to);
            return pts;
        }

        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            double A = Math.sin((1 - f) * d) / Math.sin(d);
            double B = Math.sin(f * d) / Math.sin(d);

            double x = A * Math.cos(lat1) * Math.cos(lon1) + B * Math.cos(lat2) * Math.cos(lon2);
            double y = A * Math.cos(lat1) * Math.sin(lon1) + B * Math.cos(lat2) * Math.sin(lon2);
            double z = A * Math.sin(lat1) + B * Math.sin(lat2);

            double lat = Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y)));
            double lon = Math.toDegrees(Math.atan2(y, x));
            pts.add(new GeoPoint(lat, lon));
        }
        return pts;
    }
}

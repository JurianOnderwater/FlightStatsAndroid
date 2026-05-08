package com.example.flightstats;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;

/**
 * Draws a flight route as a tapered spindle:
 *  - width is 0 at both endpoints
 *  - width peaks at the midpoint, scaled by flight frequency
 *  - color is the theme primary color with alpha
 */
public class TaperedRouteOverlay extends Overlay {

    // Max half-width in pixels at frequency == 1.
    // Scales linearly with frequency.
    private static final float BASE_MAX_HALF_WIDTH_DP = 3.5f;

    private final List<GeoPoint> arcPoints;
    private final int frequency;
    private final Paint paint;
    private final float maxHalfWidth;

    public TaperedRouteOverlay(List<GeoPoint> arcPoints, int frequency, int color, float density) {
        this.arcPoints = arcPoints;
        this.frequency = frequency;
        this.maxHalfWidth = BASE_MAX_HALF_WIDTH_DP * density * (1f + 0.6f * (frequency - 1));
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setColor(color);
        this.paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas canvas, Projection projection) {
        if (arcPoints == null || arcPoints.size() < 2) return;

        int n = arcPoints.size();
        android.graphics.Point[] screenPts = new android.graphics.Point[n];
        for (int i = 0; i < n; i++) {
            screenPts[i] = new android.graphics.Point();
            projection.toPixels(arcPoints.get(i), screenPts[i]);
        }

        // Build the left and right offset paths
        PointF[] left  = new PointF[n];
        PointF[] right = new PointF[n];

        for (int i = 0; i < n; i++) {
            // Sine envelope: 0 at endpoints, 1 at midpoint
            double t = (double) i / (n - 1);
            float halfW = maxHalfWidth * (float) Math.sin(Math.PI * t);

            // Tangent direction at this point
            float tx, ty;
            if (i == 0) {
                tx = screenPts[1].x - screenPts[0].x;
                ty = screenPts[1].y - screenPts[0].y;
            } else if (i == n - 1) {
                tx = screenPts[n-1].x - screenPts[n-2].x;
                ty = screenPts[n-1].y - screenPts[n-2].y;
            } else {
                tx = screenPts[i+1].x - screenPts[i-1].x;
                ty = screenPts[i+1].y - screenPts[i-1].y;
            }

            // Normalize
            float len = (float) Math.sqrt(tx * tx + ty * ty);
            if (len < 0.001f) { len = 1f; }
            tx /= len; ty /= len;

            // Perpendicular (rotate 90°)
            float nx = -ty;
            float ny =  tx;

            left[i]  = new PointF(screenPts[i].x + nx * halfW, screenPts[i].y + ny * halfW);
            right[i] = new PointF(screenPts[i].x - nx * halfW, screenPts[i].y - ny * halfW);
        }

        // Build the closed filled polygon (left side forward, right side backward)
        Path path = new Path();
        path.moveTo(left[0].x, left[0].y);
        for (int i = 1; i < n; i++) path.lineTo(left[i].x,  left[i].y);
        for (int i = n - 1; i >= 0; i--) path.lineTo(right[i].x, right[i].y);
        path.close();

        canvas.drawPath(path, paint);
    }
}

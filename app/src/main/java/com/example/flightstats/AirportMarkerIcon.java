package com.example.flightstats;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * Draws a Material 3 Expressive "9-sided cookie" airport marker with the IATA code.
 * The shape matches MaterialShapes.COOKIE_9_SIDED — a regular 9-gon with
 * rounded corners (quadratic Bézier at each vertex, ~18% of radius).
 */
public class AirportMarkerIcon {

    public static Drawable create(Context ctx, String iataCode) {
        int sizeDp = 44; // slightly larger to give cookie shape room to breathe
        int dp = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp,
                ctx.getResources().getDisplayMetrics());

        Bitmap bmp = Bitmap.createBitmap(dp, dp, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true);
        int colorPrimary = tv.data;
        ctx.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, tv, true);
        int colorOnPrimary = tv.data;

        float cx = dp / 2f;
        float cy = dp / 2f;
        float R  = dp / 2f * 0.88f; // slight inset so corners don't clip

        // Build the 9-sided cookie path
        Path cookie = buildCookie9Path(cx, cy, R);

        // Fill
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(colorPrimary);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(cookie, bgPaint);

        // Subtle inner highlight ring
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(0x28FFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp * 0.055f);
        canvas.drawPath(cookie, strokePaint);

        // IATA text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(colorOnPrimary);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp * 0.28f);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(iataCode, cx, textY, textPaint);

        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    /**
     * Builds a 9-sided rounded polygon (cookie shape) path centred at (cx,cy).
     * Rounded corners use quadratic Bézier curves at each vertex — the same
     * geometry as MaterialShapes.COOKIE_9_SIDED.
     */
    static Path buildCookie9Path(float cx, float cy, float R) {
        int n = 9;
        float cornerFraction = 0.20f; // how much of the vertex is rounded

        // Pre-compute normalised vertex directions
        float[] vx = new float[n];
        float[] vy = new float[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n - Math.PI / 2;
            vx[i] = (float) Math.cos(a);
            vy[i] = (float) Math.sin(a);
        }

        Path path = new Path();

        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n;
            int next = (i + 1) % n;

            // Direction from this vertex toward the previous vertex
            float dxP = vx[prev] - vx[i], dyP = vy[prev] - vy[i];
            float lenP = (float) Math.sqrt(dxP * dxP + dyP * dyP);
            dxP /= lenP; dyP /= lenP;

            // Direction from this vertex toward the next vertex
            float dxN = vx[next] - vx[i], dyN = vy[next] - vy[i];
            float lenN = (float) Math.sqrt(dxN * dxN + dyN * dyN);
            dxN /= lenN; dyN /= lenN;

            // Corner arc endpoints
            float startX = cx + R * (vx[i] + dxP * cornerFraction);
            float startY = cy + R * (vy[i] + dyP * cornerFraction);
            float endX   = cx + R * (vx[i] + dxN * cornerFraction);
            float endY   = cy + R * (vy[i] + dyN * cornerFraction);
            float cpX    = cx + R * vx[i]; // Bézier control = the sharp vertex
            float cpY    = cy + R * vy[i];

            if (i == 0) path.moveTo(startX, startY);
            else        path.lineTo(startX, startY);

            path.quadTo(cpX, cpY, endX, endY);
        }

        path.close();
        return path;
    }
}

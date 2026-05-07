package com.example.flightstats;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EdgeToEdgePieView extends View {

    public static class Slice {
        public float value;
        public int color;
        public String label;
        public String subLabel;

        public Slice(float value, int color, String label, String subLabel) {
            this.value = value;
            this.color = color;
            this.label = label;
            this.subLabel = subLabel;
        }
    }

    private List<Slice> slices = new ArrayList<>();
    private float total = 0f;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EdgeToEdgePieView(Context context) {
        super(context);
        init();
    }

    public EdgeToEdgePieView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));

        subTextPaint.setColor(Color.argb(200, 255, 255, 255));
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public void setSlices(List<Slice> slices) {
        this.slices = slices;
        this.total = 0f;
        for (Slice s : slices) total += s.value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (slices.isEmpty() || total <= 0) return;

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // Radius needs to be large enough to cover the corners of the rectangle
        float rOut = (float) Math.hypot(cx, cy);
        // Inner radius for the hollow middle
        float rIn = Math.min(w, h) * 0.28f;
        
        RectF ovalOut = new RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut);
        RectF ovalIn = new RectF(cx - rIn, cy - rIn, cx + rIn, cy + rIn);

        float currentAngle = -90f; // Start at top

        for (Slice s : slices) {
            float sweepAngle = (s.value / total) * 360f;

            // Draw slice using a Path to create a donut
            paint.setColor(s.color);
            paint.setStyle(Paint.Style.FILL);
            
            Path path = new Path();
            if (sweepAngle >= 359.9f) {
                // If it's a full circle, just draw two paths with FILL_TYPE EVEN_ODD
                path.setFillType(Path.FillType.EVEN_ODD);
                path.addCircle(cx, cy, rOut, Path.Direction.CW);
                path.addCircle(cx, cy, rIn, Path.Direction.CW);
            } else {
                path.arcTo(ovalOut, currentAngle, sweepAngle);
                path.arcTo(ovalIn, currentAngle + sweepAngle, -sweepAngle);
                path.close();
            }
            canvas.drawPath(path, paint);

            // Draw text
            if (sweepAngle > 15f) { // Only draw text if slice is reasonably large
                float midAngle = currentAngle + sweepAngle / 2f;
                double rad = Math.toRadians(midAngle);

                // Position text exactly halfway between the inner hole and the nearest edge constraint
                // Since the pie fills a rectangle, the visual distance varies. We'll use a balanced radius.
                float textRadius = rIn + (Math.min(cx, cy) - rIn) * 0.55f; 
                
                float tx = cx + (float) (Math.cos(rad) * textRadius);
                float ty = cy + (float) (Math.sin(rad) * textRadius);

                // Dynamic text size based on view dimensions
                float primarySize = Math.max(16f, Math.min(w, h) * 0.05f);
                textPaint.setTextSize(primarySize);
                subTextPaint.setTextSize(primarySize * 0.65f);

                // Draw main label (Flag + Code)
                canvas.drawText(s.label, tx, ty, textPaint);

                // Draw sub label (Percentage)
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText(s.subLabel, tx, ty + fm.descent - fm.ascent, subTextPaint);
            }

            currentAngle += sweepAngle;
        }
    }
}

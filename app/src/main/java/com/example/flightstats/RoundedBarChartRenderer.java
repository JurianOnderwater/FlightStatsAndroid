package com.example.flightstats;

import android.graphics.Canvas;
import android.graphics.RectF;

import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.buffer.BarBuffer;
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.renderer.BarChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

/**
 * Custom BarChartRenderer that draws bars as pill/stadium shapes (fully rounded tops).
 * Matches the Fitbit-style rounded bar chart look.
 */
public class RoundedBarChartRenderer extends BarChartRenderer {

    private final float mRadius;

    public RoundedBarChartRenderer(BarDataProvider chart, ChartAnimator animator,
                                   ViewPortHandler viewPortHandler, float radiusPx) {
        super(chart, animator, viewPortHandler);
        this.mRadius = radiusPx;
    }

    @Override
    protected void drawDataSet(Canvas c, IBarDataSet dataSet, int index) {
        Transformer trans = mChart.getTransformer(dataSet.getAxisDependency());

        float phaseX = mAnimator.getPhaseX();
        float phaseY = mAnimator.getPhaseY();

        BarBuffer buffer = mBarBuffers[index];
        buffer.setPhases(phaseX, phaseY);
        buffer.setDataSet(index);
        buffer.setBarWidth(mChart.getBarData().getBarWidth());
        buffer.feed(dataSet);
        trans.pointValuesToPixel(buffer.buffer);

        boolean isSingleColor = dataSet.getColors().size() == 1;
        if (isSingleColor) mRenderPaint.setColor(dataSet.getColor());

        for (int j = 0, pos = 0; j < buffer.size(); j += 4, pos++) {
            if (!mViewPortHandler.isInBoundsLeft(buffer.buffer[j + 2])) continue;
            if (!mViewPortHandler.isInBoundsRight(buffer.buffer[j])) break;

            if (!isSingleColor) {
                mRenderPaint.setColor(dataSet.getColor(pos / dataSet.getStackSize()));
            }

            float left   = buffer.buffer[j];
            float top    = buffer.buffer[j + 1];
            float right  = buffer.buffer[j + 2];
            float bottom = buffer.buffer[j + 3];

            // Use full radius for stadium/pill top, flat bottom
            float barWidth = right - left;
            float r = Math.min(mRadius, barWidth / 2f);

            // Draw body with rounded top only (pill cap on top, flat at baseline)
            RectF rect = new RectF(left, top, right, bottom);
            c.drawRoundRect(rect, r, r, mRenderPaint);

            // Fill the bottom half to make it square (only round the top)
            if (bottom - top > r) {
                RectF bottomRect = new RectF(left, bottom - r, right, bottom);
                c.drawRect(bottomRect, mRenderPaint);
            }
        }
    }
}

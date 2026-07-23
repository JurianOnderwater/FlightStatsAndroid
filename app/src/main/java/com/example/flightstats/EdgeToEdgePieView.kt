package com.example.flightstats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class EdgeToEdgePieView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    class Slice(
        val value: Float,
        val color: Int,
        val label: String,
        val subLabel: String
    )

    private var slices = listOf<Slice>()
    private var total = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun setSlices(slicesList: List<Slice>) {
        slices = slicesList
        total = slicesList.sumOf { it.value.toDouble() }.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty() || total <= 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val rOut = Math.hypot(cx.toDouble(), cy.toDouble()).toFloat()
        val rIn = Math.min(w, h) * 0.28f

        val ovalOut = RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut)
        val ovalIn = RectF(cx - rIn, cy - rIn, cx + rIn, cy + rIn)

        var currentAngle = -90f

        for (s in slices) {
            val sweepAngle = (s.value / total) * 360f

            paint.color = s.color
            paint.style = Paint.Style.FILL

            val path = Path()
            if (sweepAngle >= 359.9f) {
                path.fillType = Path.FillType.EVEN_ODD
                path.addCircle(cx, cy, rOut, Path.Direction.CW)
                path.addCircle(cx, cy, rIn, Path.Direction.CW)
            } else {
                path.arcTo(ovalOut, currentAngle, sweepAngle)
                path.arcTo(ovalIn, currentAngle + sweepAngle, -sweepAngle)
                path.close()
            }
            canvas.drawPath(path, paint)

            if (sweepAngle > 15f) {
                val midAngle = currentAngle + sweepAngle / 2f
                val rad = Math.toRadians(midAngle.toDouble())

                val textRadius = rIn + (Math.min(cx, cy) - rIn) * 0.55f

                val tx = cx + (Math.cos(rad) * textRadius).toFloat()
                val ty = cy + (Math.sin(rad) * textRadius).toFloat()

                val primarySize = Math.max(16f, Math.min(w, h) * 0.05f)
                textPaint.textSize = primarySize
                subTextPaint.textSize = primarySize * 0.65f

                canvas.drawText(s.label, tx, ty, textPaint)

                val fm = textPaint.fontMetrics
                canvas.drawText(s.subLabel, tx, ty + fm.descent - fm.ascent, subTextPaint)
            }

            currentAngle += sweepAngle
        }
    }
}

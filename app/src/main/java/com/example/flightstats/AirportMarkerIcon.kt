package com.example.flightstats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue

object AirportMarkerIcon {

    fun create(ctx: Context, iataCode: String, primaryColor: Int, onPrimaryColor: Int): Drawable {
        val sizeDp = 44
        val dp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()

        val bmp = Bitmap.createBitmap(dp, dp, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cx = dp / 2f
        val cy = dp / 2f
        val R = dp / 2f * 0.88f

        // Build the 9-sided cookie path
        val cookie = buildCookie9Path(cx, cy, R)

        // Fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(cookie, bgPaint)

        // Subtle inner highlight ring
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x28FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = dp * 0.055f
        }
        canvas.drawPath(cookie, strokePaint)

        // IATA text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = dp * 0.28f
        }

        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(iataCode, cx, textY, textPaint)

        return BitmapDrawable(ctx.resources, bmp)
    }

    private fun buildCookie9Path(cx: Float, cy: Float, R: Float): Path {
        val n = 9
        val cornerFraction = 0.20f

        val vx = FloatArray(n)
        val vy = FloatArray(n)
        for (i in 0 until n) {
            val a = 2 * Math.PI * i / n - Math.PI / 2
            vx[i] = Math.cos(a).toFloat()
            vy[i] = Math.sin(a).toFloat()
        }

        val path = Path()

        for (i in 0 until n) {
            val prev = (i + n - 1) % n
            val next = (i + 1) % n

            var dxP = vx[prev] - vx[i]
            var dyP = vy[prev] - vy[i]
            val lenP = Math.sqrt((dxP * dxP + dyP * dyP).toDouble()).toFloat()
            dxP /= lenP
            dyP /= lenP

            var dxN = vx[next] - vx[i]
            var dyN = vy[next] - vy[i]
            val lenN = Math.sqrt((dxN * dxN + dyN * dyN).toDouble()).toFloat()
            dxN /= lenN
            dyN /= lenN

            val startX = cx + R * (vx[i] + dxP * cornerFraction)
            val startY = cy + R * (vy[i] + dyP * cornerFraction)
            val endX = cx + R * (vx[i] + dxN * cornerFraction)
            val endY = cy + R * (vy[i] + dyN * cornerFraction)
            val cpX = cx + R * vx[i]
            val cpY = cy + R * vy[i]

            if (i == 0) {
                path.moveTo(startX, startY)
            } else {
                path.lineTo(startX, startY)
            }

            path.quadTo(cpX, cpY, endX, endY)
        }

        path.close()
        return path
    }
}

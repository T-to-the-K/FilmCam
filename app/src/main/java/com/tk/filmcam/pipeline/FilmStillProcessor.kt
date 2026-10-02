package com.tk.filmcam.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.tk.filmcam.film.FilmCamera
import kotlin.math.abs
import kotlin.random.Random

/**
 * Applies a film look to a captured still on the CPU.
 *
 * Mirrors the GLES preview shader in [com.tk.filmcam.gl.FilmShader] so the saved
 * photo matches what the user saw. Preview-only artefacts (halation) are
 * approximated with a cheap highlight lift rather than a true bloom pass.
 */
object FilmStillProcessor {

    fun apply(source: Bitmap, film: FilmCamera): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)

        // 1. tonality + colour, as a single ColorMatrix chain
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val matrix = buildColorMatrix(film)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(output, 0f, 0f, paint)
        paint.colorFilter = null

        // 2. halation approximation: lift bright areas toward the warm bias
        if (film.halation > 0.001f) {
            val lift = (film.halation * 26f).toInt().coerceIn(0, 64)
            val warm = Color.argb(lift, 255, 210, 170)
            val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
            glowPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            glowPaint.color = warm
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)
        }

        // 3. grain: monochrome noise, stronger in midtones
        if (film.grain > 0.001f) {
            drawGrain(canvas, width, height, film.grain)
        }

        // 4. vignette
        if (film.vignette > 0.001f) {
            drawVignette(canvas, width, height, film.vignette)
        }

        return output
    }

    private fun buildColorMatrix(film: FilmCamera): ColorMatrix {
        val c = film.contrast
        val offset = 0.5f - 0.5f * c + film.fade * (1f - 0.5f)
        val sat = film.saturation

        val m = ColorMatrix()
        m.setSaturation(sat)

        val gain = ColorMatrix(
            floatArrayOf(
                film.rgbGain[0], 0f, 0f, 0f,
                0f, film.rgbGain[1], 0f, 0f,
                0f, 0f, film.rgbGain[2], 0f,
                0f, 0f, 0f, 1f
            )
        )
        m.postConcat(gain)

        val warmthShift = film.warmth * 0.08f
        val warmth = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                warmthShift, -warmthShift, 0f, 1f
            )
        )
        m.postConcat(warmth)

        val contrast = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f,
                0f, c, 0f, 0f,
                0f, 0f, c, 0f,
                offset, offset, offset, 1f
            )
        )
        m.postConcat(contrast)

        return m
    }

    private fun drawGrain(canvas: Canvas, width: Int, height: Int, strength: Float) {
        val random = Random(System.nanoTime())
        val paint = Paint()
        val step = if (strength > 0.3f) 2 else 1
        val maxAlpha = (strength * 150f).toInt().coerceIn(0, 190)

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val n = random.nextFloat() - 0.5f
                val a = (abs(n) * 2f * maxAlpha).toInt().coerceIn(0, 255)
                if (a <= 1) continue
                val v = if (n > 0f) 255 else 0
                paint.color = Color.argb(a, v, v, v)
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
        val radius = maxOf(width, height) * 0.75f
        val centerX = width / 2f
        val centerY = height / 2f
        val alpha = (strength * 150f).toInt().coerceIn(0, 200)

        val shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
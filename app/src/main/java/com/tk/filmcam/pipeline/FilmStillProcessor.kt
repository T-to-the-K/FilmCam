package com.tk.filmcam.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.tk.filmcam.film.FilmCamera
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Applies a film look to a captured still on the CPU.
 *
 * Mirrors the GLES shader in [com.tk.filmcam.gl.FilmShader] so the saved photo
 * matches the intent of the preview. Order is fixed and matters: tonality and
 * colour first, then bloom, then grain, then vignette. Grain applied before the
 * contrast curve gets crushed by it and stops reading as grain.
 */
object FilmStillProcessor {

    private const val BLOOM_DOWNSCALE = 6
    private const val BLOOM_PASSES = 5
    private const val GRAIN_DOWNSCALE = 4

    fun apply(source: Bitmap, film: FilmCamera): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. tonality + colour in one pass. The filter must be applied while
        //    drawing *source*; drawing the destination onto its own canvas is a
        //    no-op and was why every look came out ungraded.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(buildColorMatrix(film))
        canvas.drawBitmap(source, 0f, 0f, paint)
        paint.colorFilter = null

        // 2. halation: extract highlights, blur them, screen them back over.
        if (film.halation > 0.001f) {
            val bloom = buildHalation(output, film)
            if (bloom != null) {
                val glow = Paint(Paint.FILTER_BITMAP_FLAG)
                glow.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                glow.alpha = (film.halation * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawBitmap(bloom, 0f, 0f, glow)
                bloom.recycle()
            }
        }

        // 3. grain, strongest in the midtones like real emulsion
        if (film.grain > 0.001f) {
            drawGrain(canvas, output, film.grain)
        }

        // 4. vignette
        if (film.vignette > 0.001f) {
            drawVignette(canvas, width, height, film.vignette)
        }

        return output
    }

    private fun buildColorMatrix(film: FilmCamera): ColorMatrix {
        val m = ColorMatrix()

        m.setSaturation(film.saturation)

        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    film.rgbGain[0], 0f, 0f, 0f,
                    0f, film.rgbGain[1], 0f, 0f,
                    0f, 0f, film.rgbGain[2], 0f,
                    0f, 0f, 0f, 1f
                )
            )
        )

        val warmth = film.warmth * 0.10f
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    warmth, -warmth, 0f, 1f
                )
            )
        )

        // contrast around a 0.5 pivot
        val c = film.contrast
        m.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f,
                    0f, c, 0f, 0f,
                    0f, 0f, c, 0f,
                    0.5f - 0.5f * c, 0.5f - 0.5f * c, 0.5f - 0.5f * c, 1f
                )
            )
        )

        // fade lifts the blacks without touching the highlights:
        // out' = out*(1-fade) + fade, per channel
        val f = film.fade.coerceIn(0f, 0.6f)
        if (f > 0.001f) {
            m.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f - f, 0f, 0f, 0f,
                        0f, 1f - f, 0f, 0f,
                        0f, 0f, 1f - f, 0f,
                        f, f, f, 1f
                    )
                )
            )
        }

        return m
    }

    /**
     * Highlight-only bloom. Downscaled hard, thresholded to the bright end,
     * then re-expanded with several offset passes so bilinear filtering does the
     * blurring. Cheap enough to run on a 12 MP frame without stalling the save.
     */
    private fun buildHalation(source: Bitmap, film: FilmCamera): Bitmap? {
        val bw = max(1, source.width / BLOOM_DOWNSCALE)
        val bh = max(1, source.height / BLOOM_DOWNSCALE)

        val small = Bitmap.createScaledBitmap(source, bw, bh, true)
        val pixels = IntArray(bw * bh)
        small.getPixels(pixels, 0, bw, 0, 0, bw, bh)

        val threshold = 190f
        var lit = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f
            if (lum > threshold) {
                val k = ((lum - threshold) / (255f - threshold)).coerceIn(0f, 1f)
                val a = (k * 255f).roundToInt().coerceIn(0, 255)
                pixels[i] = Color.argb(a, 255, 226, 196)
                lit++
            } else {
                pixels[i] = 0
            }
        }
        small.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        if (lit == 0) {
            small.recycle()
            return null
        }

        val bloom = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val bc = Canvas(bloom)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val spread = max(1, min(source.width, source.height) / 24)
        val offsets = arrayOf(
            0 to 0,
            spread to 0,
            -spread to 0,
            0 to spread,
            0 to -spread
        )
        for ((dx, dy) in offsets) {
            bc.drawBitmap(
                small,
                null,
                RectF(
                    dx.toFloat(),
                    dy.toFloat(),
                    source.width + dx.toFloat(),
                    source.height + dy.toFloat()
                ),
                p
            )
        }
        small.recycle()
        return bloom
    }

    /**
     * Grain as a quarter-resolution noise tile scaled back up, so the clumping
     * is 4 px like real emulsion and the whole pass is one draw call instead of
     * one call per pixel.
     */
    private fun drawGrain(canvas: Canvas, graded: Bitmap, strength: Float) {
        val gw = max(1, graded.width / GRAIN_DOWNSCALE)
        val gh = max(1, graded.height / GRAIN_DOWNSCALE)

        val luma = IntArray(gw * gh)
        val thumb = Bitmap.createScaledBitmap(graded, gw, gh, true)
        val thumbPixels = IntArray(gw * gh)
        thumb.getPixels(thumbPixels, 0, gw, 0, 0, gw, gh)
        thumb.recycle()

        val random = Random(System.nanoTime())
        val peak = (strength * 165f).roundToInt().coerceIn(4, 200)

        for (i in thumbPixels.indices) {
            val c = thumbPixels[i]
            val l = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) / 255f
            // film grain peaks in the midtones, falls off in both ends
            val weight = 1f - abs(l - 0.5f) * 1.7f
            if (weight <= 0.02f) {
                luma[i] = 0
                continue
            }
            val n = (random.nextFloat() + random.nextFloat() - 1f)
            val a = (abs(n) * peak * weight).roundToInt().coerceIn(0, 255)
            if (a <= 1) continue
            val v = if (n > 0f) 255 else 0
            luma[i] = Color.argb(a, v, v, v)
        }

        val tile = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        tile.setPixels(luma, 0, gw, 0, 0, gw, gh)

        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawRect(
            0f, 0f, graded.width.toFloat(), graded.height.toFloat(), p
        )
        p.shader = null
        tile.recycle()
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = sqrt((width * width + height * height).toDouble()).toFloat() / 2f
        val alpha = (strength * 170f).roundToInt().coerceIn(0, 210)

        val shader = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

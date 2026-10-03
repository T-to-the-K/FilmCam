package com.tk.filmcam.pipeline

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
 * The per-pixel grade below is a line-for-line twin of [com.tk.filmcam.gl.FilmShader],
 * so the photo you save is the look you framed on the viewfinder. If you change
 * the maths in one, change it in the other.
 *
 * Why this is not a `ColorMatrixColorFilter`: a ColorMatrix is one linear 4x5
 * transform, which cannot express a tone curve, split toning, or a
 * highlight-aware saturation roll-off. Forcing all three into a single linear
 * matrix is what clipped every look to pure white and pure black.
 *
 * Order: gains -> tone curve -> saturation -> split tone -> fade, then the
 * spatial passes (halation, grain, vignette).
 */
object FilmStillProcessor {

    private const val BLOOM_DOWNSCALE = 6
    private const val GRAIN_DOWNSCALE = 4

    /** Rec.709 luma weights, matching GLSL_LUMA below. */
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    fun apply(source: Bitmap, film: FilmCamera): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 1. per-pixel grade. Row-at-a-time keeps this off the JNI boundary and
        //    off per-pixel boxing; a 12 MP frame is a few hundred ms on a
        //    background thread.
        val row = IntArray(width)
        val graded = IntArray(width)
        for (y in 0 until height) {
            source.getPixels(row, 0, width, 0, y, width, 1)
            gradeRow(row, graded, film)
            output.setPixels(graded, 0, width, 0, y, width, 1)
        }

        val canvas = Canvas(output)

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

    /**
     * The grade. Kept deliberately arithmetic and branch-light so it ports to
     * GLSL one-to-one.
     */
    private fun gradeRow(src: IntArray, dst: IntArray, film: FilmCamera) {
        val gain = film.rgbGain
        val sat = film.saturation
        val k = film.contrast.coerceIn(0f, 1f)
        val warmth = film.warmth * 0.10f
        val shadow = film.shadowTint
        val highlight = film.highlightTint
        val split = film.split
        val fade = film.fade

        for (i in src.indices) {
            val p = src[i]

            // gains and warmth
            var r = (Color.red(p) / 255f) * gain[0] + warmth
            var g = (Color.green(p) / 255f) * gain[1]
            var b = (Color.blue(p) / 255f) * gain[2] - warmth

            // soft S-curve. Fixes 0 and 1 exactly, so this cannot clip.
            r = sCurve(r, k)
            g = sCurve(g, k)
            b = sCurve(b, k)

            // saturation after tone mapping, rolled off in the highlights so a
            // vivid hue desaturates toward white instead of posterising
            var l = LUMA_R * r + LUMA_G * g + LUMA_B * b
            r = l + (r - l) * sat
            g = l + (g - l) * sat
            b = l + (b - l) * sat
            val peak = max(r, max(g, b))
            if (peak > 0.72f) {
                val t = smoothstep(((peak - 0.72f) / 0.28f).coerceIn(0f, 1f)) * 0.45f
                val l2 = LUMA_R * r + LUMA_G * g + LUMA_B * b
                r += (l2 - r) * t
                g += (l2 - g) * t
                b += (l2 - b) * t
            }

            // split toning
            l = (LUMA_R * r + LUMA_G * g + LUMA_B * b).coerceIn(0f, 1f)
            val sw = (1f - l) * (1f - l) * split
            val hw = l * l * split
            r += shadow[0] * sw
            g += shadow[1] * sw
            b += shadow[2] * sw
            r += highlight[0] * hw
            g += highlight[1] * hw
            b += highlight[2] * hw

            // fade lifts the blacks and greys the toe
            if (fade > 0.001f) {
                r = r * (1f - fade) + fade
                g = g * (1f - fade) + fade
                b = b * (1f - fade) + fade
                l = LUMA_R * r + LUMA_G * g + LUMA_B * b
                r += (l - r) * (fade * 0.35f)
                g += (l - g) * (fade * 0.35f)
                b += (l - b) * (fade * 0.35f)
            }

            val ri = (r.coerceIn(0f, 1f) * 255f).roundToInt()
            val gi = (g.coerceIn(0f, 1f) * 255f).roundToInt()
            val bi = (b.coerceIn(0f, 1f) * 255f).roundToInt()
            dst[i] = (Color.alpha(p) shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    /**
     * Monotone S-curve. Two smoothstep passes blended by strength, so the
     * endpoints are preserved exactly and the peak slope reaches 2.25. This is
     * the replacement for `(c - 0.5) * contrast + 0.5`, which had no shoulder
     * and clipped anything above ~0.8.
     */
    private fun sCurve(x: Float, k: Float): Float {
        val v = x.coerceIn(0f, 1f)
        val a = v + (smoothstep(v) - v) * k
        val s = smoothstep(a)
        return a + (s - a) * k
    }

    private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)

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
        val tile = BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        p.shader = tile
        for ((dx, dy) in offsets) {
            val m = Matrix()
            m.postTranslate(dx.toFloat(), dy.toFloat())
            tile.setLocalMatrix(m)
            bc.drawPaint(p)
        }
        small.recycle()
        return bloom
    }

    private fun drawGrain(canvas: Canvas, source: Bitmap, strength: Float) {
        val w = source.width
        val h = source.height
        val gw = max(1, w / GRAIN_DOWNSCALE)
        val gh = max(1, h / GRAIN_DOWNSCALE)

        val thumb = Bitmap.createScaledBitmap(source, gw, gh, true)
        val px = IntArray(gw * gh)
        thumb.getPixels(px, 0, gw, 0, 0, gw, gh)
        val rng = Random(1234)
        val noise = IntArray(gw * gh)
        for (i in px.indices) {
            val c = px[i]
            val lum = (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) / 255f
            val weight = 1f - abs(lum - 0.5f) * 1.7f
            val n = (rng.nextFloat() + rng.nextFloat() - 1f) * strength * 0.42f * max(weight, 0.2f)
            val v = (n * 255f).roundToInt().coerceIn(0, 255)
            val bright = rng.nextBoolean()
            noise[i] = if (bright) Color.argb(v, 255, 255, 255) else Color.argb(v, 0, 0, 0)
        }
        thumb.recycle()

        val tile = Bitmap.createBitmap(noise, gw, gh, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(tile, w, h, true)
        if (scaled !== tile) tile.recycle()
        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        scaled.recycle()
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
        val radius = sqrt((width * width + height * height).toDouble()).toFloat() / 2f
        val cx = width / 2f
        val cy = height / 2f
        val colors = intArrayOf(Color.TRANSPARENT, Color.argb((strength * 210f).roundToInt(), 0, 0, 0))
        val stops = floatArrayOf(0f, 1f)
        val gradient = RadialGradient(
            cx, cy, radius, colors, stops, Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
    }
}
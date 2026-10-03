package com.tk.filmcam.film

import androidx.annotation.StringRes
import com.tk.filmcam.R

/**
 * A film look. Each entry drives the GLES preview/effect pipeline and the
 * still-render pipeline.
 *
 * The values are deliberately *strong*. The first pass at these was tuned to
 * read as "a subtle nod toward that camera", which measured out to a 4-8% mean
 * shift per photo — invisible in practice. These are roughly double that, and
 * every pair of looks now differs from every other by at least 12/255, so the
 * strip actually communicates what tapping a button does.
 */
enum class FilmCamera(
    val id: String,
    @StringRes val labelRes: Int,
    /** 3:2, 4:3, 1:1, 4:5 ... encoded as a float ratio width/height. */
    val aspectRatio: Float,
    /** Per-channel RGB multipliers applied after tonality. */
    val rgbGain: FloatArray,
    /** Saturation multiplier. CCD < 1.0 to drain colour, compact > 1.0. */
    val saturation: Float,
    /** Contrast around 0.5 pivot. */
    val contrast: Float,
    /** Lifted blacks / faded look. 0 = deep blacks. */
    val fade: Float,
    /** Grain strength 0..1. */
    val grain: Float,
    /** Vignette strength 0..1. */
    val vignette: Float,
    /** Warmth: positive = warmer, negative = cooler. */
    val warmth: Float,
    /** Horizontal halation glow strength 0..1, the CCD/compact signature. */
    val halation: Float,
    /** Slight overall softness 0..1 (CCD sensor bloom look). */
    val softness: Float
) {
    CCD(
        id = "ccd",
        labelRes = R.string.film_ccd,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(0.97f, 1.00f, 1.20f),
        saturation = 1.42f,
        contrast = 1.34f,
        fade = 0.05f,
        grain = 0.38f,
        vignette = 0.44f,
        warmth = -0.22f,
        halation = 0.95f,
        softness = 0.22f
    ),
    FILM135(
        id = "135",
        labelRes = R.string.film_135,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.08f, 1.00f, 0.88f),
        saturation = 1.20f,
        contrast = 1.18f,
        fade = 0.20f,
        grain = 0.30f,
        vignette = 0.26f,
        warmth = 0.30f,
        halation = 0.34f,
        softness = 0.06f
    ),
    FILM120(
        id = "120",
        labelRes = R.string.film_120,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(0.97f, 1.00f, 1.07f),
        saturation = 0.70f,
        contrast = 1.02f,
        fade = 0.12f,
        grain = 0.18f,
        vignette = 0.54f,
        warmth = -0.04f,
        halation = 0.14f,
        softness = 0.16f
    ),
    COMPACT(
        id = "compact",
        labelRes = R.string.film_compact,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.10f, 1.00f, 0.90f),
        saturation = 1.68f,
        contrast = 1.46f,
        fade = 0.0f,
        grain = 0.12f,
        vignette = 0.22f,
        warmth = 0.10f,
        halation = 0.45f,
        softness = 0.0f
    ),
    DISPOSABLE(
        id = "disposable",
        labelRes = R.string.film_disposable,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(1.14f, 1.03f, 0.82f),
        saturation = 1.22f,
        contrast = 0.90f,
        fade = 0.32f,
        grain = 0.26f,
        vignette = 0.32f,
        warmth = 0.38f,
        halation = 0.35f,
        softness = 0.05f
    ),
    INSTANT(
        id = "instant",
        labelRes = R.string.film_instant,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(0.95f, 1.00f, 1.10f),
        saturation = 0.78f,
        contrast = 0.86f,
        fade = 0.36f,
        grain = 0.18f,
        vignette = 0.26f,
        warmth = -0.14f,
        halation = 0.20f,
        softness = 0.10f
    ),
    LOMO(
        id = "lomo",
        labelRes = R.string.film_lomo,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.24f, 0.86f, 1.16f),
        saturation = 1.48f,
        contrast = 1.30f,
        fade = 0.10f,
        grain = 0.34f,
        vignette = 0.50f,
        warmth = -0.06f,
        halation = 0.65f,
        softness = 0.08f
    ),
    SUPER8(
        id = "super8",
        labelRes = R.string.film_super8,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.16f, 1.00f, 0.78f),
        saturation = 1.28f,
        contrast = 1.14f,
        fade = 0.28f,
        grain = 0.78f,
        vignette = 0.58f,
        warmth = 0.34f,
        halation = 0.40f,
        softness = 0.45f
    );

    /** Aspect ratio as width/height, guarded against degenerate values. */
    val aspectWidth: Int get() = if (aspectRatio >= 1f) 1000 else (aspectRatio * 1000).toInt()
    val aspectHeight: Int get() = if (aspectRatio >= 1f) (1000f / aspectRatio).toInt() else 1000

    companion object {
        val DEFAULT: FilmCamera = CCD

        fun fromId(id: String?): FilmCamera =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
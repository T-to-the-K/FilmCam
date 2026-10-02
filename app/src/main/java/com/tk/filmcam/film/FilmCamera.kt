package com.tk.filmcam.film

import androidx.annotation.StringRes
import com.tk.filmcam.R

/**
 * A film look. Each entry drives the GLES preview/effect pipeline and the
 * still-render pipeline.
 *
 * Values are deliberately conservative: they are tuned to read as "that kind
 * of camera" rather than as a heavy filter.
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
        rgbGain = floatArrayOf(1.06f, 0.99f, 0.94f),
        saturation = 1.18f,
        contrast = 1.14f,
        fade = 0.04f,
        grain = 0.30f,
        vignette = 0.34f,
        warmth = 0.05f,
        halation = 0.45f,
        softness = 0.22f
    ),
    FILM135(
        id = "135",
        labelRes = R.string.film_135,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.02f, 1.0f, 0.97f),
        saturation = 0.95f,
        contrast = 1.06f,
        fade = 0.10f,
        grain = 0.24f,
        vignette = 0.26f,
        warmth = 0.09f,
        halation = 0.18f,
        softness = 0.06f
    ),
    FILM120(
        id = "120",
        labelRes = R.string.film_120,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(1.0f, 1.0f, 1.01f),
        saturation = 0.90f,
        contrast = 1.02f,
        fade = 0.14f,
        grain = 0.20f,
        vignette = 0.30f,
        warmth = 0.03f,
        halation = 0.10f,
        softness = 0.04f
    ),
    COMPACT(
        id = "compact",
        labelRes = R.string.film_compact,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.05f, 1.0f, 0.98f),
        saturation = 1.30f,
        contrast = 1.20f,
        fade = 0.0f,
        grain = 0.12f,
        vignette = 0.16f,
        warmth = 0.02f,
        halation = 0.30f,
        softness = 0.0f
    ),
    DISPOSABLE(
        id = "disposable",
        labelRes = R.string.film_disposable,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(1.08f, 1.02f, 0.90f),
        saturation = 1.10f,
        contrast = 1.10f,
        fade = 0.08f,
        grain = 0.18f,
        vignette = 0.22f,
        warmth = 0.16f,
        halation = 0.20f,
        softness = 0.05f
    ),
    INSTANT(
        id = "instant",
        labelRes = R.string.film_instant,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(0.98f, 1.0f, 1.04f),
        saturation = 0.86f,
        contrast = 0.96f,
        fade = 0.22f,
        grain = 0.14f,
        vignette = 0.18f,
        warmth = -0.04f,
        halation = 0.12f,
        softness = 0.10f
    ),
    LOMO(
        id = "lomo",
        labelRes = R.string.film_lomo,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.12f, 0.96f, 1.06f),
        saturation = 1.25f,
        contrast = 1.16f,
        fade = 0.06f,
        grain = 0.26f,
        vignette = 0.30f,
        warmth = -0.02f,
        halation = 0.35f,
        softness = 0.08f
    ),
    SUPER8(
        id = "super8",
        labelRes = R.string.film_super8,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.10f, 1.0f, 0.86f),
        saturation = 1.15f,
        contrast = 1.08f,
        fade = 0.18f,
        grain = 0.40f,
        vignette = 0.40f,
        warmth = 0.20f,
        halation = 0.22f,
        softness = 0.30f
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
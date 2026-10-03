package com.tk.filmcam.film

import androidx.annotation.StringRes
import com.tk.filmcam.R

/**
 * A film look. One model, two implementations: [com.tk.filmcam.pipeline.FilmStillProcessor]
 * bakes it into the saved photo and [com.tk.filmcam.gl.FilmShader] runs it on the
 * viewfinder, so what you see while composing is what lands in the gallery.
 *
 * The order is fixed and matters: gains, then the tone curve, then saturation,
 * then split toning, then fade. Saturation sits *after* the tone curve on
 * purpose. Boosting saturation first and letting a contrast curve clip afterwards
 * is what turned the previous looks into pure white and pure black — measured,
 * 22% of pixels driven out of range on average, 100% of a bright outdoor frame.
 *
 * These are aesthetic looks, not simulations of a particular emulsion. Soft
 * contrast, lifted blacks, split-toned shadows, visible grain.
 */
enum class FilmCamera(
    val id: String,
    @StringRes val labelRes: Int,
    /** 3:2, 4:3, 1:1, 4:5 ... encoded as a float ratio width/height. */
    val aspectRatio: Float,
    /** Per-channel RGB multipliers applied before the tone curve. */
    val rgbGain: FloatArray,
    /** Saturation multiplier, applied after the tone curve. */
    val saturation: Float,
    /** Soft S-curve strength, 0..1. Never clips: 0 stays 0 and 1 stays 1. */
    val contrast: Float,
    /** Lifted blacks / faded look. 0 = deep blacks. */
    val fade: Float,
    /** Warmth: positive = warmer, negative = cooler. */
    val warmth: Float,
    /** Shadow colour cast, signed, added weighted by (1 - luma)^2. */
    val shadowTint: FloatArray,
    /** Highlight colour cast, signed, added weighted by luma^2. */
    val highlightTint: FloatArray,
    /** Master strength for both tints, 0..1. */
    val split: Float,
    /** Grain strength 0..1. */
    val grain: Float,
    /** Vignette strength 0..1. */
    val vignette: Float,
    /** Highlight halation glow strength 0..1, the CCD/compact signature. */
    val halation: Float,
    /** Slight overall softness 0..1 (CCD sensor bloom look). */
    val softness: Float
) {
    CCD(
        id = "ccd",
        labelRes = R.string.film_ccd,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(0.97f, 1.00f, 1.16f),
        saturation = 1.18f,
        contrast = 0.42f,
        fade = 0.04f,
        warmth = -0.18f,
        shadowTint = floatArrayOf(-0.03f, 0.00f, 0.09f),
        highlightTint = floatArrayOf(0.05f, 0.02f, -0.02f),
        split = 1.0f,
        grain = 0.36f,
        vignette = 0.40f,
        halation = 0.90f,
        softness = 0.20f
    ),
    FILM135(
        id = "135",
        labelRes = R.string.film_135,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.05f, 1.00f, 0.92f),
        saturation = 1.10f,
        contrast = 0.34f,
        fade = 0.10f,
        warmth = 0.26f,
        shadowTint = floatArrayOf(0.02f, 0.01f, -0.04f),
        highlightTint = floatArrayOf(0.07f, 0.03f, -0.03f),
        split = 1.0f,
        grain = 0.26f,
        vignette = 0.22f,
        halation = 0.30f,
        softness = 0.06f
    ),
    FILM120(
        id = "120",
        labelRes = R.string.film_120,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(0.98f, 1.00f, 1.04f),
        saturation = 0.72f,
        contrast = 0.20f,
        fade = 0.14f,
        warmth = -0.02f,
        shadowTint = floatArrayOf(-0.02f, 0.00f, 0.05f),
        highlightTint = floatArrayOf(0.03f, 0.02f, 0.00f),
        split = 0.8f,
        grain = 0.14f,
        vignette = 0.46f,
        halation = 0.12f,
        softness = 0.14f
    ),
    COMPACT(
        id = "compact",
        labelRes = R.string.film_compact,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.08f, 1.00f, 0.94f),
        saturation = 1.42f,
        contrast = 0.55f,
        fade = 0.0f,
        warmth = 0.08f,
        shadowTint = floatArrayOf(-0.04f, -0.01f, 0.08f),
        highlightTint = floatArrayOf(0.04f, 0.01f, 0.02f),
        split = 1.0f,
        grain = 0.10f,
        vignette = 0.20f,
        halation = 0.42f,
        softness = 0.0f
    ),
    DISPOSABLE(
        id = "disposable",
        labelRes = R.string.film_disposable,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(1.12f, 1.08f, 0.74f),
        saturation = 1.30f,
        contrast = 0.08f,
        fade = 0.32f,
        warmth = 0.30f,
        shadowTint = floatArrayOf(0.05f, 0.04f, -0.08f),
        highlightTint = floatArrayOf(0.10f, 0.07f, -0.07f),
        split = 1.0f,
        grain = 0.24f,
        vignette = 0.28f,
        halation = 0.32f,
        softness = 0.04f
    ),
    INSTANT(
        id = "instant",
        labelRes = R.string.film_instant,
        aspectRatio = 1f / 1f,
        rgbGain = floatArrayOf(1.04f, 0.97f, 1.08f),
        saturation = 0.98f,
        contrast = 0.06f,
        fade = 0.34f,
        warmth = -0.06f,
        shadowTint = floatArrayOf(-0.03f, 0.01f, 0.08f),
        highlightTint = floatArrayOf(0.08f, 0.02f, 0.05f),
        split = 1.0f,
        grain = 0.10f,
        vignette = 0.18f,
        halation = 0.18f,
        softness = 0.10f
    ),
    LOMO(
        id = "lomo",
        labelRes = R.string.film_lomo,
        aspectRatio = 3f / 2f,
        rgbGain = floatArrayOf(1.12f, 0.94f, 1.10f),
        saturation = 1.34f,
        contrast = 0.46f,
        fade = 0.06f,
        warmth = -0.04f,
        shadowTint = floatArrayOf(-0.06f, 0.00f, 0.10f),
        highlightTint = floatArrayOf(0.06f, -0.01f, 0.08f),
        split = 1.0f,
        grain = 0.32f,
        vignette = 0.46f,
        halation = 0.62f,
        softness = 0.08f
    ),
    SUPER8(
        id = "super8",
        labelRes = R.string.film_super8,
        aspectRatio = 4f / 3f,
        rgbGain = floatArrayOf(1.20f, 0.97f, 0.70f),
        saturation = 0.92f,
        contrast = 0.38f,
        fade = 0.12f,
        warmth = 0.32f,
        shadowTint = floatArrayOf(0.06f, 0.01f, -0.09f),
        highlightTint = floatArrayOf(0.11f, 0.03f, -0.06f),
        split = 1.0f,
        grain = 0.70f,
        vignette = 0.52f,
        halation = 0.38f,
        softness = 0.40f
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
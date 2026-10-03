package com.tk.filmcam.gl

import com.tk.filmcam.film.FilmCamera

/**
 * The live viewfinder grade.
 *
 * This is the twin of `FilmStillProcessor.gradeRow` in Kotlin. Same steps, same
 * order, same constants — gains, soft S-curve, saturation with a highlight
 * roll-off, split toning, fade, then halation, grain and vignette. Change one,
 * change the other, or the photo stops matching the preview.
 *
 * The texture is an external OES surface (`samplerExternalOES`), not a
 * `sampler2D`: the camera writes into a SurfaceTexture bound to an external
 * GL texture. `uTransform` carries mirror, rotation and centre-crop, all
 * folded into one 3x3 so the vertex stage is a single matrix multiply.
 */
object FilmShader {

    const val LUMA_R = "0.2126"
    const val LUMA_G = "0.7152"
    const val LUMA_B = "0.0722"

    val VERTEX: String = """
attribute vec4 aPosition;
attribute vec2 aTexCoord;
uniform mat3 uTransform;
varying vec2 vTexCoord;
void main() {
    vec3 t = uTransform * vec3(aTexCoord, 1.0);
    vTexCoord = t.xy;
    gl_Position = aPosition;
}
"""

    val FRAGMENT: String = """
#extension GL_OES_EGL_image_external : require
precision highp float;

varying vec2 vTexCoord;

uniform samplerExternalOES uTex;
uniform vec2  uTexSize;      // camera buffer size, in pixels
uniform vec2  uViewSize;     // view size, in pixels
uniform float uTime;

uniform vec3  uGain;
uniform float uSaturation;
uniform float uContrast;
uniform float uFade;
uniform float uWarmth;
uniform vec3  uShadowTint;
uniform vec3  uHighlightTint;
uniform float uSplit;
uniform float uGrain;
uniform float uVignette;
uniform float uHalation;
uniform float uSoftness;

const vec3 LUMA = vec3($LUMA_R, $LUMA_G, $LUMA_B);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float smoothstep1(float x) {
    x = clamp(x, 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

// Monotone S-curve. Fixes 0 and 1 exactly so it cannot clip.
float sCurve(float x, float k) {
    float v = clamp(x, 0.0, 1.0);
    float a = v + (smoothstep1(v) - v) * k;
    float s = smoothstep1(a);
    return a + (s - a) * k;
}

vec3 sampleTex(vec2 uv) {
    return texture2D(uTex, uv).rgb;
}

void main() {
    vec2 uv = vTexCoord;
    vec2 texel = 1.0 / uTexSize;

    // --- optical softness: cheap 5-tap cross blur, only when the look wants it
    vec3 color = sampleTex(uv);
    if (uSoftness > 0.001) {
        vec3 blur = color * 0.36;
        blur += sampleTex(uv + vec2(texel.x, 0.0) * 2.0) * 0.16;
        blur += sampleTex(uv - vec2(texel.x, 0.0) * 2.0) * 0.16;
        blur += sampleTex(uv + vec2(0.0, texel.y) * 2.0) * 0.16;
        blur += sampleTex(uv - vec2(0.0, texel.y) * 2.0) * 0.16;
        color = mix(color, blur, uSoftness);
    }

    // --- halation: highlights bleed sideways, the CCD signature
    if (uHalation > 0.001) {
        vec3 bleed = vec3(0.0);
        float radius = 4.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7853981634;
            vec3 s = sampleTex(uv + vec2(cos(a), sin(a)) * texel * radius);
            bleed += max(vec3(0.0), s - 0.62) * smoothstep(0.62, 1.0, dot(s, LUMA));
        }
        color += (bleed / 8.0) * uHalation * 1.6;
    }

    // --- 1. gains and warmth
    color *= uGain;
    float w = uWarmth * 0.10;
    color.r += w;
    color.b -= w;

    // --- 2. tone curve BEFORE colour. The old pipeline did contrast after a
    //        saturation boost, which clipped the channel furthest from luma and
    //        is why every look came out pure white and pure black.
    float k = clamp(uContrast, 0.0, 1.0);
    color = vec3(sCurve(color.r, k), sCurve(color.g, k), sCurve(color.b, k));

    // --- 3. saturation after tone mapping, rolled off in the highlights
    float l = dot(color, LUMA);
    color = vec3(l) + (color - vec3(l)) * uSaturation;
    float peak = max(color.r, max(color.g, color.b));
    if (peak > 0.72) {
        float t = smoothstep1((peak - 0.72) / 0.28) * 0.45;
        vec3 l2 = vec3(dot(color, LUMA));
        color += (l2 - color) * t;
    }

    // --- 4. split toning: shadows and highlights get their own colour
    l = clamp(dot(color, LUMA), 0.0, 1.0);
    color += uShadowTint * ((1.0 - l) * (1.0 - l) * uSplit);
    color += uHighlightTint * (l * l * uSplit);

    // --- 5. fade lifts the blacks and greys the toe
    if (uFade > 0.001) {
        color = color * (1.0 - uFade) + uFade;
        vec3 l3 = vec3(dot(color, LUMA));
        color += (l3 - color) * (uFade * 0.35);
    }

    // --- 6. grain, weighted to the midtones like real emulsion, and animated
    //        so the viewfinder reads as film rather than as a still image
    if (uGrain > 0.001) {
        float g = hash12(uv * uTexSize + vec2(uTime * 61.7, uTime * 37.3));
        float midWeight = max(1.0 - abs(l - 0.5) * 1.7, 0.2);
        color += (g - 0.5) * uGrain * 0.42 * midWeight;
    }

    // --- 7. vignette
    if (uVignette > 0.001) {
        vec2 centred = uv - 0.5;
        float aspect = uViewSize.x / max(uViewSize.y, 1.0);
        float r = length(centred * vec2(aspect, 1.0));
        float vig = 1.0 - smoothstep(0.30, 0.95, r);
        color *= mix(1.0, vig, uVignette);
    }

    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""

    /**
     * Fold mirror, rotation and centre-crop into a single 3x3 UV transform.
     *
     * Column-major, to match `glUniformMatrix3fv(..., transpose = false)` with
     * the values laid out as GLSL's `mat3(c0, c1, c2)`.
     *
     * @param sensorOrientation clockwise degrees the buffer needs to be rotated
     *        to be upright, from `CameraCharacteristics.SENSOR_ORIENTATION`.
     * @param displayRotation `Surface.ROTATION_0/90/180/270` of the view.
     * @param bufferWidth camera buffer width in pixels.
     * @param bufferHeight camera buffer height in pixels.
     */
    fun transform(
        sensorOrientation: Int,
        displayRotation: Int,
        mirror: Boolean,
        bufferWidth: Int,
        bufferHeight: Int,
        viewWidth: Int,
        viewHeight: Int
    ): FloatArray {
        val degrees = if (mirror) {
            (sensorOrientation + displayRotationDegrees(displayRotation)) % 360
        } else {
            (sensorOrientation - displayRotationDegrees(displayRotation) + 360) % 360
        }
        val rad = Math.toRadians(degrees.toDouble())
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()

        // Sampling map for a clockwise content rotation by `degrees`: at 90
        // degrees output(u,v) reads input(v, 1-u). Transposing this is what
        // puts the viewfinder on its head.
        var m = floatArrayOf(c, s, 0f, -s, c, 0f, 0f, 0f, 1f)

        // Rotate about the centre of the displayed image, then mirror. The order
        // matters: a reflection about u = 1-u does not commute with the shift
        // that centres the rotation, so mirroring first shifts the result by a
        // whole image width on the selfie camera.
        m = multiply(TRANSLATE_HALF, multiply(m, TRANSLATE_NEG_HALF))
        if (mirror) m = multiply(m, MIRROR_X)

        // centre-crop the rotated buffer down to the view's aspect
        val swaps = sensorOrientation % 180 != 0
        val rotatedWidth = if (swaps) bufferHeight else bufferWidth
        val rotatedHeight = if (swaps) bufferWidth else bufferHeight
        val imageAspect = rotatedWidth.toFloat() / maxOf(1, rotatedHeight)
        val viewAspect = viewWidth.toFloat() / maxOf(1, viewHeight)
        val cropX: Float
        val cropY: Float
        if (imageAspect > viewAspect) {
            cropX = viewAspect / imageAspect
            cropY = 1f
        } else {
            cropX = 1f
            cropY = imageAspect / viewAspect
        }
        m = floatArrayOf(
            m[0] * cropX, m[1] * cropX, m[2] * cropX + (1f - cropX) * 0.5f,
            m[3] * cropY, m[4] * cropY, m[5] * cropY + (1f - cropY) * 0.5f,
            m[6], m[7], m[8]
        )

        // GLSL mat3 is column-major, so hand back the transpose of the
        // row-major product.
        return floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])
    }

    /** Row-major 3x3 multiply: `a * b`. */
    private fun multiply(a: FloatArray, b: FloatArray) = floatArrayOf(
        a[0] * b[0] + a[1] * b[3] + a[2] * b[6],
        a[0] * b[1] + a[1] * b[4] + a[2] * b[7],
        a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
        a[3] * b[0] + a[4] * b[3] + a[5] * b[6],
        a[3] * b[1] + a[4] * b[4] + a[5] * b[7],
        a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
        a[6] * b[0] + a[7] * b[3] + a[8] * b[6],
        a[6] * b[1] + a[7] * b[4] + a[8] * b[7],
        a[6] * b[2] + a[7] * b[5] + a[8] * b[8]
    )

    private val TRANSLATE_HALF = floatArrayOf(1f, 0f, 0.5f, 0f, 1f, 0.5f, 0f, 0f, 1f)
    private val TRANSLATE_NEG_HALF = floatArrayOf(1f, 0f, -0.5f, 0f, 1f, -0.5f, 0f, 0f, 1f)
    private val MIRROR_X = floatArrayOf(-1f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun displayRotationDegrees(displayRotation: Int): Int = when (displayRotation) {
        1 -> 90
        2 -> 180
        3 -> 270
        else -> 0
    }

    /** Everything the fragment stage needs from a look, in one array. */
    fun uniforms(film: FilmCamera): FloatArray = floatArrayOf(
        film.rgbGain[0], film.rgbGain[1], film.rgbGain[2],
        film.saturation,
        film.contrast,
        film.fade,
        film.warmth,
        film.shadowTint[0], film.shadowTint[1], film.shadowTint[2],
        film.highlightTint[0], film.highlightTint[1], film.highlightTint[2],
        film.split,
        film.grain,
        film.vignette,
        film.halation,
        film.softness
    )
}
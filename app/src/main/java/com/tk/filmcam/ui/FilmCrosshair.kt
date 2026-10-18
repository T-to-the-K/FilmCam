package com.tk.filmcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tk.filmcam.film.FilmCamera
import kotlin.math.min

/**
 * A crosshair that belongs to the selected film. Every look draws its own
 * reticle, so the viewfinder tells you which camera you are holding without
 * reading the label, the way a viewfinder screen does on the real thing.
 *
 * The reticle sits inside the graded frame, not the raw view. The grade is a
 * centred inset (see [com.tk.filmcam.gl.FilmLookRenderer.frameRect]), so a
 * crosshair pinned to the full view would drift off the picture in every aspect
 * other than the sensor's own. The geometry below mirrors that function.
 */
@Composable
fun FilmCrosshair(film: FilmCamera, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val viewAspect = w / h
        val frameAspect = film.aspectRatio
        val halfWidth: Float
        val halfHeight: Float
        if (frameAspect > viewAspect) {
            halfWidth = FRAME_FILL
            halfHeight = (viewAspect / frameAspect) * FRAME_FILL
        } else {
            halfHeight = FRAME_FILL
            halfWidth = (frameAspect / viewAspect) * FRAME_FILL
        }
        val centreY = FRAME_LIFT.coerceAtMost(1f - halfHeight)
        val cx = w / 2f
        val cy = h / 2f - centreY * h / 2f
        val hw = halfWidth * w / 2f
        val hh = halfHeight * h / 2f

        drawReticle(film, cx, cy, hw, hh)
    }
}

/**
 * Mirrors [com.tk.filmcam.gl.FilmLookRenderer]. Kept as literals rather than
 * reached for across the module: they describe one frame in two places, and a
 * change to one side has to be a deliberate change to both.
 */
private const val FRAME_FILL = 0.86f
private const val FRAME_LIFT = 0.07f

/** Tint per look. A hint of the look's palette, kept faint enough to read against. */
private fun tintFor(film: FilmCamera): Color = when (film) {
    FilmCamera.CCD -> Color(0xFFEAF2FF)
    FilmCamera.FILM135 -> Color(0xFFFFF3D6)
    FilmCamera.FILM120 -> Color(0xFFF1F1F1)
    FilmCamera.COMPACT -> Color(0xFFFFE9C7)
    FilmCamera.DISPOSABLE -> Color(0xFFFFF0B0)
    FilmCamera.INSTANT -> Color(0xFFE9E6FF)
    FilmCamera.LOMO -> Color(0xFFFFD9E0)
    FilmCamera.SUPER8 -> Color(0xFFFFE6C0)
}.copy(alpha = 0.8f)

private fun DrawScope.drawReticle(film: FilmCamera, cx: Float, cy: Float, hw: Float, hh: Float) {
    val tint = tintFor(film)
    val s = min(hw, hh)
    val thin = 1.2.dp.toPx()
    val thick = 2.8.dp.toPx()

    when (film) {
        FilmCamera.CCD -> drawCcd(cx, cy, s, tint, thin)
        FilmCamera.FILM135 -> drawRangefinder(cx, cy, hw, hh, s, tint, thin)
        FilmCamera.FILM120 -> drawMediumFormat(cx, cy, hw, hh, s, tint, thin)
        FilmCamera.COMPACT -> drawCompact(cx, cy, s, tint, thin)
        FilmCamera.DISPOSABLE -> drawDisposable(cx, cy, s, tint, thick)
        FilmCamera.INSTANT -> drawInstant(cx, cy, s, tint, thin)
        FilmCamera.LOMO -> drawLomo(cx, cy, s, tint, thin)
        FilmCamera.SUPER8 -> drawSuper8(cx, cy, hw, hh, s, tint, thin, thick)
    }
}

/** A square AF target with accented corners — the Y2K digicam read. */
private fun DrawScope.drawCcd(cx: Float, cy: Float, s: Float, tint: Color, thin: Float) {
    val box = s * 0.36f
    val half = box / 2f
    drawRect(
        color = tint,
        topLeft = Offset(cx - half, cy - half),
        size = Size(box, box),
        style = Stroke(width = thin)
    )
    val tick = box * 0.24f
    val corner = thin * 1.8f
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val x = cx + sx * half
            val y = cy + sy * half
            drawLine(tint, Offset(x, y), Offset(x - sx * tick, y), corner, StrokeCap.Round)
            drawLine(tint, Offset(x, y), Offset(x, y - sy * tick), corner, StrokeCap.Round)
        }
    }
    drawCircle(tint, radius = thin * 1.3f, center = Offset(cx, cy))
}

private fun DrawScope.drawRangefinder(cx: Float, cy: Float, hw: Float, hh: Float, s: Float, tint: Color, thin: Float) {
    drawLine(tint, Offset(cx - hw * 0.86f, cy), Offset(cx + hw * 0.86f, cy), thin, StrokeCap.Round)
    drawLine(tint, Offset(cx, cy - hh * 0.86f), Offset(cx, cy + hh * 0.86f), thin, StrokeCap.Round)
    drawCircle(tint, radius = s * 0.07f, center = Offset(cx, cy), style = Stroke(width = thin * 1.4f))
}

private fun DrawScope.drawMediumFormat(cx: Float, cy: Float, hw: Float, hh: Float, s: Float, tint: Color, thin: Float) {
    drawCornerMarks(cx, cy, hw, hh, length = s * 0.18f, tint = tint, width = thin)
    drawCircle(tint, radius = s * 0.06f, center = Offset(cx, cy), style = Stroke(width = thin))
}

private fun DrawScope.drawCompact(cx: Float, cy: Float, s: Float, tint: Color, thin: Float) {
    val gap = s * 0.17f
    val len = s * 0.11f
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val x = cx + sx * gap
            val y = cy + sy * gap
            drawLine(tint, Offset(x, y), Offset(x - sx * len, y), thin, StrokeCap.Round)
            drawLine(tint, Offset(x, y), Offset(x, y - sy * len), thin, StrokeCap.Round)
        }
    }
    drawCircle(tint, radius = thin * 1.4f, center = Offset(cx, cy))
}

private fun DrawScope.drawDisposable(cx: Float, cy: Float, s: Float, tint: Color, thick: Float) {
    val arm = s * 0.22f
    drawLine(tint, Offset(cx - arm, cy), Offset(cx + arm, cy), thick, StrokeCap.Round)
    drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy + arm), thick, StrokeCap.Round)
    val d = thick * 1.6f
    drawRect(tint, topLeft = Offset(cx - d / 2f, cy - d / 2f), size = Size(d, d))
}

private fun DrawScope.drawInstant(cx: Float, cy: Float, s: Float, tint: Color, thin: Float) {
    val w = s * 0.44f
    val h = s * 0.52f
    drawRoundRect(
        color = tint,
        topLeft = Offset(cx - w / 2f, cy - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(s * 0.07f),
        style = Stroke(width = thin)
    )
    val arm = s * 0.11f
    drawLine(tint, Offset(cx - arm, cy), Offset(cx + arm, cy), thin, StrokeCap.Round)
    drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy + arm), thin, StrokeCap.Round)
}

private fun DrawScope.drawLomo(cx: Float, cy: Float, s: Float, tint: Color, thin: Float) {
    val r = s * 0.16f
    drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(width = thin))
    drawLine(tint, Offset(cx - r * 1.5f, cy), Offset(cx + r * 1.5f, cy), thin, StrokeCap.Round)
    drawLine(tint, Offset(cx, cy - r * 1.5f), Offset(cx, cy + r * 1.5f), thin, StrokeCap.Round)
    val d = r / 1.4142f
    val tick = s * 0.03f
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            val x = cx + sx * d
            val y = cy + sy * d
            drawLine(tint, Offset(x, y), Offset(x + sx * tick, y + sy * tick), thin, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawSuper8(cx: Float, cy: Float, hw: Float, hh: Float, s: Float, tint: Color, thin: Float, thick: Float) {
    drawCornerMarks(cx, cy, hw, hh, length = s * 0.16f, tint = tint, width = thin)

    val topY = cy - hh * 0.98f
    val t = s * 0.05f
    val triangle = Path().apply {
        moveTo(cx, topY - t)
        lineTo(cx - t, topY + t)
        lineTo(cx + t, topY + t)
        close()
    }
    drawPath(triangle, tint)

    val arm = s * 0.1f
    drawLine(tint, Offset(cx - arm, cy), Offset(cx + arm, cy), thick, StrokeCap.Round)
    drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy + arm), thick, StrokeCap.Round)
}

private fun DrawScope.drawCornerMarks(cx: Float, cy: Float, hw: Float, hh: Float, length: Float, tint: Color, width: Float) {
    val x0 = cx - hw * 0.98f
    val x1 = cx + hw * 0.98f
    val y0 = cy - hh * 0.98f
    val y1 = cy + hh * 0.98f
    drawLine(tint, Offset(x0, y0), Offset(x0 + length, y0), width, StrokeCap.Round)
    drawLine(tint, Offset(x0, y0), Offset(x0, y0 + length), width, StrokeCap.Round)
    drawLine(tint, Offset(x1, y0), Offset(x1 - length, y0), width, StrokeCap.Round)
    drawLine(tint, Offset(x1, y0), Offset(x1, y0 + length), width, StrokeCap.Round)
    drawLine(tint, Offset(x0, y1), Offset(x0 + length, y1), width, StrokeCap.Round)
    drawLine(tint, Offset(x0, y1), Offset(x0, y1 - length), width, StrokeCap.Round)
    drawLine(tint, Offset(x1, y1), Offset(x1 - length, y1), width, StrokeCap.Round)
    drawLine(tint, Offset(x1, y1), Offset(x1, y1 - length), width, StrokeCap.Round)
}

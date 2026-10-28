package com.tk.filmcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tk.filmcam.film.FilmCamera

/**
 * The frame around the camera view. This is the rectangle that holds the
 * picture, not a decoration inside it: every look wraps the view in its own
 * border, the way a 35mm strip, an instant print or a digicam bezel surrounds
 * the image itself.
 *
 * The geometry mirrors [com.tk.filmcam.gl.FilmLookRenderer.frameRect], so the
 * frame sits exactly on the edge of the graded picture. Everything is drawn
 * outward from that edge, into the black letterbox, so no part of the picture
 * is hidden.
 */
@Composable
fun FilmFrame(film: FilmCamera, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val viewAspect = w / h
        // The camera view box itself is pinned to the sensor's 4:3, not to the
        // film look: it is the rectangle the preview is drawn into. The look
        // changes the border around it, never the box.
        val frameAspect = if (w >= h) 4f / 3f else 3f / 4f
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

        val left = cx - hw
        val right = cx + hw
        val top = cy - hh
        val bottom = cy + hh

        val tint = frameTint(film)
        val thin = 1.2.dp.toPx()
        val bold = 2.8.dp.toPx()

        when (film) {
            FilmCamera.CCD -> drawCcdBezel(left, top, right, bottom, tint, thin)
            FilmCamera.FILM135 -> drawFilm135Strip(left, top, right, bottom, tint, thin)
            FilmCamera.FILM120 -> drawFilm120Border(left, top, right, bottom, tint, thin, bold)
            FilmCamera.COMPACT -> drawCompactBezel(left, top, right, bottom, tint, thin)
            FilmCamera.DISPOSABLE -> drawDisposableBorder(left, top, right, bottom, tint, thin)
            FilmCamera.INSTANT -> drawInstantCard(left, top, right, bottom, tint, thin)
            FilmCamera.LOMO -> drawLomoBorder(left, top, right, bottom, tint, bold)
            FilmCamera.SUPER8 -> drawSuper8Strip(left, top, right, bottom, tint, thin)
        }
    }
}

/** Mirrors [com.tk.filmcam.gl.FilmLookRenderer]. See the note in [FilmCrosshair]. */
private const val FRAME_FILL = 0.86f
private const val FRAME_LIFT = 0.07f

private fun frameTint(film: FilmCamera): Color = when (film) {
    FilmCamera.CCD -> Color(0xFFEAF2FF)
    FilmCamera.FILM135 -> Color(0xFFFFE7B8)
    FilmCamera.FILM120 -> Color(0xFFF1F1F1)
    FilmCamera.COMPACT -> Color(0xFFFFE9C7)
    FilmCamera.DISPOSABLE -> Color(0xFFFFE07A)
    FilmCamera.INSTANT -> Color(0xFFE9E6FF)
    FilmCamera.LOMO -> Color(0xFFFFC9D4)
    FilmCamera.SUPER8 -> Color(0xFFFFD9A3)
}.copy(alpha = 0.78f)

/** Two thin rules around the view plus corner blocks, the digicam bezel. */
private fun DrawScope.drawCcdBezel(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val inner = 6.dp.toPx()
    val outer = 13.dp.toPx()

    drawRect(
        color = tint.copy(alpha = 0.55f),
        topLeft = Offset(l - inner, t - inner),
        size = Size(r - l + 2 * inner, b - t + 2 * inner),
        style = Stroke(thin)
    )
    drawRect(
        color = tint,
        topLeft = Offset(l - outer, t - outer),
        size = Size(r - l + 2 * outer, b - t + 2 * outer),
        style = Stroke(thin)
    )

    val block = 5.dp.toPx()
    for (x in floatArrayOf(l - outer, r + outer)) {
        for (y in floatArrayOf(t - outer, b + outer)) {
            drawRect(tint, Offset(x - block / 2f, y - block / 2f), Size(block, block))
        }
    }
}

/** 35mm: the picture sits inside the strip, sprocket holes top and bottom. */
private fun DrawScope.drawFilm135Strip(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val band = 18.dp.toPx()

    drawRect(
        color = tint.copy(alpha = 0.5f),
        topLeft = Offset(l - band, t - band),
        size = Size(r - l + 2 * band, b - t + 2 * band),
        style = Stroke(thin)
    )

    val holeWidth = 11.dp.toPx()
    val holeHeight = 7.dp.toPx()
    val gap = 8.dp.toPx()
    val margin = 6.dp.toPx()
    val topY = t - band + 5.dp.toPx()
    val bottomY = b + band - 5.dp.toPx() - holeHeight
    val corner = CornerRadius(holeHeight * 0.35f)

    var x = l + margin
    while (x + holeWidth < r - margin) {
        drawRoundRect(tint, Offset(x, topY), Size(holeWidth, holeHeight), corner, style = Stroke(thin))
        drawRoundRect(tint, Offset(x, bottomY), Size(holeWidth, holeHeight), corner, style = Stroke(thin))
        x += holeWidth + gap
    }
}

/** Medium format: a heavy border and four corner blocks around the view. */
private fun DrawScope.drawFilm120Border(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float, bold: Float) {
    val band = 12.dp.toPx()

    drawRect(
        color = tint,
        topLeft = Offset(l - band, t - band),
        size = Size(r - l + 2 * band, b - t + 2 * band),
        style = Stroke(bold)
    )

    val block = 9.dp.toPx()
    for (x in floatArrayOf(l - band, r + band)) {
        for (y in floatArrayOf(t - band, b + band)) {
            drawRect(tint, Offset(x - block / 2f, y - block / 2f), Size(block, block))
        }
    }

    val tick = 9.dp.toPx()
    val cx = (l + r) / 2f
    drawLine(tint, Offset(cx - tick, t - band), Offset(cx + tick, t - band), thin)
    drawLine(tint, Offset(cx - tick, b + band), Offset(cx + tick, b + band), thin)
}

/** Point and shoot: a rounded bezel with a small label line underneath. */
private fun DrawScope.drawCompactBezel(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val band = 10.dp.toPx()

    drawRoundRect(
        color = tint,
        topLeft = Offset(l - band, t - band),
        size = Size(r - l + 2 * band, b - t + 2 * band),
        cornerRadius = CornerRadius(16.dp.toPx()),
        style = Stroke(thin * 1.7f)
    )

    val lineY = b + band - 4.dp.toPx()
    val lineLeft = l + 18.dp.toPx()
    drawLine(tint.copy(alpha = 0.75f), Offset(lineLeft, lineY), Offset(lineLeft + 42.dp.toPx(), lineY), thin)
}

/** Cheap camera: the border itself is dashed, nothing lines up. */
private fun DrawScope.drawDisposableBorder(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val band = 10.dp.toPx()

    drawRect(
        color = tint,
        topLeft = Offset(l - band, t - band),
        size = Size(r - l + 2 * band, b - t + 2 * band),
        style = Stroke(
            width = thin * 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        )
    )
}

/** Instant print: a card around the picture with the deep bottom border. */
private fun DrawScope.drawInstantCard(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val side = 13.dp.toPx()
    val topBand = 13.dp.toPx()
    val bottomBand = 46.dp.toPx()

    val card = Offset(l - side, t - topBand)
    val cardSize = Size(r - l + 2 * side, b - t + topBand + bottomBand)
    drawRect(tint.copy(alpha = 0.14f), card, cardSize)
    drawRect(tint.copy(alpha = 0.8f), card, cardSize, style = Stroke(thin))

    val lineY = b + bottomBand - 14.dp.toPx()
    drawLine(tint.copy(alpha = 0.8f), Offset(l + 16.dp.toPx(), lineY), Offset(l + 16.dp.toPx() + 26.dp.toPx(), lineY), thin)
}

/** Lomo: heavy brackets just outside the corners of the view. */
private fun DrawScope.drawLomoBorder(l: Float, t: Float, r: Float, b: Float, tint: Color, bold: Float) {
    val band = 12.dp.toPx()
    val arm = 30.dp.toPx()
    val x0 = l - band
    val x1 = r + band
    val y0 = t - band
    val y1 = b + band

    drawLine(tint, Offset(x0, y0), Offset(x0 + arm, y0), bold)
    drawLine(tint, Offset(x0, y0), Offset(x0, y0 + arm), bold)
    drawLine(tint, Offset(x1, y0), Offset(x1 - arm, y0), bold)
    drawLine(tint, Offset(x1, y0), Offset(x1, y0 + arm), bold)
    drawLine(tint, Offset(x0, y1), Offset(x0 + arm, y1), bold)
    drawLine(tint, Offset(x0, y1), Offset(x0, y1 - arm), bold)
    drawLine(tint, Offset(x1, y1), Offset(x1 - arm, y1), bold)
    drawLine(tint, Offset(x1, y1), Offset(x1, y1 - arm), bold)
}

/** Super 8: perforated film edges running down both sides of the view. */
private fun DrawScope.drawSuper8Strip(l: Float, t: Float, r: Float, b: Float, tint: Color, thin: Float) {
    val band = 16.dp.toPx()

    drawRect(
        color = tint.copy(alpha = 0.5f),
        topLeft = Offset(l - band, t - band),
        size = Size(r - l + 2 * band, b - t + 2 * band),
        style = Stroke(thin)
    )

    val holeWidth = 6.dp.toPx()
    val holeHeight = 10.dp.toPx()
    val gap = 8.dp.toPx()
    val margin = 8.dp.toPx()
    val leftX = l - band + 5.dp.toPx()
    val rightX = r + band - 5.dp.toPx() - holeWidth
    val corner = CornerRadius(holeWidth * 0.35f)

    var y = t + margin
    while (y + holeHeight < b - margin) {
        drawRoundRect(tint, Offset(leftX, y), Size(holeWidth, holeHeight), corner, style = Stroke(thin))
        drawRoundRect(tint, Offset(rightX, y), Size(holeWidth, holeHeight), corner, style = Stroke(thin))
        y += holeHeight + gap
    }
}

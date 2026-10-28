package com.tk.filmcam.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.tk.filmcam.film.FilmCamera
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * The capture feedback, drawn on the viewfinder box itself rather than over the
 * whole screen. Pressing the shutter closes a set of blades inside the box and
 * opens them again, then a highlight runs around the frame while the photo is
 * being graded and saved. Nothing covers the interface and nothing hides the
 * surround: the box is the only thing that moves.
 *
 * [active] is the same flag that disables the shutter button, so the sweep
 * lives exactly as long as the capture does.
 */
@Composable
fun CaptureAnimation(
    film: FilmCamera,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val flash = remember { Animatable(0f) }
    val closure = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (active) {
            flash.snapTo(0f)
            closure.snapTo(0f)
            flash.animateTo(0.85f, tween(durationMillis = 45))
            flash.animateTo(0f, tween(durationMillis = 160))
            closure.animateTo(1f, tween(durationMillis = 110, easing = FastOutSlowInEasing))
            delay(150)
            closure.animateTo(0f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        } else {
            closure.animateTo(0f, tween(durationMillis = 160))
            flash.animateTo(0f, tween(durationMillis = 80))
        }
    }

    val spin = rememberInfiniteTransition(label = "capture")
    val phase by spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing)
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val viewAspect = w / h
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
        val width = halfWidth * w
        val height = halfHeight * h
        val left = w / 2f - width / 2f
        val top = h / 2f - centreY * h / 2f - height / 2f
        if (width <= 0f || height <= 0f) return@Canvas

        clipRect(left, top, left + width, top + height) {
            val covered = height / 2f * closure.value
            drawRect(
                color = Color.Black,
                topLeft = Offset(left, top),
                size = Size(width, covered)
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(left, top + height - covered),
                size = Size(width, covered)
            )
            drawRect(
                color = Color.White.copy(alpha = flash.value),
                topLeft = Offset(left, top),
                size = Size(width, height)
            )
        }

        if (active && closure.value < 0.98f) {
            val tint = captureTint(film)
            val edge = 2.dp.toPx()
            val perimeter = 2f * (width + height)
            val start = perimeter * phase
            val length = perimeter * 0.16f
            val steps = 56
            var previous = pointOnBox(left, top, width, height, perimeter, start)

            for (step in 1..steps) {
                val distance = start + length * step / steps
                val point = pointOnBox(left, top, width, height, perimeter, distance)
                val fade = sin(Math.PI * step / steps).toFloat()

                drawLine(
                    color = tint.copy(alpha = 0.35f * fade),
                    start = previous,
                    end = point,
                    strokeWidth = edge * 2.4f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.9f * fade),
                    start = previous,
                    end = point,
                    strokeWidth = edge,
                    cap = StrokeCap.Round
                )
                previous = point
            }
        }
    }
}

/** Walks clockwise around the box, starting at its top left corner. */
private fun pointOnBox(l: Float, t: Float, w: Float, h: Float, perimeter: Float, distance: Float): Offset {
    var d = distance % perimeter
    if (d < 0f) d += perimeter
    return when {
        d < w -> Offset(l + d, t)
        d < w + h -> Offset(l + w, t + (d - w))
        d < w + h + w -> Offset(l + w - (d - w - h), t + h)
        else -> Offset(l, t + h - (d - w - h - w))
    }
}

/** Mirrors [com.tk.filmcam.gl.FilmLookRenderer]. See the note in [FilmCrosshair]. */
private const val FRAME_FILL = 0.86f
private const val FRAME_LIFT = 0.07f

private fun captureTint(film: FilmCamera): Color = when (film) {
    FilmCamera.CCD -> Color(0xFFEAF2FF)
    FilmCamera.FILM135 -> Color(0xFFFFE7B8)
    FilmCamera.FILM120 -> Color(0xFFF1F1F1)
    FilmCamera.COMPACT -> Color(0xFFFFE9C7)
    FilmCamera.DISPOSABLE -> Color(0xFFFFE07A)
    FilmCamera.INSTANT -> Color(0xFFE9E6FF)
    FilmCamera.LOMO -> Color(0xFFFFC9D4)
    FilmCamera.SUPER8 -> Color(0xFFFFD9A3)
}

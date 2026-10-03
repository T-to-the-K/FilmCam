package com.tk.filmcam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.filmcam.R
import com.tk.filmcam.film.FilmCamera

/**
 * The Dazz-style film chooser: a horizontal strip of one-tap buttons, one per
 * camera type. Selecting a button swaps the live look on the preview.
 */
@Composable
fun FilmPicker(
    selected: FilmCamera,
    onSelect: (FilmCamera) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "" },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(FilmCamera.entries.toList(), key = { it.id }) { film ->
                FilmChip(
                    film = film,
                    isSelected = film == selected,
                    onClick = { onSelect(film) }
                )
            }
        }
    }
}

@Composable
private fun FilmChip(
    film: FilmCamera,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(film.labelRes)
    val labelDesc = stringResource(R.string.cd_film_thumbnail, label)
    val selectedDesc = stringResource(R.string.cd_film_selected, label)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else Color.White.copy(alpha = 0.18f),
        label = "filmChipBorder"
    )
    val background by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.45f),
        label = "filmChipBackground"
    )

    Box(
        modifier = Modifier
            .height(46.dp)
            .background(background, RoundedCornerShape(23.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(23.dp)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isSelected) selectedDesc else labelDesc }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** Small round colour swatch showing each look's signature tint. */
@Composable
fun FilmSwatch(film: FilmCamera, modifier: Modifier = Modifier) {
    val tint = Color(
        red = film.rgbGain[0].coerceIn(0f, 1.4f) / 1.4f,
        green = film.rgbGain[1].coerceIn(0f, 1.4f) / 1.4f,
        blue = film.rgbGain[2].coerceIn(0f, 1.4f) / 1.4f,
        alpha = 1f
    )
    Spacer(
        modifier = modifier
            .size(10.dp)
            .background(tint, CircleShape)
    )
}

@Composable
fun PreviewSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize()) { content() }
}

@Composable
fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    val shutterDesc = stringResource(R.string.cd_shutter)

    Box(
        modifier = modifier
            .size(74.dp)
            .border(width = 3.dp, color = ringColor, shape = CircleShape)
            .padding(6.dp)
            .background(
                if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = shutterDesc },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {}
    }
}

@Composable
fun FlipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.cd_flip_camera)
    Box(
        modifier = modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Text("⟳", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
fun IconPill(
    label: String,
    desc: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.25f)
            active -> Color(0xFFFFC24B)
            else -> Color.White
        },
        label = "pillTint"
    )
    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                if (active) Color(0x33FFC24B) else Color.Black.copy(alpha = 0.4f),
                RoundedCornerShape(19.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tint, fontSize = 15.sp)
    }
}

@Composable
fun ZoomReadout(zoom: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(19.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.zoom_readout, zoom),
            color = Color.White,
            fontSize = 13.sp
        )
    }
}

@Composable
fun StatusBar(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun Spacer8(modifier: Modifier = Modifier) = Spacer(modifier.width(8.dp))
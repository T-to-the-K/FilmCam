package com.tk.filmcam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            .height(48.dp)
            .background(background, RoundedCornerShape(24.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
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
    ) {}
}

@Composable
fun FlipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.cd_flip_camera)
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun IconPill(
    icon: ImageVector,
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
            .size(48.dp)
            .background(
                if (active) Color(0x33FFC24B) else Color.Black.copy(alpha = 0.4f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun ZoomReadout(zoom: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .width(64.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
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
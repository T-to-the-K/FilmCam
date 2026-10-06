package com.tk.filmcam.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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

@Composable
fun FilterEntryPill(
    film: FilmCamera,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(film.labelRes)
    val desc = stringResource(R.string.cd_open_filters)
    val borderColor by animateColorAsState(
        targetValue = Color.White.copy(alpha = 0.18f),
        label = "filterPillBorder"
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun FilterPanel(
    selected: FilmCamera,
    onSelect: (FilmCamera) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filters_title),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Close filters" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(FilmCamera.entries.toList(), key = { it.id }) { film ->
                    FilterGridItem(
                        film = film,
                        isSelected = film == selected,
                        onClick = {
                            onSelect(film)
                            onClose()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGridItem(
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
        label = "filterGridBorder"
    )
    val background by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.12f)
        else Color.Black.copy(alpha = 0.45f),
        label = "filterGridBackground"
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

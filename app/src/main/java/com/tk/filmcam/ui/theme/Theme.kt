package com.tk.filmcam.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FilmColorScheme = darkColorScheme(
    primary = Color(0xFFE8E2D4),
    onPrimary = Color(0xFF1A1A18),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2EFE6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF2EFE6),
    surfaceVariant = Color(0xFF232323),
    onSurfaceVariant = Color(0xFFBDB8AC),
    outline = Color(0xFF4A463E)
)

@Composable
fun FilmCamTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FilmColorScheme,
        content = content
    )
}
package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DjColorScheme = darkColorScheme(
    primary = DeckACyan,
    onPrimary = Color.Black,
    primaryContainer = DeckACyanBg,
    onPrimaryContainer = DeckACyan,
    secondary = DeckBAmber,
    onSecondary = Color.Black,
    secondaryContainer = DeckBAmberBg,
    onSecondaryContainer = DeckBAmber,
    tertiary = LedGreen,
    onTertiary = Color.Black,
    background = DjBackground,
    onBackground = TextPrimary,
    surface = DjSurface,
    onSurface = TextPrimary,
    surfaceVariant = DjSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DjSurfaceBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DjColorScheme,
        typography = Typography,
        content = content
    )
}

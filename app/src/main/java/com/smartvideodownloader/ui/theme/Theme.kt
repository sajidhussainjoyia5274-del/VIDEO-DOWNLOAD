package com.smartvideodownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Navy blue, light blue, white, burgundy accents
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B2545), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F8), onPrimaryContainer = Color(0xFF0B2545),
    secondary = Color(0xFF4F9BD9), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBFA), onSecondaryContainer = Color(0xFF0B2545),
    tertiary = Color(0xFF800020), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6D9DF), onTertiaryContainer = Color(0xFF4A0013),
    background = Color(0xFFF4F8FC), onBackground = Color(0xFF0B1A2E),
    surface = Color.White, onSurface = Color(0xFF0B1A2E),
    surfaceVariant = Color(0xFFE3ECF5), onSurfaceVariant = Color(0xFF41546B),
    outline = Color(0xFF6B7F95),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB4F0), onPrimary = Color(0xFF0B2545),
    primaryContainer = Color(0xFF13315C), onPrimaryContainer = Color(0xFFD6E6F8),
    secondary = Color(0xFF7DB8EC), onSecondary = Color(0xFF0B2545),
    secondaryContainer = Color(0xFF13315C), onSecondaryContainer = Color(0xFFD6E6F8),
    tertiary = Color(0xFFE07A8E), onTertiary = Color(0xFF4A0013),
    tertiaryContainer = Color(0xFF5C0F22), onTertiaryContainer = Color(0xFFF6D9DF),
    background = Color(0xFF0A1626), onBackground = Color(0xFFE6EEF7),
    surface = Color(0xFF0F1E33), onSurface = Color(0xFFE6EEF7),
    surfaceVariant = Color(0xFF1B2D47), onSurfaceVariant = Color(0xFFB4C4D8),
    outline = Color(0xFF8CA0B8),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SmartVideoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}

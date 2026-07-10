package com.rutamercaderistas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueSoft,
    onPrimaryContainer = AccentBlue,
    secondary = AccentGreen,
    onSecondary = Color.White,
    secondaryContainer = AccentGreenSoft,
    onSecondaryContainer = AccentGreen,
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = AccentOrangeSoft,
    onTertiaryContainer = AccentOrange,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = RedSoft,
    onErrorContainer = Color(0xFFD32F2F),
    scrim = Scrim
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003A6E),
    onPrimaryContainer = AccentBlueSoft,
    secondary = AccentGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF003D1A),
    onSecondaryContainer = AccentGreenSoft,
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A2800),
    onTertiaryContainer = AccentOrangeSoft,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFEF5350),
    onError = Color(0xFF1A1A1A),
    errorContainer = Color(0xFF4E1515),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0x99000000)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MercaderistasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
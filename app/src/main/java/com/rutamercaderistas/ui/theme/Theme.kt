package com.rutamercaderistas.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.preferences.prefsDataStore

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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F6F8),
    surfaceContainer = Color(0xFFF0F2F4),
    surfaceContainerHigh = Color(0xFFEAEDF0),
    surfaceContainerHighest = Color(0xFFE4E7EB),
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = RedSoft,
    onErrorContainer = ErrorRed,
    scrim = Scrim
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun MercaderistasTheme(content: @Composable () -> Unit) {
    val dimens = rememberAppDimens()
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context)
    } else {
        LightColorScheme
    }
    val userScale by context.prefsDataStore.data
        .map { it[PreferencesRepository.KEY_FONT_SCALE] ?: 1f }
        .collectAsState(initial = 1f)
    val systemFontScale = context.resources.configuration.fontScale
    val baseDensity = LocalDensity.current
    val density = Density(baseDensity.density, systemFontScale * userScale)
    CompositionLocalProvider(
        LocalAppDimens provides dimens,
        LocalDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

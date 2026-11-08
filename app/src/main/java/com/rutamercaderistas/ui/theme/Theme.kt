package com.rutamercaderistas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.preferences.prefsDataStore

private val LightColorScheme = lightColorScheme(
    // El chrome usa el índigo aurora para continuar el banner morado. El azul
    // queda para enlaces, Lider y las categorías (AccentBlue*).
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoSoft,
    onPrimaryContainer = BrandIndigoText,
    secondary = AccentGreen,
    onSecondary = Color.White,
    secondaryContainer = AccentGreenSoft,
    onSecondaryContainer = AccentGreenText,
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
    surfaceContainerLow = Color(0xFFF4F6FA),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE7EBF3),
    surfaceContainerHighest = Color(0xFFDFE4EE),
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = RedSoft,
    onErrorContainer = ErrorRed,
    scrim = Scrim
)

/**
 * Modo oscuro: mismos roles semánticos que el tema claro para que las
 * superficies nuevas (tarjeta de actualización, modales, vidrio) se adapten
 * sin colores crudos. La identidad sigue siendo índigo.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB9A7FF),
    onPrimary = Color(0xFF241A66),
    primaryContainer = Color(0xFF3B2AA8),
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFF7EE2A8),
    onSecondary = Color(0xFF003919),
    secondaryContainer = Color(0xFF1E5E3A),
    onSecondaryContainer = Color(0xFFB8F5CE),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3B00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF12151C),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF262B36),
    onSurfaceVariant = Color(0xFFB6BECC),
    surfaceContainerLowest = Color(0xFF0A0C11),
    surfaceContainerLow = Color(0xFF161A22),
    surfaceContainer = Color(0xFF1B202A),
    surfaceContainerHigh = Color(0xFF222833),
    surfaceContainerHighest = Color(0xFF2A313D),
    outline = Color(0xFF7D8797),
    outlineVariant = Color(0xFF3A424F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xCC000000),
)

/** Tope de escala de fuente efectiva (sistema x usuario). Ver [MercaderistasTheme]. */
const val MAX_FONT_SCALE = 1.3f

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
    // Paleta curada: dynamicColorScheme pintaba botones, enlaces y la barra
    // inferior con el color del wallpaper, así que la identidad (y el vidrio
    // índigo) cambiaba de teléfono en teléfono. El tema sigue el modo del
    // sistema, pero con los roles de marca.
    val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    val userScale by context.prefsDataStore.data
        .map { it[PreferencesRepository.KEY_FONT_SCALE] ?: 1f }
        .collectAsStateWithLifecycle(initialValue = 1f)
    val systemFontScale = context.resources.configuration.fontScale
    val baseDensity = LocalDensity.current
    // Tope de escala: con zoom alto del sistema la app explotaba en tamaño y
    // obligaba a scrollear de más. Se respeta el zoom hasta 1.3x y ahí se frena
    // para conservar la densidad de información. Solo afecta sp, no dp.
    val effectiveFontScale = (systemFontScale * userScale).coerceAtMost(MAX_FONT_SCALE)
    // Avoid allowing a large system display zoom to make every component oversized.
    // Touch targets keep their 48.dp semantic size; only the app's visual density is capped.
    val density = Density(compactDensity(baseDensity.density), effectiveFontScale)
    val reducedMotion = rememberReducedMotionEnabled()
    CompositionLocalProvider(
        LocalAppDimens provides dimens,
        LocalDensity provides density,
        LocalReducedMotionEnabled provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

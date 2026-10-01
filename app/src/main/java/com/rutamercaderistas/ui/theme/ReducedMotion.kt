package com.rutamercaderistas.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Equivalente a prefers-reduced-motion: true cuando el usuario quitó las
 * animaciones del sistema ("Quitar animaciones" en accesibilidad). Toda
 * animación decorativa o en loop debe consultarlo y degradar a cross-fade
 * o estado estático. Pura y testeable en JVM vía [isMotionReduced].
 */
val LocalReducedMotionEnabled: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { false }

/** true si la escala de animación del sistema es 0 (animaciones quitadas). */
fun isMotionReduced(animatorDurationScale: String?): Boolean =
    animatorDurationScale?.toFloatOrNull()?.let { it == 0f } ?: false

@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getString(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            )
            isMotionReduced(scale)
        } catch (_: Exception) {
            false
        }
    }
}

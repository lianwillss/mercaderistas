package com.rutamercaderistas.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Feedback en press-down (§1 del marco): encoge al 97% mientras está
 * presionado. Complementa el ripple de M3 (que ya viene por defecto en
 * `clickable`): el ripple confirma el toque, la escala confirma la presión.
 * Pasar el MISMO [interactionSource] al `clickable` correspondiente.
 */
@Composable
fun rememberPressInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (LocalReducedMotionEnabled.current) tween(100)
        else MotionSprings.default(),
        label = "pressScale",
    )
    graphicsLayer(scaleX = scale, scaleY = scale)
}

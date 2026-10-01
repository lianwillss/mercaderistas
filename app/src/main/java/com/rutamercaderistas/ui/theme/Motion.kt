package com.rutamercaderistas.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object MotionDuration {
    val short: Int = 200
    val medium: Int = 400
    val long: Int = 600
    val extraLong: Int = 800
}

object MotionEasing {
    val standard: Easing = FastOutSlowInEasing
    val linear: Easing = LinearEasing
    val decelerate: Easing = FastOutSlowInEasing
}

/**
 * Springs para todo lo tocable o interrumpible (los tweens de arriba quedan
 * para fades de opacidad). Un spring parte del valor actual en pantalla y
 * hereda velocidad, así que agarrar una animación a mitad de camino no salta.
 */
object MotionSprings {
    /** Default: críticamente amortiguado, sin rebote ni distracción. */
    fun <T> default() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * Solo cuando el gesto trajo momentum (flick, arrastre con velocidad).
     * En apariciones simples (menús, banners) el rebote se siente mal.
     */
    fun <T> bouncy() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

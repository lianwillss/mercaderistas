package com.rutamercaderistas.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing

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

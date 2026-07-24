package com.rutamercaderistas.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Elevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 8.dp
    val level5: Dp = 12.dp

    val card: Dp = level2
    val dialog: Dp = level3
    val dropdown: Dp = level4
    val modal: Dp = level5
}

enum class ElevationLevel(val value: Dp) {
    LEVEL0(Elevation.level0),
    LEVEL1(Elevation.level1),
    LEVEL2(Elevation.level2),
    LEVEL3(Elevation.level3),
    LEVEL4(Elevation.level4),
    LEVEL5(Elevation.level5),
}

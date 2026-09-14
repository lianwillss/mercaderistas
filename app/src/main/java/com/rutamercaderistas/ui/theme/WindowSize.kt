package com.rutamercaderistas.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWindowWidth {
    Compact,
    Medium,
    Expanded,
}

fun appWindowWidth(width: Dp): AppWindowWidth = when {
    width < 600.dp -> AppWindowWidth.Compact
    width < 840.dp -> AppWindowWidth.Medium
    else -> AppWindowWidth.Expanded
}

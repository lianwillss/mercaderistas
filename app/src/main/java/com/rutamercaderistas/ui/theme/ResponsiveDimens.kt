package com.rutamercaderistas.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun rs(): Float {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return (widthDp / 412f).coerceIn(0.78f, 1f)
}

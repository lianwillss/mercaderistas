package com.rutamercaderistas.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rutamercaderistas.domain.model.chainColorsHex
import com.rutamercaderistas.domain.model.normalizeChain

@Composable
fun chainColor(chain: String): Color {
    val hex = chainColorsHex[normalizeChain(chain)]
    return if (hex != null) Color(hex) else MaterialTheme.colorScheme.secondary
}

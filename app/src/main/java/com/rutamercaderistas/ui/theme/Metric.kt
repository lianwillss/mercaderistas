package com.rutamercaderistas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * Cifras tabulares: todos los dígitos ocupan el mismo ancho, así los números
 * quedan alineados en columna al bajar por la lista. Se derivan del typescale
 * del tema para no fijar tamaños en la pantalla.
 */
val MaterialTheme.metricValue: TextStyle
    @Composable get() = typography.headlineSmall.copy(fontFeatureSettings = "tnum")

val MaterialTheme.metricValueCompact: TextStyle
    @Composable get() = typography.titleLarge.copy(fontFeatureSettings = "tnum")

val MaterialTheme.metricLabel: TextStyle
    @Composable get() = typography.labelSmall.copy(fontFeatureSettings = "tnum")

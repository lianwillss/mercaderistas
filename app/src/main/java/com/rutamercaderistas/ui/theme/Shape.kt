package com.rutamercaderistas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object ComponentShapes {
    val button = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(20.dp)
    val textField = RoundedCornerShape(28.dp)

    // Botón de degradado (Limpiar): radió del diseño original, no el de
    // button/pill, para que el borde de 3 dp se lea como un marco.
    val gradientButton = RoundedCornerShape(8.dp)
    val gradientButtonInset = RoundedCornerShape(6.dp)
}

/**
 * Formas del vidrio. La barra y el riel son cápsulas flotantes separadas del
 * borde de la pantalla: la esquina grande es lo que las hace leer como
 * "objeto de vidrio" y no como una franja blanca más.
 */
object GlassShapes {
    val bar = RoundedCornerShape(28.dp)
    val rail = RoundedCornerShape(26.dp)
    val pill = RoundedCornerShape(20.dp)
}

package com.rutamercaderistas.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Garantiza que el verde de texto cumple WCAG AA (>= 4.5:1) sobre fondos
 * claros. AccentGreen (#34C759) como foreground daba ~2.2:1; por eso existe
 * [AccentGreenText] solo para texto/iconos (los rellenos siguen verdes).
 */
class ContrastTest {

    private fun luminance(color: Color): Double {
        fun linear(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val (hi, lo) = listOf(luminance(fg), luminance(bg)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `AccentGreenText cumple AA sobre fondos claros`() {
        val lightBackgrounds = listOf(
            Color.White,
            Color(0xFFF5F6F8), // surfaceContainerLow (tarjetas)
            AccentGreenSoft, // pills y fondos verdes suaves
        )
        for (bg in lightBackgrounds) {
            assertTrue(
                "ratio ${ratio(AccentGreenText, bg)} sobre $bg",
                ratio(AccentGreenText, bg) >= 4.5,
            )
        }
    }

    @Test
    fun `AccentGreen como foreground no cumple (por eso existe el token)`() {
        assertTrue(ratio(AccentGreen, Color.White) < 4.5)
    }
}

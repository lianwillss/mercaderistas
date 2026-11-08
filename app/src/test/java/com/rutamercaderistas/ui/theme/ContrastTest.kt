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
            Color(0xFFF4F6FA), // surfaceContainerLow (tarjetas)
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

    /**
     * El naranjo de urgencia como texto quedaba en 4,49:1 sobre su propio fondo
     * suave: fallaba AA por centésimas. UrgencyOrangeText lo resuelve sin tocar
     * los rellenos.
     */
    @Test
    fun `UrgencyOrangeText cumple AA sobre fondos claros`() {
        val fondos = listOf(
            Color.White,
            Color(0xFFF4F6FA),
            UrgencyOrangeSoft,
        )
        for (bg in fondos) {
            assertTrue(
                "ratio ${ratio(UrgencyOrangeText, bg)} sobre $bg",
                ratio(UrgencyOrangeText, bg) >= 4.5,
            )
        }
    }

    @Test
    fun `UrgencyOrange como texto no cumple (por eso existe el token)`() {
        assertTrue(ratio(UrgencyOrange, UrgencyOrangeSoft) < 4.5)
    }

    /**
     * Los iconos de cadena (16–20dp) se pintaban con los rellenos de marca:
     * el amarillo de Tottus quedaba en 1,4:1 sobre blanco (invisible) y el
     * azul/fucsia/rojo no llegaban a AA como foreground. [storeColor] debe
     * devolver siempre la variante legible, también sobre el fondo suave.
     */
    @Test
    fun `storeColor devuelve variantes legibles para todas las cadenas`() {
        val cadenas = listOf("Jumbo", "Lider", "Santa Isabel", "Unimarc", "Tottus", "Alvi", "Cencosud", "Otra")
        val fondos = listOf(Color.White, Color(0xFFF4F6FA))
        for (cadena in cadenas) {
            val fg = storeColor(cadena)
            for (bg in fondos) {
                assertTrue(
                    "$cadena: ratio ${ratio(fg, bg)} sobre $bg",
                    ratio(fg, bg) >= 4.5,
                )
            }
            assertTrue(
                "$cadena: ratio ${ratio(fg, storeSoftColor(cadena))} sobre su fondo suave",
                ratio(fg, storeSoftColor(cadena)) >= 4.5,
            )
        }
    }

    @Test
    fun `los rellenos de marca de las cadenas no sirven como foreground`() {
        // Si alguien vuelve a tintar un icono con el relleno, estos ratios lo delatan.
        assertTrue(ratio(StoreColorYellow, Color.White) < 3.0)
        assertTrue(ratio(StoreColorFuchsia, Color.White) < 4.5)
        assertTrue(ratio(StoreColorRed, Color.White) < 4.5)
        assertTrue(ratio(AccentBlue, Color.White) < 4.5)
    }

    /** Iniciales de local: texto pequeño sobre su propio fondo al 15 %. */
    @Test
    fun `la paleta de iniciales cumple AA sobre blanco y sobre su tinte`() {
        for (color in AvatarPalette) {
            val tinte = Color(
                red = color.red * 0.15f + 0.85f,
                green = color.green * 0.15f + 0.85f,
                blue = color.blue * 0.15f + 0.85f,
            )
            assertTrue("ratio ${ratio(color, Color.White)} sobre blanco", ratio(color, Color.White) >= 4.5)
            assertTrue("ratio ${ratio(color, tinte)} sobre su tinte", ratio(color, tinte) >= 4.5)
        }
    }

    @Test
    fun `el texto secundario cumple AA sobre superficies neutras`() {
        for (bg in listOf(
            Color(0xFFF4F6FA), // surfaceContainerLow
            Color(0xFFEEF1F7), // surfaceContainer
            Color(0xFFE7EBF3), // surfaceContainerHigh
        )) {
            assertTrue(
                "ratio ${ratio(TextSecondary, bg)} sobre $bg",
                ratio(TextSecondary, bg) >= 4.5,
            )
        }
    }

    @Test
    fun `TextTertiary cumple AA sobre blanco, fondo y surfaceVariant`() {
        val fondos = listOf(Color.White, Background, SurfaceVariant)
        for (bg in fondos) {
            assertTrue(
                "ratio ${ratio(TextTertiary, bg)} sobre $bg",
                ratio(TextTertiary, bg) >= 4.5,
            )
        }
    }

    /** Mezcla `fg` con `alpha` sobre `bg`: así se mide el vidrio translúcido. */
    private fun sobre(fg: Color, bg: Color, alpha: Float) = Color(
        red = fg.red * alpha + bg.red * (1f - alpha),
        green = fg.green * alpha + bg.green * (1f - alpha),
        blue = fg.blue * alpha + bg.blue * (1f - alpha),
    )

    /**
     * El índigo es el chrome 2026: relleno con blanco encima y variante de
     * texto para enlaces/iconos. Si alguien lo aclara "para que se vea más
     * moderno", estos ratios lo delatan.
     */
    @Test
    fun `el indigo de marca cumple AA como relleno y como texto`() {
        assertTrue(
            "blanco sobre BrandIndigo: ${ratio(Color.White, BrandIndigo)}",
            ratio(Color.White, BrandIndigo) >= 4.5,
        )
        for (bg in listOf(
            Color.White,
            BrandIndigoSoft,
            Background,
            Color(0xFFEEF1F7), // surfaceContainer: selección secundaria
            Color(0xFFE7EBF3), // surfaceContainerHigh: chips y metadatos
        )) {
            assertTrue(
                "BrandIndigoText sobre $bg: ${ratio(BrandIndigoText, bg)}",
                ratio(BrandIndigoText, bg) >= 4.5,
            )
        }
    }

    /**
     * La píldora de la barra de vidrio es la única superficie índigo suave:
     * el label activo debe seguir cumpliendo AA incluso sobre el peor fondo
     * que se vea a través del vidrio.
     */
    @Test
    fun `la pildora de navegacion mantiene AA`() {
        for (velo in listOf(GlassPillTop, GlassPillBottom)) {
            for (fondo in listOf(Color.White, Color(0xFF0F172A))) {
                val compuesto = sobre(velo, fondo, velo.alpha)
                assertTrue(
                    "BrandIndigoText sobre píldora (${velo.alpha}) sobre $fondo: " +
                        "${ratio(BrandIndigoText, compuesto)}",
                    ratio(BrandIndigoText, compuesto) >= 4.5,
                )
            }
        }
    }

    /**
     * El header indigo lleva texto blanco sobre un degradado. HeaderViolet es
     * el punto más claro que llega a tocar el texto: si se aclara el header,
     * este test lo delata.
     */
    @Test
    fun `el texto blanco del header cumple AA sobre el degradado indigo`() {
        val fondos = listOf(HeaderDeepIndigo, HeaderMidIndigo, HeaderIndigo, HeaderViolet)
        for (bg in fondos) {
            assertTrue(
                "blanco sobre $bg: ${ratio(Color.White, bg)}",
                ratio(Color.White, bg) >= 4.5,
            )
        }
    }

    /**
     * El vidrio deja pasar el contenido: se mide el peor caso (contenido
     * oscuro debajo) para las dos opacidades del degradado. Los labels de la
     * barra usan TextSecondary (no seleccionado) y BrandIndigoText (activo).
     */
    @Test
    fun `el vidrio mantiene AA incluso sobre contenido oscuro`() {
        val contenidoOscuro = Color(0xFF0F172A)
        val contenidoClaro = Color.White
        val velos = listOf(GlassTintTop, GlassTintBottom)
        for (velo in velos) {
            for (fondo in listOf(contenidoOscuro, contenidoClaro)) {
                val compuesto = sobre(velo, fondo, velo.alpha)
                assertTrue(
                    "TextSecondary sobre vidrio (${velo.alpha}) sobre $fondo: " +
                        "${ratio(TextSecondary, compuesto)}",
                    ratio(TextSecondary, compuesto) >= 4.5,
                )
                assertTrue(
                    "BrandIndigoText sobre vidrio (${velo.alpha}) sobre $fondo: " +
                        "${ratio(BrandIndigoText, compuesto)}",
                    ratio(BrandIndigoText, compuesto) >= 4.5,
                )
            }
        }
    }

    @Test
    fun `TextPrimary y TextSecondary cumplen AA y mantienen jerarquia`() {
        assertTrue(ratio(TextPrimary, Surface) >= 7.0)
        assertTrue(ratio(TextSecondary, Surface) >= 4.5)
        // La jerarquía importa: el terciario nunca debe ser más oscuro que el secundario.
        assertTrue(luminance(TextPrimary) < luminance(TextSecondary))
        assertTrue(luminance(TextSecondary) < luminance(TextTertiary))
    }

    /**
     * El placeholder de "Buscar ruta" usaba onSurfaceVariant al 60 %: sobre el
     * contenedor del campo caía a 2,1–2,6:1. Va a opacidad plena (6,1:1).
     */
    /**
     * La tarjeta/modal de actualización usa estos pares. Si alguien cambia el
     * texto a un gris claro o el chip a índigo translúcido, el test lo delata.
     */
    @Test
    fun `la tarjeta de actualizacion cumple AA`() {
        val tarjeta = Color(0xFFF4F6FA) // surfaceContainerLow
        assertTrue("titulo: ${ratio(TextPrimary, tarjeta)}", ratio(TextPrimary, tarjeta) >= 4.5)
        assertTrue("subtitulo: ${ratio(TextSecondary, tarjeta)}", ratio(TextSecondary, tarjeta) >= 4.5)
        assertTrue("progreso: ${ratio(BrandIndigo, tarjeta)}", ratio(BrandIndigo, tarjeta) >= 4.5)
        assertTrue(
            "chip de version: ${ratio(BrandIndigoText, BrandIndigoSoft)}",
            ratio(BrandIndigoText, BrandIndigoSoft) >= 4.5,
        )
        assertTrue("boton: ${ratio(Color.White, BrandIndigo)}", ratio(Color.White, BrandIndigo) >= 4.5)
    }

    @Test
    fun `el placeholder del buscador de ruta cumple AA sobre el contenedor`() {
        // unfocusedContainerColor = surfaceVariant.copy(alpha = 0.4f) sobre Surface
        fun sobreBlanco(color: Color, alpha: Float) = Color(
            red = color.red * alpha + 1f * (1f - alpha),
            green = color.green * alpha + 1f * (1f - alpha),
            blue = color.blue * alpha + 1f * (1f - alpha),
        )
        val contenedor = sobreBlanco(SurfaceVariant, 0.4f)
        assertTrue(
            "placeholder a opacidad plena: ${ratio(TextSecondary, contenedor)}",
            ratio(TextSecondary, contenedor) >= 4.5,
        )
        val alSesenta = sobreBlanco(TextSecondary, 0.6f)
        assertTrue(
            "al 60 % no cumplía: ${ratio(alSesenta, contenedor)}",
            ratio(alSesenta, contenedor) < 4.5,
        )
    }
}
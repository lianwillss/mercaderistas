package com.rutamercaderistas.ui.theme

import androidx.compose.ui.graphics.Color
import com.rutamercaderistas.domain.model.normalizeChain

// Stock: botón de quiebres (naranja que respira + amarillo fluorescente)
val QuiebreOrangeStart = Color(0xFFE8590C)
val QuiebreOrangeEnd = Color(0xFFC2410C)
val QuiebreYellow = Color(0xFFFFF200)

// Stock: tarjeta de negativos (negro con neón)
val NegativosBlack = Color(0xFF0B0B0B)
val NegativosNeon = Color(0xFFCCFF00)

// Stock: cabecera (usa los azules del banner del rutero)
val StockHeaderText = Color(0xFFFFFFFF)
val StockHeaderSubtle = Color(0xD9FFFFFF)
val StockHeaderChip = Color(0x3DFFFFFF)

// ── Banner superior colapsable (2026) ──────────────────────────────
// Degradado morado #3B1F7A → #7B4FD6, texto blanco y superficies
// translúcidas. El texto blanco da ≥ 5:1 en todo el degradado.
val BannerPurpleDeep = Color(0xFF3B1F7A)
val BannerPurpleMid = Color(0xFF5A34AC)
val BannerPurpleLight = Color(0xFF7B4FD6)
val BannerText = Color(0xFFFFFFFF)
val BannerTextMuted = Color(0xE6FFFFFF)
val BannerGlass = Color(0x33FFFFFF)
val BannerGlassBorder = Color(0x2EFFFFFF)

// Frescura del snapshot: verde hoy, ámbar ayer, rojo 2+ días.
// Rellenos con texto blanco encima: 5.2:1, 5.0:1 y 6.5:1 (AA).
val FrescuraHoy = Color(0xFF1E7D32)
val FrescuraAyer = Color(0xFFB45309)
val FrescuraAtrasado = Color(0xFFB91C1C)

// ── Identidad 2026: índigo aurora ───────────────────────────────────
// El banner superior ya era morado; el azul tipo iOS del chrome competía
// con él. El índigo continúa ese degradado, da una identidad propia y deja
// el azul (AccentBlue*) para enlaces, Lider y las tarjetas de categoría.
// [BrandIndigo] es relleno (botones, indicador, FAB): blanco encima = 5,9:1.
// [BrandIndigoText] es la variante para texto e iconos: 7,9:1 sobre blanco
// y 6,7:1 sobre su fondo suave (AA).
val BrandIndigo = Color(0xFF5B4BE0)
val BrandIndigoDeep = Color(0xFF3D2FB8)
val BrandIndigoSoft = Color(0xFFECEAFE)
val BrandIndigoText = Color(0xFF4338CA)
val BrandViolet = Color(0xFF8B5CF6)

// ── Liquid Glass 2026 ───────────────────────────────────────────────
// Vidrio frío translúcido: el velo deja pasar el contenido, el borde
// superior es especular (luz) y el inferior se apaga. La opacidad mínima
// (0,84) mantiene AA para texto secundario incluso si debajo hay contenido
// oscuro; los ratios están cubiertos por ContrastTest.
val GlassTintTop = Color(0xEBF6F8FF)      // blanco frío al 92 %
val GlassTintBottom = Color(0xD6F1F3FF)   // 84 %: el blur se alcanza a ver
val GlassBorderHighlight = Color(0xE6FFFFFF)
val GlassBorderShade = Color(0x2E9AA3C7)
val GlassSheen = Color(0x40FFFFFF)
val GlassShadowAmbient = Color(0x1F3D2FB8)
val GlassShadowSpot = Color(0x4D3D2FB8)
// La píldora del vidrio es la superficie índigo suave de la selección
// principal de la navegación. El resto de las selecciones (días, filtros,
// cadenas) usa superficie neutra + borde índigo, para que un filtro no
// compita visualmente con el destino activo.
val GlassPillTop = Color(0xF5ECEAFE)
val GlassPillBottom = Color(0xF0DCD7FE)
val GlassPillBorder = Color(0xCCFFFFFF)

// Lienzo porcelana frío + tinta slate. Antes el fondo era casi blanco
// (F8F9FA) y el texto #1A1D21; ahora hay más separación entre lienzo,
// tarjetas y texto sin subir el contraste de forma agresiva.
val Background = Color(0xFFF6F7FB)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFEEF1F7)
val Outline = Color(0xFF98A2B3)
val OutlineVariant = Color(0xFFD5DBE6)

val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475467)
// El terciario anterior (#686E74) daba 4,4:1 sobre el nuevo surfaceVariant
// y fallaba AA por poco. #5F6B7A mantiene la jerarquía (más claro que el
// secundario) y llega a 4,8:1 sobre el fondo más oscuro.
val TextTertiary = Color(0xFF5F6B7A)

val AccentBlue = Color(0xFF007AFF)
val AccentBlueSoft = Color(0xFFE8F0FE)
val AccentGreen = Color(0xFF34C759)
val AccentGreenSoft = Color(0xFFE6F9EA)
// Verde SOLO para texto/iconos sobre fondos claros. AccentGreen como
// foreground da 2.2:1 (falla AA); este da 5.2/4.8/4.7 sobre
// blanco/surfaceContainerLow/AccentGreenSoft. Rellenos siguen con AccentGreen.
val AccentGreenText = Color(0xFF1E7D32)
val AccentOrange = Color(0xFFFF9500)
val AccentOrangeSoft = Color(0xFFFFF3E0)

val RedSoft = Color(0xFFFFEBEE)
val CardShadow = Color(0x1A000000)
val Scrim = Color(0x80000000)

val StoreColorFuchsia = Color(0xFFE91E63)
val StoreColorFuchsiaSoft = Color(0xFFFCE4EC)
val StoreColorRed = Color(0xFFE53935)
val StoreColorRedSoft = Color(0xFFFFEBEE)
val StoreColorYellow = Color(0xFFFFD600)
val StoreColorYellowSoft = Color(0xFFFFF8E1)
val StoreColorPurple = Color(0xFF9C27B0)
val StoreColorPurpleSoft = Color(0xFFF3E5F5)

// ── Cadenas: variantes para FOREGROUND (iconos y texto) ──────────────
// Los rellenos de marca de arriba fallan como foreground sobre claro:
// amarillo 1,4:1, azul 4,0:1, fucsia 4,35:1, rojo 4,23:1. Igual que
// AccentGreenText/UrgencyOrangeText, estos tokens son el mismo tono
// oscurecido y solo se usan para tintar iconos o texto.
val AccentBlueText = Color(0xFF005FB8)        // 6,3:1 sobre blanco
val StoreColorFuchsiaText = Color(0xFFAD1457) // 7,0:1
val StoreColorRedText = Color(0xFFB3261E)     // 6,5:1
val StoreColorYellowText = Color(0xFF8A6100)  // 5,5:1 (el amarillo puro da 1,4:1)
val StoreColorPurpleText = Color(0xFF6A1B9A)  // 9,4:1

// Iniciales de local (círculos de la lista de marcas): todos ≥ 4,5:1 sobre
// blanco y sobre su propio fondo al 15 %. Antes el amarillo y el verde claro
// quedaban entre 1,4:1 y 3,3:1.
internal val AvatarPalette = listOf(
    Color(0xFFB3261E), Color(0xFFAD1457), Color(0xFF6A1B9A),
    Color(0xFF4527A0), Color(0xFF283593), Color(0xFF005FB8),
    Color(0xFF00695C), Color(0xFF7A5400), Color(0xFF4E342E),
    Color(0xFF455A64),
)

fun storeColor(name: String): Color {
    return when (normalizeChain(name)) {
        // Se usa como tint de iconos sobre claro: siempre la variante legible.
        "JUMBO" -> AccentGreenText
        "LIDER" -> AccentBlueText
        "SANTA ISABEL" -> StoreColorFuchsiaText
        "UNIMARC" -> StoreColorRedText
        "TOTTUS" -> StoreColorYellowText
        "ALVI" -> StoreColorPurpleText
        "CENCOSUD" -> AccentBlueText
        else -> AccentBlueText
    }
}

// ── Semantic tokens ──────────────────────────────────
val OfflineRed = Color(0xFFFF3B30)
val UrgencyOrange = Color(0xFFB45309)
// #B45309 sobre #FFF0E0 da 4,49:1: falla AA por centésimas cuando se usa en
// texto de 11sp. Este token es el mismo naranjo oscurecido para texto e iconos
// (5,43:1), igual que AccentGreenText con el verde. Los rellenos siguen con
// UrgencyOrange.
val UrgencyOrangeText = Color(0xFFA34708)
val UrgencyOrangeSoft = Color(0xFFFFF0E0)
val UrgencyTomorrowSoft = Color(0xFFFFF8F0)
val ErrorRed = Color(0xFFD32F2F)
val DiscountRed = Color(0xFFB91C1C)
val DiscountSoft = Color(0xFFFEE2E2)

// Precios de promos: tonos oscuros que cumplen contraste AA (>= 4.5:1) sobre fondos claros
val PriceBlue = Color(0xFF005FB8)
val PriceGreen = Color(0xFF1E7D32)
val PricePurple = Color(0xFF6A1B9A)
val PriceOrange = Color(0xFFB45309)
val UrgencyBadgeSoft = Color(0xFFFEF3C7)
val PromoGradientStart = Color(0xFFF97316)
val PromoGradientEnd = Color(0xFFEF4444)

// ── Header principal 2026: índigo aurora ────────────────────────────
// El chrome pasó a índigo; el header azul era el último bloque de otro
// sistema. El degradado arranca casi negro, pasa por índigo y termina en
// violeta antes de fundirse con el fondo. El texto blanco del header se
// mantiene ≥ 7,6:1 sobre el punto más claro que llega a tocar (HeaderViolet).
val HeaderDeepIndigo = Color(0xFF151038)
val HeaderMidIndigo = Color(0xFF241A66)
val HeaderIndigo = Color(0xFF3B2AA8)
val HeaderViolet = Color(0xFF4C3AC9)
val Wave1Indigo = Color(0xFF5B4BE0)
val Wave2Violet = Color(0xFF8B5CF6)
val Wave3Lavender = Color(0xFFB9A7FF)

fun storeSoftColor(name: String): Color {
    return when (normalizeChain(name)) {
        "JUMBO" -> AccentGreenSoft
        "LIDER" -> AccentBlueSoft
        "SANTA ISABEL" -> StoreColorFuchsiaSoft
        "UNIMARC" -> StoreColorRedSoft
        "TOTTUS" -> StoreColorYellowSoft
        "ALVI" -> StoreColorPurpleSoft
        "CENCOSUD" -> AccentBlueSoft
        else -> AccentBlueSoft
    }
}

package com.rutamercaderistas.ui.theme

import androidx.compose.ui.graphics.Color
import com.rutamercaderistas.domain.model.normalizeChain

val Background = Color(0xFFF8F9FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF1F3F5)
val Outline = Color(0xFFADB5BD)
val OutlineVariant = Color(0xFFC0C6CC)

val TextPrimary = Color(0xFF1A1D21)
val TextSecondary = Color(0xFF5C6166)
val TextTertiary = Color(0xFF6C7278)

val AccentBlue = Color(0xFF007AFF)
val AccentBlueSoft = Color(0xFFE8F0FE)
val AccentGreen = Color(0xFF34C759)
val AccentGreenSoft = Color(0xFFE6F9EA)
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

fun storeColor(name: String): Color {
    return when (normalizeChain(name)) {
        "JUMBO" -> AccentGreen
        "LIDER" -> AccentBlue
        "SANTA ISABEL" -> StoreColorFuchsia
        "UNIMARC" -> StoreColorRed
        "TOTTUS" -> StoreColorYellow
        "ALVI" -> StoreColorPurple
        "CENCOSUD" -> AccentBlue
        else -> AccentBlue
    }
}

// ── Semantic tokens ──────────────────────────────────
val OfflineRed = Color(0xFFFF3B30)
val UrgencyOrange = Color(0xFFB45309)
val UrgencyOrangeSoft = Color(0xFFFFF0E0)
val UrgencyTomorrowSoft = Color(0xFFFFF8F0)
val ErrorRed = Color(0xFFD32F2F)
val DiscountRed = Color(0xFFB91C1C)
val DiscountSoft = Color(0xFFFEE2E2)
val UrgencyBadgeSoft = Color(0xFFFEF3C7)
val PromoGradientStart = Color(0xFFF97316)
val PromoGradientEnd = Color(0xFFEF4444)

// HeaderSection gradient blues
val HeaderDeepBlue = Color(0xFF0A2E5A)
val HeaderMidDarkBlue = Color(0xFF0D4F8B)
val HeaderMidBlue = Color(0xFF1A7BB5)
val HeaderLightBlue = Color(0xFFB8DCF0)
val Wave1Blue = Color(0xFF1A7BB5)
val Wave2Blue = Color(0xFF2D9CDB)
val Wave3Blue = Color(0xFF4DB8E8)

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

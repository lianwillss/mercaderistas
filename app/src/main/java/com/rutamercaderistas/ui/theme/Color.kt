package com.rutamercaderistas.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFFF8F9FA)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF1F3F5)
val Outline = Color(0xFFE9ECEF)
val OutlineVariant = Color(0xFFDEE2E6)

val TextPrimary = Color(0xFF1A1D21)
val TextSecondary = Color(0xFF6C757D)
val TextTertiary = Color(0xFFADB5BD)

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
    val n = name.trim().uppercase()
    return when {
        n.startsWith("JUMBO") -> AccentGreen
        n.startsWith("LIDER") -> AccentBlue
        n.startsWith("SISA") || n.startsWith("SANTA ISABEL") || n.startsWith("STA ISABEL") -> StoreColorFuchsia
        n.startsWith("UNIMARC") -> StoreColorRed
        n.startsWith("TOTTUS") -> StoreColorYellow
        n.startsWith("ALVI") -> StoreColorPurple
        else -> AccentBlue
    }
}

fun storeSoftColor(name: String): Color {
    val n = name.trim().uppercase()
    return when {
        n.startsWith("JUMBO") -> AccentGreenSoft
        n.startsWith("LIDER") -> AccentBlueSoft
        n.startsWith("SISA") || n.startsWith("SANTA ISABEL") || n.startsWith("STA ISABEL") -> StoreColorFuchsiaSoft
        n.startsWith("UNIMARC") -> StoreColorRedSoft
        n.startsWith("TOTTUS") -> StoreColorYellowSoft
        n.startsWith("ALVI") -> StoreColorPurpleSoft
        else -> AccentBlueSoft
    }
}

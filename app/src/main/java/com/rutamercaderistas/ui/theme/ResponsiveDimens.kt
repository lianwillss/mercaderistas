package com.rutamercaderistas.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val REFERENCE_WIDTH = 412f

@Composable
fun rs(): Float {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return (widthDp / REFERENCE_WIDTH).coerceIn(0.78f, 1f)
}

@Immutable
data class AppDimens(
    val isCompact: Boolean = false,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 12.dp,
    val spacingLg: Dp = 16.dp,
    val spacingXl: Dp = 20.dp,
    val spacingXxl: Dp = 24.dp,
    val spacingSection: Dp = 32.dp,
    val cardPadding: Dp = 16.dp,
    val cardPaddingH: Dp = 20.dp,
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 24.dp,
    val iconLg: Dp = 28.dp,
    val iconXl: Dp = 36.dp,
    val iconXxl: Dp = 40.dp,
    val touchMin: Dp = 48.dp,
    val promoButtonSize: Dp = 56.dp,
    val routeGridMinWidth: Dp = 380.dp,
    val scrollBottomPadding: Dp = 32.dp,
)

val LocalAppDimens = staticCompositionLocalOf { AppDimens() }

@Composable
fun rememberAppDimens(): AppDimens {
    val factor = rs()
    val compact = factor < 0.9f
    return with(factor) {
        AppDimens(
            isCompact = compact,
            spacingXs = (4 * this).dp,
            spacingSm = (8 * this).dp,
            spacingMd = (12 * this).dp,
            spacingLg = (16 * this).dp,
            spacingXl = (20 * this).dp,
            spacingXxl = (24 * this).dp,
            spacingSection = (32 * this).dp,
            cardPadding = (16 * this).dp,
            cardPaddingH = (20 * this).dp,
            iconSm = (18 * this).dp,
            iconMd = (24 * this).dp,
            iconLg = (28 * this).dp,
            iconXl = (36 * this).dp,
            iconXxl = (40 * this).dp,
            touchMin = 48.dp,
            promoButtonSize = (56 * this).dp,
            routeGridMinWidth = if (compact) 320.dp else 380.dp,
            scrollBottomPadding = (32 * this).dp,
        )
    }
}

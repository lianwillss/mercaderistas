package com.rutamercaderistas.ui.theme

/** Logical density cap used to keep content compact on phones with large display zoom. */
const val COMPACT_DENSITY_DPI = 480
const val COMPACT_DENSITY = COMPACT_DENSITY_DPI / 160f

internal fun compactDensity(baseDensity: Float): Float =
    baseDensity.coerceAtMost(COMPACT_DENSITY)

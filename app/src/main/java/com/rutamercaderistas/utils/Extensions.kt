package com.rutamercaderistas.utils

import android.content.res.Resources
import java.text.Normalizer

// Compilados una vez: normalizeMarca corre por cada marca al importar y al buscar.
private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
private val SPACE_OR_DASH = Regex("[\\s-]")

fun String.normalizeMarca(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    val withoutDiacritics = decomposed.replace(DIACRITICS, "")
    return withoutDiacritics.uppercase()
        .replace("&", "Y")
        .replace(SPACE_OR_DASH, "")
}

fun String.cleanBrand(): String {
    return this.replace("⭐ ", "")
        .trim()
        .normalizeMarca()
}


package com.rutamercaderistas.utils

import android.content.res.Resources
import java.text.Normalizer

fun String.normalizeMarca(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    val withoutDiacritics = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return withoutDiacritics.uppercase().replace(Regex("[\\s-]"), "")
}

fun String.cleanBrand(): String {
    return this.replace("⭐ ", "")
        .trim()
        .normalizeMarca()
}

private val brandPrefixes = listOf(
    "JUMBO" to "JUMBO",
    "SANTA ISABEL" to "SANTA ISABEL",
    "STA ISABEL" to "SANTA ISABEL",
    "LIDER" to "LIDER",
    "WALMART" to "LIDER",
    "TOTTUS" to "TOTTUS",
    "UNIMARC" to "UNIMARC",
    "PARIS" to "PARIS",
    "FALABELLA" to "FALABELLA",
    "ALVI" to "ALVI",
    "PRONTO" to "PRONTO",
    "CUK" to "CUK",
    "SUK" to "SUK",
    "KOMBUCHA" to "KOMBUCHA",
    "ECOCULTIVA" to "ECOCULTIVA",
    "OLIMPIA" to "OLIMPIA",
    "FRANUI" to "FRANUI",
    "CASO Y CIA" to "CASO Y CIA",
    "NAT NATURAL" to "NAT NATURAL",
)

fun normalizeBrand(name: String): String {
    val c = name.replace("⭐ ", "").trim().uppercase()
    for ((prefix, canonical) in brandPrefixes) {
        if (c.startsWith(prefix) && (c.length == prefix.length || c[prefix.length] == ' ')) {
            return canonical
        }
    }
    val firstSpace = c.indexOf(' ')
    return if (firstSpace > 0) c.substring(0, firstSpace) else c
}

fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

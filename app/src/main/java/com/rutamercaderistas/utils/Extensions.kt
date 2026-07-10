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

fun dpToPx(dp: Int): Int = (dp * Resources.getSystem().displayMetrics.density).toInt()

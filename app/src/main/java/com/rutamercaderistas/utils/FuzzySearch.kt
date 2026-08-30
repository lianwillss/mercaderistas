package com.rutamercaderistas.utils

import com.rutamercaderistas.services.compactNorm
import com.rutamercaderistas.services.normalizeSearch

/**
 * Coincidencia difusa tolerante a errores de tipeo y acentos.
 * Normaliza con [normalizeSearch] (minúsculas, sin acentos, conserva espacios).
 * Un local coincide si el texto normalizado contiene la query completa, o bien
 * cada token de la query tiene a su vez un token cercano en el texto objetivo.
 */
fun fuzzyMatches(query: String, target: String): Boolean {
    val q = normalizeSearch(query)
    if (q.isBlank()) return true
    val t = normalizeSearch(target)
    if (t.isBlank()) return false
    if (t.contains(q)) return true
    // Coincidencia compacta (sin espacios) para tolerar "supermercado" vs "super mercado".
    if (compactNorm(t).contains(compactNorm(q))) return true

    val qTokens = q.split(TOKEN_SPLIT)
    val tTokens = t.split(TOKEN_SPLIT)
    if (qTokens.isEmpty()) return true

    return qTokens.all { qt -> tTokens.any { tt -> tokenFuzzy(qt, tt) } }
}

private val TOKEN_SPLIT = Regex("\\s+")

private fun tokenFuzzy(qt: String, tt: String): Boolean {
    if (qt.isEmpty()) return true
    if (qt == tt) return true
    if (tt.contains(qt) && qt.length >= 3) return true
    if (tt.startsWith(qt) && qt.length >= 3) return true
    // Errores de tipeo: distancia de edición pequeña respecto al largo del token.
    val maxDist = if (qt.length <= 3) 0 else if (qt.length <= 6) 1 else 2
    return levenshtein(qt, tt) <= maxDist
}

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    val lp = lhs.length
    val rp = rhs.length
    var cost = IntArray(rp + 1) { it }
    for (i in 1..lp) {
        var prevDiag = cost[0]
        cost[0] = i
        for (j in 1..rp) {
            val temp = cost[j]
            cost[j] = minOf(
                cost[j] + 1,
                cost[j - 1] + 1,
                prevDiag + if (lhs[i - 1] == rhs[j - 1]) 0 else 1,
            )
            prevDiag = temp
        }
    }
    return cost[rp]
}

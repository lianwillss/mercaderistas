package com.rutamercaderistas.utils

import com.rutamercaderistas.domain.model.effectiveChain
import com.rutamercaderistas.domain.model.normalizeChain
import com.rutamercaderistas.models.LocalDelDia
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

/**
 * Alias de cadena del lado query: lo que el usuario escribe vs el nombre
 * canónico (sisa → santa isabel, walmart → lider).
 */
private val CHAIN_QUERY_ALIASES = mapOf(
    "sisa" to "santa isabel",
    "sta" to "santa isabel",
    "walmart" to "lider",
)

private fun expandChainAliases(query: String): String {
    var q = normalizeSearch(query)
    for ((alias, canonical) in CHAIN_QUERY_ALIASES) {
        q = q.replace(Regex("\\b$alias\\b"), canonical)
    }
    return q
}

/**
 * Ranking de locales para la búsqueda global: código exacto (con ceros
 * tolerados) > nombre exacto > difuso. También matchea por cadena
 * (jumbo, sisa/santa isabel, lider/walmart, unimarc, alvi, etc.)
 * usando la cadena normalizada. Función pura, testeable.
 */
fun rankLocales(query: String, locales: List<LocalDelDia>): List<LocalDelDia> {
    if (query.isBlank()) return emptyList()
    val expandedQuery = expandChainAliases(query)
    return locales.mapNotNull { local ->
        val chainNorm = normalizeChain(effectiveChain(local.cadena, local.formato))
        val haystack = buildString {
            append(local.local).append(' ')
            append(local.codigo).append(' ')
            append(local.direccion).append(' ')
            append(local.comuna).append(' ')
            append(local.cadena).append(' ')
            append(local.formato).append(' ')
            append(chainNorm)
            if (local.clientes.isNotEmpty()) {
                append(' ')
                append(local.clientes.joinToString(" ") { it.nombre })
            }
        }
        if (!fuzzyMatches(query, haystack) && !fuzzyMatches(expandedQuery, haystack)) return@mapNotNull null
        val qLower = query.lowercase()
        val codeHit = local.codigo.lowercase().trimStart('0').contains(qLower.trimStart('0')) ||
            local.codigo.lowercase().contains(qLower)
        val nameHit = local.local.lowercase().contains(qLower)
        val score = when {
            codeHit && query.any { it.isDigit() } -> 0
            nameHit -> 1
            else -> 2
        }
        score to local
    }.sortedBy { it.first }.map { it.second }
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

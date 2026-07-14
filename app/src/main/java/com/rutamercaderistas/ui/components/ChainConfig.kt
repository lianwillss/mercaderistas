package com.rutamercaderistas.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val codeToChain = mapOf(
    "J" to "JUMBO",
    "N" to "SANTA ISABEL",
    "EX" to "LIDER",
    "TT" to "TOTTUS",
    "HI" to "LIDER",
    "UN" to "UNIMARC",
    "PP" to "PARIS",
    "FA" to "FALABELLA",
    "AL" to "ALVI",
    "CO" to "PRONTO",
)

private val chainColors = mapOf(
    "JUMBO" to Color(0xFF065F46),
    "SANTA ISABEL" to Color(0xFFDC2626),
    "LIDER" to Color(0xFF16A34A),
    "TOTTUS" to Color(0xFF059669),
    "UNIMARC" to Color(0xFFDC2626),
    "PARIS" to Color(0xFFEAB308),
    "FALABELLA" to Color(0xFFEC4899),
    "ALVI" to Color(0xFF3B82F6),
    "PRONTO" to Color(0xFF8B5CF6),
)

private val holdingToChains = mapOf(
    "CENCOSUD" to setOf("JUMBO", "SANTA ISABEL", "SPID", "EASY", "PARIS"),
)

fun belongsToHolding(chain: String, cadena: String): Boolean {
    val normChain = normalizeChain(chain)
    val normCadena = normalizeChain(cadena)
    val chains = holdingToChains[normCadena] ?: return false
    return chains.any { normChain == normalizeChain(it) }
}

fun effectiveChain(cadena: String, formato: String): String =
    formato.ifBlank { cadena }

fun normalizeChain(chain: String): String {
    val c = chain.trim().uppercase()
    val coded = codeToChain[c]
    if (coded != null) return coded
    for ((code, name) in codeToChain) {
        if (c.length > code.length && c.startsWith(code) && c[code.length].isDigit()) {
            return name
        }
    }
    return when {
        c.startsWith("JUMBO") -> "JUMBO"
        c.startsWith("WALMART") -> "LIDER"
        c.startsWith("LIDER") -> "LIDER"
        c.startsWith("SANTA ISABEL") || c.startsWith("STA") -> "SANTA ISABEL"
        c.startsWith("UNIMARC") -> "UNIMARC"
        c.startsWith("TOTTUS") -> "TOTTUS"
        c.startsWith("PARIS") -> "PARIS"
        c.startsWith("FALABELLA") -> "FALABELLA"
        c.startsWith("ALVI") -> "ALVI"
        c.startsWith("PRONTO") -> "PRONTO"
        else -> c
    }
}

@Composable
fun chainColor(chain: String): Color {
    return chainColors[normalizeChain(chain)] ?: MaterialTheme.colorScheme.secondary
}

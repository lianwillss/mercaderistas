package com.rutamercaderistas.domain.model

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

val chainColorsHex = mapOf(
    "JUMBO" to 0xFF065F46,
    "SANTA ISABEL" to 0xFFDC2626,
    "LIDER" to 0xFF16A34A,
    "TOTTUS" to 0xFF059669,
    "UNIMARC" to 0xFFDC2626,
    "PARIS" to 0xFFEAB308,
    "FALABELLA" to 0xFFEC4899,
    "ALVI" to 0xFF3B82F6,
    "PRONTO" to 0xFF8B5CF6,
    "CENCOSUD" to 0xFF1A56DB,
)

internal val holdingToChains = mapOf(
    "CENCOSUD" to setOf("JUMBO", "SANTA ISABEL", "SPID", "EASY", "PARIS"),
)

private fun inferChainFromName(storeName: String, cadena: String, formato: String): String? {
    if (formato.isNotBlank()) return formato
    val normCadena = normalizeChain(cadena)
    val chains = holdingToChains[normCadena] ?: return cadena
    val name = storeName.uppercase()
    if (normCadena == "CENCOSUD" && name.contains("SISA")) return "SANTA ISABEL"
    for (chain in chains) {
        if (name.contains(chain)) return chain
    }
    return null
}

fun matchesChain(promoChain: String, storeName: String, cadena: String, formato: String): Boolean {
    val resolved = inferChainFromName(storeName, cadena, formato)
    return resolved != null && normalizeChain(promoChain) == normalizeChain(resolved)
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

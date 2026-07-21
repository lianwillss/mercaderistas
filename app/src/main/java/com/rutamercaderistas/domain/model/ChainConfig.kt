package com.rutamercaderistas.domain.model

data class ChainInfo(
    val name: String,
    val prefixes: List<String> = emptyList(),
    val codes: List<String> = emptyList(),
    val holding: String? = null,
    val colorHex: Long = 0xFF6B7280,
)

private val ALL_CHAINS = listOf(
    ChainInfo("JUMBO", prefixes = listOf("JUMBO"), codes = listOf("J"), holding = "CENCOSUD", colorHex = 0xFF065F46),
    ChainInfo("SANTA ISABEL", prefixes = listOf("SANTA ISABEL", "STA ISABEL", "STA"), codes = listOf("N"), holding = "CENCOSUD", colorHex = 0xFFDC2626),
    ChainInfo("LIDER", prefixes = listOf("LIDER", "WALMART"), codes = listOf("EX", "HI"), colorHex = 0xFF16A34A),
    ChainInfo("TOTTUS", prefixes = listOf("TOTTUS"), codes = listOf("TT"), colorHex = 0xFF059669),
    ChainInfo("UNIMARC", prefixes = listOf("UNIMARC"), codes = listOf("UN"), colorHex = 0xFFDC2626),
    ChainInfo("PARIS", prefixes = listOf("PARIS"), codes = listOf("PP"), holding = "CENCOSUD", colorHex = 0xFFEAB308),
    ChainInfo("FALABELLA", prefixes = listOf("FALABELLA"), codes = listOf("FA"), colorHex = 0xFFEC4899),
    ChainInfo("ALVI", prefixes = listOf("ALVI"), codes = listOf("AL"), colorHex = 0xFF3B82F6),
    ChainInfo("PRONTO", prefixes = listOf("PRONTO"), codes = listOf("CO"), colorHex = 0xFF8B5CF6),
    ChainInfo("CENCOSUD", prefixes = listOf("CENCOSUD"), colorHex = 0xFF1A56DB),
    ChainInfo("SPID", prefixes = listOf("SPID"), holding = "CENCOSUD"),
    ChainInfo("EASY", prefixes = listOf("EASY"), holding = "CENCOSUD"),
)

val codeToChain: Map<String, String> = ALL_CHAINS
    .flatMap { chain -> chain.codes.map { it to chain.name } }
    .toMap()

val chainColorsHex: Map<String, Long> = ALL_CHAINS
    .filter { it.colorHex != 0xFF6B7280L }
    .associate { it.name to it.colorHex }

internal val holdingToChains: Map<String, Set<String>> = ALL_CHAINS
    .mapNotNull { chain -> chain.holding?.let { holding -> holding to chain } }
    .groupBy { (holding, _) -> holding }
    .mapValues { (_, pairs) -> pairs.map { (_, chain) -> chain.name }.toSet() }

private val chainPrefixIndex: List<Pair<String, String>> = ALL_CHAINS
    .flatMap { chain -> chain.prefixes.map { it to chain.name } }

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
    for ((prefix, name) in chainPrefixIndex) {
        if (c.startsWith(prefix)) return name
    }
    return c
}

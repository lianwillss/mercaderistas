package com.rutamercaderistas.services

import java.text.Normalizer
import java.util.regex.Pattern

class ColumnMapper {

    companion object {
        fun normalize(text: String): String {
            val nrc = Normalizer.normalize(text, Normalizer.Form.NFD)
            val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
            return pattern.matcher(nrc).replaceAll("")
                .uppercase()
                .trim()
                .replace("\\s+".toRegex(), " ")
        }
    }

    private val mapping = mutableMapOf<String, Int>()

    fun map(headers: List<String>) {
        mapping.clear()
        headers.forEachIndexed { index, header ->
            val normalized = normalize(header)
            if (normalized.isNotBlank()) {
                mapping[normalized] = index
            }
        }
    }

    fun getIndex(vararg aliases: String): Int {
        for (alias in aliases) {
            val normalizedAlias = normalize(alias)
            mapping[normalizedAlias]?.let { return it }
        }
        return -1
    }

    /**
     * Busca columnas cuyo nombre normalizado CONTENGA alguno de los keywords.
     * Útil cuando no sabemos el nombre exacto de la columna.
     */
    fun findFirstContaining(vararg keywords: String): Int {
        for ((key, index) in mapping) {
            for (kw in keywords) {
                val nkw = normalize(kw)
                if (key.contains(nkw)) return index
            }
        }
        return -1
    }

    fun hasRequired(vararg required: String): List<String> {
        val missing = mutableListOf<String>()
        for (req in required) {
            if (getIndex(req) == -1) {
                missing.add(req)
            }
        }
        return missing
    }

    fun getMappingSummary(): String {
        return mapping.toString()
    }
}

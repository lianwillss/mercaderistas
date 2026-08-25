package com.rutamercaderistas.data.network

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMATS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("MM-dd-yy"),
    DateTimeFormatter.ofPattern("MM-dd-yyyy"),
    DateTimeFormatter.ofPattern("dd-MM-yy"),
    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    DateTimeFormatter.ofPattern("MM/dd/yy"),
    DateTimeFormatter.ofPattern("dd/MM/yy"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
)

fun parseCsvLine(line: String): List<String>? {
    if (line.isBlank()) return null
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' -> {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
            }
            (ch == ',' || ch == ';') && !inQuotes -> {
                result.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
        i++
    }
    result.add(current.toString())
    return result
}

fun parseDate(dateStr: String): LocalDate? {
    if (dateStr.isBlank()) return null
    val s = dateStr.trim()
    for (fmt in DATE_FORMATS) {
        try {
            return LocalDate.parse(s, fmt)
        } catch (_: Exception) {
        }
    }
    return null
}

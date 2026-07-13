package com.rutamercaderistas.data.network

import java.time.LocalDate

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
    return try {
        LocalDate.parse(dateStr.trim())
    } catch (_: Exception) {
        null
    }
}

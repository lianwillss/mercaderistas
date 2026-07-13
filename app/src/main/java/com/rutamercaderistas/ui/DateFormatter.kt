package com.rutamercaderistas.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatters {
    private val es = Locale("es")
    val full: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", es)
    val short: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", es)

    fun formatFull(iso: String): String = try {
        LocalDate.parse(iso).format(full)
    } catch (_: Exception) { iso }

    fun formatShort(date: LocalDate): String = date.format(short)
}

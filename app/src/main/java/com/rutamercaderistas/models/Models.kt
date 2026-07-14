package com.rutamercaderistas.models

// ── Marcas consideradas prioritarias ───────────────────────────────
val MARCAS_PRIORITARIAS = setOf(
    "CASO Y CIA",
    "CUK",
    "SUK",
    "KOMBUCHA",
    "ECOCULTIVA",
    "NAT NATURAL",
    "OLIMPIA",
    "FRANUI"
)

// Representa una fila leída del Excel
data class EntradaRuta(
    val reponedor: String,
    val rutero: String,
    val codigo: String,
    val local: String,
    val direccion: String,
    val cliente: String,
    val cadena: String = "",
    val formato: String = "",
    val region: String = "",
    val comuna: String = "",
    val supervisor: String = "",
    val gestores: String = "",
    val modalidad: String = "",
    val equipo: String = "",
    val lunes: Boolean = false,
    val martes: Boolean = false,
    val miercoles: Boolean = false,
    val jueves: Boolean = false,
    val viernes: Boolean = false,
    val sabado: Boolean = false,
    val domingo: Boolean = false
) {
    // Cuántos días a la semana se visita esta marca en este local
    val frecuencia: Int get() =
        listOf(lunes, martes, miercoles, jueves, viernes, sabado, domingo).count { it }

    val esPrioritaria: Boolean get() {
        val limpio = clienteLimpio.uppercase()
        return MARCAS_PRIORITARIAS.any { marca ->
            limpio == marca ||
            limpio.startsWith("$marca ") ||
            limpio.contains(" $marca ") ||
            limpio.endsWith(" $marca")
        }
    }

    // Elimina el ⭐ si viene del Excel
    val clienteLimpio: String get() =
        cliente.replace("⭐ ", "").trim()
}

// ── Agrupa los clientes de un local para un día específico ─────────
data class LocalDelDia(
    val codigo: String,
    val local: String,
    val direccion: String,
    val comuna: String = "",
    val region: String = "",
    val cadena: String = "",
    val formato: String = "",
    val supervisor: String = "",
    val gestores: String = "",
    val modalidad: String = "",
    val equipo: String = "",
    val reponedor: String = "",
    val clientes: List<ClienteInfo>
) {
    val totalClientes: Int get() = clientes.size
}

// ── Información de un cliente/marca dentro de un local ─────────────
data class ClienteInfo(
    val nombre: String,
    val esPrioritaria: Boolean,
    val frecuencia: Int
) {
    val frecuenciaTexto: String get() = when (frecuencia) {
        7    -> "Todos los días"
        6    -> "6 días/sem"
        5    -> "5 días/sem"
        4    -> "4 días/sem"
        3    -> "3 días/sem"
        2    -> "2 días/sem"
        1    -> "1 día/sem"
        else -> ""
    }
}

// ── Enum de días de la semana ───────────────────────────────────────
enum class DiaSemana(val abreviacion: String, val nombreCompleto: String) {
    LUNES("LUN", "Lunes"),
    MARTES("MAR", "Martes"),
    MIERCOLES("MIE", "Miércoles"),
    JUEVES("JUE", "Jueves"),
    VIERNES("VIE", "Viernes"),
    SABADO("SAB", "Sábado"),
    DOMINGO("DOM", "Domingo");

    companion object {
        fun todos(): List<DiaSemana> = values().toList()
    }
}

private val PREPS = setOf("y", "de", "del", "la", "los", "las", "en", "a", "el", "por", "con", "sin", "su")

fun String.toNaturalCase(): String {
    if (isBlank()) return this
    return split(" ").joinToString(" ") { word ->
        when {
            word.isBlank() -> word
            word == "S/N" || word == "S/C" -> word
            word.lowercase() in PREPS -> word.lowercase()
            else -> word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryEntity

/**
 * Qué parte del rutero se muestra y se descarga.
 *
 * El mercaderista trabaja una o pocas rutas; bajar la RM completa (296 locales)
 * solo tiene sentido para un supervisor o para una revisión puntual. El alcance
 * vive separado de [StockFilters] porque no es un filtro de la lista: decide qué
 * datos se piden a KPI One y qué locales ofrece el catálogo.
 */
enum class StockScope(val etiqueta: String) {
    MIS_RUTAS("Mis rutas"),
    RM_COMPLETA("RM completa"),
    ;

    companion object {
        fun leer(valor: String?): StockScope =
            entries.firstOrNull { it.name.equals(valor, ignoreCase = true) } ?: MIS_RUTAS
    }
}

/** Rutas visibles/descargables y por qué quedaron así. */
data class StockAlcance(
    val scope: StockScope = StockScope.MIS_RUTAS,
    val rutas: Set<String> = emptySet(),
) {
    /** true cuando hay rutas concretas que acotan catálogo, búsqueda y descarga. */
    val limita: Boolean get() = scope == StockScope.MIS_RUTAS && rutas.isNotEmpty()

    /** Clave que se guarda en `stock-meta.json` para no mezclar alcances en el QC. */
    val claveSnapshot: String
        get() = when {
            scope == StockScope.RM_COMPLETA -> SCOPE_RM
            rutas.isEmpty() -> SCOPE_RM
            else -> claveAlcanceRutas(rutas)
        }

    val resumen: String
        get() = when {
            scope == StockScope.RM_COMPLETA -> "Toda la Región Metropolitana"
            rutas.isEmpty() -> "Sin rutero activo: se muestra RM completa"
            rutas.size == 1 -> "1 ruta: " + rutas.first()
            else -> rutas.size.toString() + " rutas · " +
                rutas.sorted().take(3).joinToString(", ") +
                if (rutas.size > 3) "…" else ""
        }

    companion object {
        const val SCOPE_RM = "rm"
        const val SCOPE_ALL = "all"
        const val PREFIJO_RUTAS = "rutas:"
    }
}

/** "rutas:EMU-1,EMU-3" — estable para comparar el alcance de dos snapshots. */
fun claveAlcanceRutas(rutas: Collection<String>): String =
    StockAlcance.PREFIJO_RUTAS + rutas
        .map { normalizarRuta(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .joinToString(",")

/**
 * Rutas que le corresponden a la persona del rutero activo.
 *
 * Se parte del rutero seleccionado y se suman las rutas que comparten sus
 * gestores. Si el rutero no trae gestores, se usa el supervisor como respaldo.
 * Así un mercaderista ve su ruta y un supervisor las rutas de su equipo, sin
 * abrir la RM entera. Sin datos de persona se devuelve solo el rutero activo.
 */
fun rutasDeLaPersona(
    entries: List<RouteEntryEntity>,
    ruteroActivo: String?,
): Set<String> {
    val activo = ruteroActivo?.trim().orEmpty()
    if (activo.isEmpty() || entries.isEmpty()) return emptySet()

    val propias = entries.filter { it.rutero.trim().equals(activo, ignoreCase = true) }
    if (propias.isEmpty()) return emptySet()

    val gestores = propias.flatMap { tokensDeGestores(it.gestores) }.toSet()
    val supervisores = propias
        .map { normalizarPersona(it.supervisor) }
        .filter { it.isNotEmpty() }
        .toSet()

    val grupo = entries.filter { entry ->
        val mismaRuta = entry.rutero.trim().equals(activo, ignoreCase = true)
        if (mismaRuta) return@filter true
        if (gestores.isNotEmpty()) {
            tokensDeGestores(entry.gestores).any { it in gestores }
        } else {
            supervisores.isNotEmpty() &&
                normalizarPersona(entry.supervisor) in supervisores
        }
    }

    return grupo
        .map { it.rutero.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

/** Separa listas de nombres ("Juan / Pedro, Ana") en tokens comparables. */
private val SEPARADORES_GESTORES = Regex("[,;/|]+")

// Compilado una sola vez: corre por cada fila del rutero.
private val ESPACIOS_DOBLES = Regex("\\s+")

private fun tokensDeGestores(valor: String): List<String> =
    valor.split(SEPARADORES_GESTORES)
        .map { normalizarPersona(it) }
        .filter { it.isNotEmpty() }

private fun normalizarPersona(valor: String): String =
    normalizarStock(valor)
        .replace(ESPACIOS_DOBLES, " ")
        .trim()

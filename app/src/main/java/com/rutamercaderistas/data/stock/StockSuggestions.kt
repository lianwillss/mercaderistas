package com.rutamercaderistas.data.stock

import com.rutamercaderistas.utils.levenshtein

/**
 * Una opción de los desplegables de búsqueda (local, ruta o marca).
 *
 * [valor] es lo que se aplica al filtro; [claves] es todo el texto contra el
 * que se compara (p. ej. código + nombre del local, para que se encuentre por
 * cualquiera de los dos).
 */
data class StockSugerencia(
    val valor: String,
    val etiqueta: String,
    val claves: String = etiqueta,
)

/** Sugerencias ya ordenadas por relevancia y el total real de coincidencias. */
data class SugerenciasStock(
    val items: List<StockSugerencia> = emptyList(),
    val total: Int = 0,
)

/** Opciones del combo de locales: se busca por código y por nombre. */
fun opcionesDeLocales(locales: List<StockLocal>): List<StockSugerencia> =
    locales.map {
        StockSugerencia(valor = it.code, etiqueta = it.label, claves = it.code + " " + it.name)
    }

fun opcionesDeMarcas(marcas: List<String>): List<StockSugerencia> =
    marcas.map { StockSugerencia(valor = it, etiqueta = it) }

fun opcionesDeRutas(rutas: List<String>): List<StockSugerencia> =
    rutas.map {
        StockSugerencia(
            valor = it,
            etiqueta = it,
            claves = it + " " + normalizarRuta(it) + " " + it.replace("-", " "),
        )
    }

/**
 * Ordena las sugerencias por relevancia (no alfabéticamente):
 * valor exacto → valor/etiqueta por prefijo → inicio de palabra → contiene
 * (normal o compacto) → error de tipeo. Desempata por [usos] (más usado
 * primero) y recién después alfabéticamente; devuelve como máximo [limit]
 * filas; [SugerenciasStock.total] trae el total real para la cabecera.
 *
 * [usos] es el mapa valor → veces elegidas del historial: desempata DESPUÉS de
 * la relevancia, sin pisar exacto > prefijo > contiene > typo.
 */
fun sugerenciasStock(
    consulta: String,
    opciones: List<StockSugerencia>,
    limit: Int = LIMITE_SUGERENCIAS,
    usos: Map<String, Int> = emptyMap(),
): SugerenciasStock {
    val q = ConsultaStock(consulta)
    if (q.vacia) return SugerenciasStock(opciones.take(limit), opciones.size)
    val puntuadas = opciones.mapNotNull { opcion ->
        puntajeSugerencia(q, opcion)?.let { it to opcion }
    }
    return SugerenciasStock(
        items = puntuadas
            .sortedWith(
                compareBy(
                    { it.first },
                    // Más usos primero: el signo negativo ordena descendente.
                    { -(usos[it.second.valor] ?: 0) },
                    { normalizarStock(it.second.etiqueta) },
                ),
            )
            .take(limit)
            .map { it.second },
        total = puntuadas.size,
    )
}

/** Menor es más relevante; null = no coincide. */
private fun puntajeSugerencia(q: ConsultaStock, opcion: StockSugerencia): Int? {
    val valor = normalizarStock(opcion.valor)
    val etiqueta = normalizarStock(opcion.etiqueta)
    val claves = normalizarStock(opcion.claves)
    val valorCompacto = normalizarRuta(opcion.valor)
    val clavesCompactas = normalizarRuta(opcion.claves)

    if (q.normal == valor) return 0
    if (q.normal == etiqueta) return 1
    if (valor.startsWith(q.normal) ||
        (q.compacto.isNotEmpty() && valorCompacto.startsWith(q.compacto))
    ) {
        return 2
    }
    if (etiqueta.startsWith(q.normal)) return 3
    if (claves.contains(q.normal)) return 4
    if (q.compacto.isNotEmpty() && clavesCompactas.contains(q.compacto)) return 4
    if (coincideAproximado(q, claves)) return 5
    return null
}

/**
 * Tolerancia a errores de tipeo: todos los tokens de la consulta deben tener un
 * token cercano en el texto. Se usa sólo en las sugerencias (listas chicas); la
 * lista de resultados exige coincidencia exacta/compacta para no inflarse.
 */
private fun coincideAproximado(q: ConsultaStock, texto: String): Boolean {
    if (q.normal.length < 3) return false
    val tokensConsulta = q.normal.split(' ').filter { it.isNotEmpty() }
    val tokensTexto = normalizarStock(texto).split(' ').filter { it.isNotEmpty() }
    if (tokensConsulta.isEmpty() || tokensTexto.isEmpty()) return false
    return tokensConsulta.all { consultaToken ->
        val maxDist = when {
            consultaToken.length <= 3 -> 0
            consultaToken.length <= 6 -> 1
            else -> 2
        }
        tokensTexto.any { textoToken ->
            textoToken == consultaToken || levenshtein(consultaToken, textoToken) <= maxDist
        }
    }
}

/** Cuántas sugerencias se muestran a la vez en el desplegable. */
const val LIMITE_SUGERENCIAS = 8

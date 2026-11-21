package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryEntity

/**
 * Resolución de una ruta del rutero a códigos de local.
 *
 * Es una dimensión independiente de local, marca y SKU: dada una ruta devuelve
 * sus códigos y no toca ningún otro filtro. La comparación normaliza tildes,
 * mayúsculas, espacios y guiones, y acepta coincidencia exacta o, con 3+
 * caracteres, prefijo ("emu" → EMU-1…EMU-7; "em" no, para no arrastrar DMU).
 */
fun codigosDeRuta(entradas: List<RouteEntryEntity>, ruta: String): List<String> {
    val buscada = normalizarRuta(ruta)
    // PITUTO y PROPAL están dados de baja del módulo: no resuelven consulta.
    if (buscada.isBlank() || rutaExcluida(ruta)) return emptyList()
    val exactas = entradas.filter { normalizarRuta(it.rutero) == buscada }
    val elegidas = when {
        exactas.isNotEmpty() -> exactas
        buscada.length >= 3 -> entradas.filter { normalizarRuta(it.rutero).startsWith(buscada) }
        else -> emptyList()
    }
    return elegidas.map { it.codigo.trim().uppercase() }.distinct()
}

/**
 * Misma resolución para el snapshot local, que guarda las rutas en cada local
 * en vez de una tabla del rutero.
 */
fun codigosDeRuta(locales: List<StockLocal>, ruta: String): Set<String> {
    val buscada = normalizarRuta(ruta)
    if (buscada.isBlank() || rutaExcluida(ruta)) return emptySet()
    return locales
        .filter { local ->
            local.rutas.any { nombre ->
                val normalizado = normalizarRuta(nombre)
                normalizado == buscada ||
                    (buscada.length >= 3 && normalizado.startsWith(buscada))
            }
        }
        .map { it.code }
        .toSet()
}

package com.rutamercaderistas.data.stock

import kotlinx.serialization.Serializable

/**
 * Historial de selecciones del buscador de Stock.
 *
 * Guarda las SELECCIONES de Local/Ruta/Marca (las que el usuario elige del
 * combo o aplica desde una sugerencia del estado vacío), nunca cada tecla.
 * Es la base del desempate por frecuencia y de la sección "Recientes" del
 * combo. La lógica vive acá, sin Android, para poder probarla en JVM puro.
 */
@Serializable
data class StockSeleccion(
    /** Valor canónico que se aplica al filtro: "J503", "EMU-3", "CASO Y CIA". */
    val valor: String,
    /** Veces que se eligió; desempata las sugerencias tras la relevancia. */
    val usos: Int = 1,
    /** Epoch millis de la última elección; ordena "Recientes". */
    val ultimoUso: Long = 0L,
)

/** Tipo de campo del historial. */
@Serializable
enum class TipoBusquedaStock { LOCAL, RUTA, MARCA }

/** Estado persistente del historial de búsquedas de Stock (preferencia versionada). */
@Serializable
data class StockBusquedas(
    val version: Int = 1,
    val locales: List<StockSeleccion> = emptyList(),
    val rutas: List<StockSeleccion> = emptyList(),
    val marcas: List<StockSeleccion> = emptyList(),
) {
    fun lista(tipo: TipoBusquedaStock): List<StockSeleccion> = when (tipo) {
        TipoBusquedaStock.LOCAL -> locales
        TipoBusquedaStock.RUTA -> rutas
        TipoBusquedaStock.MARCA -> marcas
    }

    fun conLista(tipo: TipoBusquedaStock, lista: List<StockSeleccion>): StockBusquedas =
        when (tipo) {
            TipoBusquedaStock.LOCAL -> copy(locales = lista)
            TipoBusquedaStock.RUTA -> copy(rutas = lista)
            TipoBusquedaStock.MARCA -> copy(marcas = lista)
        }

    val total: Int get() = locales.size + rutas.size + marcas.size
}

/** Tope por tipo: con 3 tipos el total queda naturalmente en 24. */
const val TOPE_BUSQUEDAS_POR_TIPO = 8

/** Tope global de defensa (no se alcanza con el tope por tipo). */
const val TOPE_BUSQUEDAS_TOTAL = 24

/** Cuántas "Recientes" muestra el combo con el campo vacío. */
const val RECIENTES_EN_COMBO = 5

/**
 * Registra una selección sin bloquear la UI: incrementa [StockSeleccion.usos],
 * actualiza [StockSeleccion.ultimoUso], mantiene el orden por recencia y aplica
 * los topes por tipo y total. Devuelve el estado nuevo (inmutable).
 */
fun registrarSeleccion(
    stock: StockBusquedas,
    tipo: TipoBusquedaStock,
    valor: String,
    ahora: Long,
    topePorTipo: Int = TOPE_BUSQUEDAS_POR_TIPO,
    topeTotal: Int = TOPE_BUSQUEDAS_TOTAL,
): StockBusquedas {
    val elegido = valor.trim()
    if (elegido.isEmpty()) return stock
    val lista = stock.lista(tipo)
    val existe = lista.any { it.valor == elegido }
    val nueva = if (existe) {
        lista.map {
            if (it.valor == elegido) it.copy(usos = it.usos + 1, ultimoUso = ahora) else it
        }
    } else {
        lista + StockSeleccion(valor = elegido, usos = 1, ultimoUso = ahora)
    }
    // Recencia primero; a igual recencia, más usos primero.
    val ordenada = nueva.sortedWith(
        compareByDescending<StockSeleccion> { it.ultimoUso }
            .thenByDescending { it.usos }
            .thenBy { it.valor },
    )
    var resultado = stock.conLista(tipo, ordenada.take(topePorTipo))

    // Tope global: evacuar las más viejas (menos usadas a igual antigüedad).
    while (resultado.total > topeTotal) {
        val candidato = TipoBusquedaStock.entries
            .filter { resultado.lista(it).isNotEmpty() }
            .mapNotNull { t ->
                resultado.lista(t).minByOrNull { it.ultimoUso }?.let { t to it }
            }
            .minWithOrNull(
                compareBy<Pair<TipoBusquedaStock, StockSeleccion>> { it.second.ultimoUso }
                    .thenBy { it.second.usos },
            )
            ?: break
        resultado = resultado.conLista(
            candidato.first,
            resultado.lista(candidato.first).filterNot { it === candidato.second },
        )
    }
    return resultado
}

/**
 * Recientes de un tipo, ordenadas por recencia (y uso) para la sección
 * "Recientes" del combo. No devuelve más de [limite] filas.
 */
fun recientesBusqueda(
    stock: StockBusquedas,
    tipo: TipoBusquedaStock,
    limite: Int = RECIENTES_EN_COMBO,
): List<StockSeleccion> = stock.lista(tipo).take(limite)

/**
 * Mapa valor → usos de un tipo, para desempatar las sugerencias del combo.
 * La clave es el valor canónico de la selección, igual al [com.rutamercaderistas.data.stock.StockSugerencia.valor]
 * de las opciones del combo.
 */
fun usosPorTipo(stock: StockBusquedas, tipo: TipoBusquedaStock): Map<String, Int> =
    stock.lista(tipo).associate { it.valor to it.usos }

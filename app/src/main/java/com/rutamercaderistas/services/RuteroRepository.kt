package com.rutamercaderistas.services

import android.util.Log
import com.rutamercaderistas.models.ClienteInfo
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.models.toNaturalCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RuteroRepository Optimizado: Maneja únicamente los datos de la ruta ACTIVA.
 */
object RuteroRepository {
    private const val TAG = "RuteroRepository"

    private val _entriesFlow = MutableStateFlow<List<EntradaRuta>>(emptyList())
    val entriesFlow: StateFlow<List<EntradaRuta>> = _entriesFlow.asStateFlow()

    private var activeRuteroName: String? = null
    private var cachedStats: Stats? = null

    /**
     * Establece los datos de la ruta seleccionada.
     */
    fun setEntries(entries: List<EntradaRuta>, ruteroName: String) {
        activeRuteroName = ruteroName
        _entriesFlow.value = entries
        cachedStats = computeStats(entries)
        Log.d(TAG, "ROUTE_LOADED: $ruteroName con ${entries.size} registros")
    }

    fun getActiveRuteroName() = activeRuteroName

    /**
     * Limpia absolutamente todos los datos en memoria.
     */
    fun clear() {
        activeRuteroName = null
        cachedStats = null
        _entriesFlow.value = emptyList()
        Log.d(TAG, "REPOSITORY_CLEARED: Memoria del repositorio vaciada")
    }

    /**
     * Procesa los locales para el día seleccionado de la ruta activa.
     */
    fun getLocalesForDay(dia: DiaSemana): List<LocalDelDia> {
        val allEntries = _entriesFlow.value
        if (allEntries.isEmpty()) return emptyList()

        return allEntries
            .filter { isAssignedForDay(it, dia) }
            .groupBy { it.codigo + it.local }
            .map { (_, entries) ->
                val first = entries.first()
                LocalDelDia(
                    codigo = first.codigo,
                    local = first.local.toNaturalCase(),
                    direccion = first.direccion.toNaturalCase(),
                    cadena = first.cadena,
                    formato = first.formato,
                    region = first.region,
                    comuna = first.comuna,
                    clientes = entries.map { entry ->
                        ClienteInfo(
                            nombre = entry.cliente,
                            esPrioritaria = entry.esPrioritaria,
                            frecuencia = entry.frecuencia
                        )
                    }.sortedByDescending { it.esPrioritaria }
                )
            }
    }

    private fun isAssignedForDay(entry: EntradaRuta, dia: DiaSemana): Boolean {
        return when (dia) {
            DiaSemana.LUNES -> entry.lunes
            DiaSemana.MARTES -> entry.martes
            DiaSemana.MIERCOLES -> entry.miercoles
            DiaSemana.JUEVES -> entry.jueves
            DiaSemana.VIERNES -> entry.viernes
            DiaSemana.SABADO -> entry.sabado
            DiaSemana.DOMINGO -> entry.domingo
        }
    }

    fun hasAnyVisitOnDay(dia: DiaSemana): Boolean {
        return _entriesFlow.value.any { isAssignedForDay(it, dia) }
    }

    fun getStats(): Stats {
        return cachedStats ?: computeStats(_entriesFlow.value).also { cachedStats = it }
    }

    fun getAllLocales(): List<LocalDelDia> {
        val allEntries = _entriesFlow.value
        if (allEntries.isEmpty()) return emptyList()
        return allEntries
            .groupBy { it.codigo + it.local }
            .map { (_, entries) ->
                val first = entries.first()
                LocalDelDia(
                    codigo = first.codigo,
                    local = first.local.toNaturalCase(),
                    direccion = first.direccion.toNaturalCase(),
                    cadena = first.cadena,
                    formato = first.formato,
                    region = first.region,
                    comuna = first.comuna,
                    clientes = entries.map { entry ->
                        ClienteInfo(
                            nombre = entry.cliente,
                            esPrioritaria = entry.esPrioritaria,
                            frecuencia = entry.frecuencia,
                        )
                    }.sortedByDescending { it.esPrioritaria },
                )
            }
    }

    private fun computeStats(entries: List<EntradaRuta>): Stats {
        val localesUnicos = entries.distinctBy { it.codigo + it.local }.size
        val marcasTotales = entries.distinctBy { it.cliente }.size
        return Stats(localesUnicos, marcasTotales, entries.size)
    }

    data class Stats(
        val totalLocales: Int,
        val totalMarcas: Int,
        val visitasTotales: Int
    )
}

package com.rutamercaderistas.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.export.RouteExporter
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class RouteUiState(
    val routes: List<String> = emptyList(),
    val selectedRoute: String? = null,
    val entries: List<EntradaRuta> = emptyList(),
    val activeDays: List<DiaSemana> = emptyList(),
    val currentDayLocales: List<LocalDelDia> = emptyList(),
    val allLocales: List<LocalDelDia> = emptyList(),
    val stats: RuteroRepository.Stats = RuteroRepository.Stats(0, 0, 0),
    val recentRoutes: List<String> = emptyList(),
    val lastSyncRelative: String = "",
    val isSyncing: Boolean = false,
    val isDataLoaded: Boolean = false,
)

@HiltViewModel
class RouteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruteroManager: RuteroManager,
    private val recentRoutesStore: RecentRoutesStore,
    private val routeExporter: RouteExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    init {
        observeRoutes()
        observeRecentRoutes()
        observeEntries()
    }

    private fun observeRoutes() {
        viewModelScope.launch {
            ruteroManager.ruterosFlow.collect { routes ->
                _uiState.value = _uiState.value.copy(routes = routes)
            }
        }
    }

    private fun observeRecentRoutes() {
        viewModelScope.launch {
            recentRoutesStore.recentRoutesFlow.collect { recent ->
                _uiState.value = _uiState.value.copy(recentRoutes = recent)
            }
        }
    }

    private fun observeEntries() {
        viewModelScope.launch {
            RuteroRepository.entriesFlow.collect { entries ->
                val activeDays = if (entries.isNotEmpty()) {
                    DiaSemana.todos().filter { RuteroRepository.hasAnyVisitOnDay(it) }
                } else emptyList()

                val stats = RuteroRepository.getStats()
                val allLocales = RuteroRepository.getAllLocales()

                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    activeDays = activeDays,
                    stats = stats,
                    allLocales = allLocales,
                    isDataLoaded = entries.isNotEmpty(),
                )
            }
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            val index = withContext(Dispatchers.IO) { ruteroManager.loadIndex() }
            if (index.isNotEmpty()) {
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val lastRoute = prefs.getString(Constants.KEY_RUTERO, null)
                if (lastRoute != null && index.contains(lastRoute)) {
                    selectRoute(lastRoute)
                }
            }
            updateSyncLabel()
        }
    }

    fun selectRoute(rutero: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSyncing = true)
                val entries = ruteroManager.loadRoute(rutero)
                if (entries.isNotEmpty()) {
                    prefs.edit().putString(Constants.KEY_RUTERO, rutero).apply()
                    RuteroRepository.setEntries(entries, rutero)
                    recentRoutesStore.addRoute(rutero)
                    _uiState.value = _uiState.value.copy(
                        selectedRoute = rutero,
                        isSyncing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }

    fun setCurrentDay(dia: DiaSemana?) {
        if (dia == null) return
        val locales = RuteroRepository.getLocalesForDay(dia)
        _uiState.value = _uiState.value.copy(currentDayLocales = locales)
    }

    fun updateSyncLabel() {
        val excelFile = File(context.filesDir, RuteroManager.EXCEL_FILE_NAME)
        val label = if (!excelFile.exists()) "hoy"
        else {
            val modified = Instant.ofEpochMilli(excelFile.lastModified())
            val now = Instant.now()
            val minutes = ChronoUnit.MINUTES.between(modified, now)
            val hours = ChronoUnit.HOURS.between(modified, now)
            val days = ChronoUnit.DAYS.between(modified, now)
            when {
                minutes < 1 -> "ahora"
                minutes < 60 -> "hace $minutes min"
                hours < 24 -> "hace $hours h"
                else -> "hace $days d"
            }
        }
        _uiState.value = _uiState.value.copy(lastSyncRelative = label)
    }

    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getShareText(): String {
        val s = _uiState.value
        val routeName = s.selectedRoute ?: "Ruta"
        val lines = mutableListOf<String>()
        lines.add("\uD83D\uDCCB Ruta: $routeName")
        lines.add("")
        s.currentDayLocales.forEach { local ->
            val brands = local.clientes.take(3).joinToString(", ") { it.nombre }
            lines.add("\uD83D\uDCCD ${local.codigo} - ${local.local}")
            if (local.direccion.isNotBlank()) lines.add("   ${local.direccion}")
            if (brands.isNotBlank()) lines.add("   \uD83C\uDFF7 $brands")
            lines.add("")
        }
        lines.add("Mercaderistas app")
        return lines.joinToString("\n")
    }

    fun exportRoute() {
        val s = _uiState.value
        val name = s.selectedRoute ?: return
        routeExporter.exportAsImage(
            routeName = name,
            entries = s.entries,
        ) { file ->
            routeExporter.shareImage(file, name)
        }
    }
}

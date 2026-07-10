package com.rutamercaderistas.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.export.RouteExporter
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.services.PromotionRepository
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.utils.cleanBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class PromotionDiagnostic(
    val lastUpdated: String = "",
    val totalPromos: Int = 0,
    val distinctBrands: Int = 0,
    val first20: String = "",
    val currentLocaleResult: String = "",
)

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
    val promotionsByBrand: Map<String, List<PromotionEntity>> = emptyMap(),
    val isSyncing: Boolean = false,
    val isDataLoaded: Boolean = false,
    val snackbarMessage: String? = null,
    val needsInitialLoad: Boolean = false,
    val promotionDiagnostic: PromotionDiagnostic? = null,
)

@HiltViewModel
class RouteViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruteroManager: RuteroManager,
    private val recentRoutesStore: RecentRoutesStore,
    private val routeExporter: RouteExporter,
    private val repository: RuteroRepository,
    private val promotionRepository: PromotionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    init {
        observeRoutes()
        observeRecentRoutes()
        observeEntries()
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("=== DIAG: Cargando promociones en init ===")
            val ok = promotionRepository.refresh()
            val allPromos = promotionRepository.getAllPromotions()
            Timber.d("DIAG: refresh() = %b, promos en Room = %d", ok, allPromos.size)
            val grouped = allPromos.groupBy { it.brand.cleanBrand() }
            Timber.d("DIAG: marcas distintas agrupadas = %d", grouped.size)
            grouped.forEach { (brand, list) ->
                Timber.d("DIAG:   %s → %d promos", brand, list.size)
            }
            _uiState.value = _uiState.value.copy(
                promotionsByBrand = grouped,
            )
        }
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
            repository.entriesFlow.collect { entries ->
                val activeDays = if (entries.isNotEmpty()) {
                    DiaSemana.todos().filter { repository.hasAnyVisitOnDay(it) }
                } else emptyList()

                val stats = repository.getStats()
                val allLocales = repository.getAllLocales()

                val promotions = withContext(Dispatchers.IO) {
                    Timber.d("=== DIAG: observeEntries — recargando promos ===")
                    promotionRepository.refresh()
                    val allPromos = promotionRepository.getAllPromotions()
                    Timber.d("DIAG: promos en Room = %d", allPromos.size)
                    val grouped = allPromos.groupBy { it.brand.cleanBrand() }
                    Timber.d("DIAG: marcas agrupadas = %d", grouped.size)
                    grouped
                }

                Timber.d("=== DIAG: Cruce con locales ===")
                allLocales.forEach { local ->
                    val cadena = local.cadena
                    Timber.d("DIAG: Local=\"%s\" cadena=\"%s\"", local.local, cadena)
                    local.clientes.forEach { cliente ->
                        val cleanName = cliente.nombre.cleanBrand()
                        val matched = promotions[cleanName].orEmpty()
                            .filter { cadena.isBlank() || it.chain.equals(cadena, ignoreCase = true) }
                        Timber.d("DIAG:   Marca=\"%s\" (clean=\"%s\") → %d promos",
                            cliente.nombre, cleanName, matched.size)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    activeDays = activeDays,
                    stats = stats,
                    allLocales = allLocales,
                    selectedRoute = repository.getActiveRuteroName(),
                    promotionsByBrand = promotions,
                    isDataLoaded = entries.isNotEmpty(),
                    needsInitialLoad = false,
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
                val route = if (lastRoute != null && index.contains(lastRoute)) lastRoute
                else index.first()
                selectRoute(route)
            } else {
                val excelFile = File(context.filesDir, RuteroManager.EXCEL_FILE_NAME)
                if (!excelFile.exists()) {
                    _uiState.value = _uiState.value.copy(needsInitialLoad = true)
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
                    repository.setEntries(entries, rutero)
                    recentRoutesStore.addRoute(rutero)
                    val stats = repository.getStats()
                    val allLocales = repository.getAllLocales()
                    _uiState.value = _uiState.value.copy(
                        selectedRoute = rutero,
                        stats = stats,
                        allLocales = allLocales,
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
        val locales = repository.getLocalesForDay(dia)
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
        val name = s.selectedRoute ?: run {
            _uiState.value = _uiState.value.copy(snackbarMessage = "Selecciona una ruta primero")
            return
        }
        viewModelScope.launch {
            try {
                val file = routeExporter.exportAsImage(name, s.entries, s.stats)
                try {
                    routeExporter.shareImage(file, name)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(snackbarMessage = "Error al compartir: ${e.message}")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(snackbarMessage = "Error al exportar: ${e.message}")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun loadPromotionDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.d("=== DIAG: Cargando diagnóstico ===")
            val allPromos = promotionRepository.getAllPromotions()
            val lastUpdated = promotionRepository.getLastUpdated()
            val distinctBrands = allPromos.map { it.brand.cleanBrand() }.distinct().size
            val first20 = allPromos.take(20).joinToString("\n") { p ->
                "  [${p.brand}] ${p.productName} — ${p.price} (${p.startDate} → ${p.endDate})"
            }

            val s = _uiState.value
            val cadena = if (s.currentDayLocales.isNotEmpty()) s.currentDayLocales.first().cadena else ""
            val currentLocaleResult = s.currentDayLocales.take(3).joinToString("\n\n") { local ->
                val lines = mutableListOf("• ${local.local} (cadena: \"${local.cadena}\")")
                local.clientes.forEach { cliente ->
                    val clean = cliente.nombre.cleanBrand()
                    val promos = s.promotionsByBrand[clean].orEmpty()
                        .filter { local.cadena.isBlank() || it.chain.equals(local.cadena, ignoreCase = true) }
                    lines.add("    ${cliente.nombre} → ${promos.size} promos")
                    promos.forEach { p ->
                        lines.add("      - ${p.productName}")
                    }
                }
                lines.joinToString("\n")
            }

            val lastUpdatedStr = if (lastUpdated > 0) {
                val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                f.format(java.util.Date(lastUpdated))
            } else "Nunca"

            _uiState.update {
                it.copy(promotionDiagnostic = PromotionDiagnostic(
                    lastUpdated = lastUpdatedStr,
                    totalPromos = allPromos.size,
                    distinctBrands = distinctBrands,
                    first20 = first20,
                    currentLocaleResult = currentLocaleResult,
                ))
            }
        }
    }

    fun clearDiagnostic() {
        _uiState.update { it.copy(promotionDiagnostic = null) }
    }
}

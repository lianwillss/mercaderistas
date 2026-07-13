package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.data.local.PromotionEntity
import com.rutamercaderistas.data.preferences.FileRepository
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.domain.usecase.ComputeChainToLocalesUseCase
import com.rutamercaderistas.domain.usecase.ComputeRouteBrandsUseCase
import com.rutamercaderistas.domain.usecase.GroupPromotionsUseCase
import com.rutamercaderistas.domain.usecase.GroupedPromotions
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.models.LocalDelDia
import com.rutamercaderistas.services.PromotionRepository
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import com.rutamercaderistas.ui.components.normalizeChain
import com.rutamercaderistas.utils.normalizeBrand
import com.rutamercaderistas.utils.cleanBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class PromotionDiagnostic(
    val lastUpdated: String = "",
    val totalPromos: Int = 0,
    val distinctBrands: Int = 0,
    val first20: String = "",
    val chainCatalog: String = "",
    val currentLocaleResult: String = "",
)

sealed interface RouteDataState {
    data object Initial : RouteDataState
    data class Loaded(
        val selectedRoute: String?,
        val entries: List<EntradaRuta>,
        val activeDays: List<DiaSemana>,
        val currentDayLocales: List<LocalDelDia>,
        val allLocales: List<LocalDelDia>,
        val stats: RuteroRepository.Stats,
    ) : RouteDataState
}

sealed interface PromotionsState {
    data object Idle : PromotionsState
    data object Loading : PromotionsState
    data class Loaded(
        val byBrand: Map<String, List<PromotionEntity>> = emptyMap(),
        val marcasConPromo: Int = 0,
        val totalPromosActivas: Int = 0,
        val promosExpiringToday: Int = 0,
        val promosExpiringTomorrow: Int = 0,
        val promosExpiringSoon: List<PromotionEntity> = emptyList(),
        val error: String? = null,
    ) : PromotionsState
}

data class RouteUiState(
    val routes: List<String> = emptyList(),
    val recentRoutes: List<String> = emptyList(),
    val route: RouteDataState = RouteDataState.Initial,
    val promotions: PromotionsState = PromotionsState.Idle,
    val chainToLocales: Map<String, String> = emptyMap(),
    val routeBrands: Set<String> = emptySet(),
    val lastSyncRelative: String = "",
    val snackbarMessage: String? = null,
    val needsInitialLoad: Boolean = false,
    val promotionDiagnostic: PromotionDiagnostic? = null,
) {
    val isDataLoaded: Boolean get() = route is RouteDataState.Loaded
    val selectedRoute: String? get() = (route as? RouteDataState.Loaded)?.selectedRoute
    val entries: List<EntradaRuta> get() = (route as? RouteDataState.Loaded)?.entries ?: emptyList()
    val activeDays: List<DiaSemana> get() = (route as? RouteDataState.Loaded)?.activeDays ?: emptyList()
    val currentDayLocales: List<LocalDelDia> get() = (route as? RouteDataState.Loaded)?.currentDayLocales ?: emptyList()
    val allLocales: List<LocalDelDia> get() = (route as? RouteDataState.Loaded)?.allLocales ?: emptyList()
    val stats: RuteroRepository.Stats get() = (route as? RouteDataState.Loaded)?.stats ?: RuteroRepository.Stats(0, 0, 0)
    val isPromotionsLoading: Boolean get() = promotions is PromotionsState.Loading
    val promotionsByBrand: Map<String, List<PromotionEntity>> get() = (promotions as? PromotionsState.Loaded)?.byBrand ?: emptyMap()
    val marcasConPromo: Int get() = (promotions as? PromotionsState.Loaded)?.marcasConPromo ?: 0
    val totalPromosActivas: Int get() = (promotions as? PromotionsState.Loaded)?.totalPromosActivas ?: 0
    val promosExpiringToday: Int get() = (promotions as? PromotionsState.Loaded)?.promosExpiringToday ?: 0
    val promosExpiringTomorrow: Int get() = (promotions as? PromotionsState.Loaded)?.promosExpiringTomorrow ?: 0
    val promosExpiringSoon: List<PromotionEntity> get() = (promotions as? PromotionsState.Loaded)?.promosExpiringSoon ?: emptyList()
    val promotionErrorMessage: String? get() = (promotions as? PromotionsState.Loaded)?.error
}

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ruteroManager: RuteroManager,
    private val recentRoutesStore: RecentRoutesStore,
    private val routeExporter: com.rutamercaderistas.data.export.RouteExporter,
    private val repository: RuteroRepository,
    private val promotionRepository: PromotionRepository,
    private val groupPromotions: GroupPromotionsUseCase,
    private val computeChainToLocales: ComputeChainToLocalesUseCase,
    private val computeRouteBrands: ComputeRouteBrandsUseCase,
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
                _uiState.update { it.copy(routes = routes) }
            }
        }
    }

    private fun observeRecentRoutes() {
        viewModelScope.launch {
            recentRoutesStore.recentRoutesFlow.collect { recent ->
                _uiState.update { it.copy(recentRoutes = recent) }
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
                val selectedRoute = repository.getActiveRuteroName()
                val routeBrands = computeRouteBrands(allLocales)
                val chainToLocales = computeChainToLocales(allLocales)

                _uiState.update { state ->
                    val previousCurrentDayLocales = (state.route as? RouteDataState.Loaded)?.currentDayLocales ?: emptyList()
                    state.copy(
                        route = RouteDataState.Loaded(
                            selectedRoute = selectedRoute,
                            entries = entries,
                            activeDays = activeDays,
                            currentDayLocales = previousCurrentDayLocales,
                            allLocales = allLocales,
                            stats = stats,
                        ),
                        chainToLocales = chainToLocales,
                        routeBrands = routeBrands,
                        needsInitialLoad = false,
                    )
                }

                loadCachedPromotions()
            }
        }
    }

    private fun loadCachedPromotions() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = promotionRepository.getAllPromotions()
            if (cached.isEmpty()) return@launch
            val result = groupPromotions(cached)
            _uiState.update { it.copy(
                promotions = PromotionsState.Loaded(
                    byBrand = result.promotionsByBrand,
                    marcasConPromo = result.marcasConPromo,
                    totalPromosActivas = result.totalPromosActivas,
                    promosExpiringToday = result.promosExpiringToday,
                    promosExpiringTomorrow = result.promosExpiringTomorrow,
                    promosExpiringSoon = result.promosExpiringSoon,
                ),
            )}
        }
    }

    fun loadInitialData() {
        viewModelScope.launch {
            val index = withContext(Dispatchers.IO) { ruteroManager.loadIndex() }
            if (index.isNotEmpty()) {
                val lastRoute = preferencesRepository.getSelectedRoute()
                val route = if (lastRoute != null && index.contains(lastRoute)) lastRoute
                else index.first()
                selectRoute(route)
            } else {
                if (!fileRepository.excelExists()) {
                    _uiState.update { it.copy(needsInitialLoad = true) }
                }
            }
            updateSyncLabel()
            loadPromotions()
        }
    }

    private fun updatePromotionState(grouped: GroupedPromotions) {
        _uiState.update { it.copy(
            promotions = PromotionsState.Loaded(
                byBrand = grouped.promotionsByBrand,
                marcasConPromo = grouped.marcasConPromo,
                totalPromosActivas = grouped.totalPromosActivas,
                promosExpiringToday = grouped.promosExpiringToday,
                promosExpiringTomorrow = grouped.promosExpiringTomorrow,
                promosExpiringSoon = grouped.promosExpiringSoon,
            ),
        )}
    }

    private fun loadPromotions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = promotionRepository.getAllPromotions()
                if (cached.isNotEmpty()) {
                    updatePromotionState(groupPromotions(cached))
                } else {
                    _uiState.update { it.copy(promotions = PromotionsState.Loading) }
                }
                val ok = promotionRepository.refresh()
                val all = promotionRepository.getAllPromotions()
                updatePromotionState(groupPromotions(all))
                _uiState.update { it.copy(
                    promotions = when (val p = _uiState.value.promotions) {
                        is PromotionsState.Loaded -> p.copy(error = if (ok) null else "No se pudieron descargar las promociones")
                        else -> PromotionsState.Loaded(error = if (ok) null else "No se pudieron descargar las promociones")
                    },
                )}
            } catch (e: Exception) {
                Timber.w(e, "Error cargando promociones")
                _uiState.update { state ->
                    state.copy(
                        promotions = when (state.promotions) {
                            is PromotionsState.Loading -> PromotionsState.Idle
                            else -> state.promotions
                        },
                    )
                }
            }
        }
    }

    fun selectRoute(rutero: String) {
        viewModelScope.launch {
            try {
                val entries = ruteroManager.loadRoute(rutero)
                if (entries.isNotEmpty()) {
                    preferencesRepository.setSelectedRoute(rutero)
                    repository.setEntries(entries, rutero)
                    recentRoutesStore.addRoute(rutero)
                    val activeDays = DiaSemana.todos().filter { repository.hasAnyVisitOnDay(it) }
                    val stats = repository.getStats()
                    val allLocales = repository.getAllLocales()
                    val currentDay = activeDays.firstOrNull()
                    val currentDayLocales = if (currentDay != null)
                        repository.getLocalesForDay(currentDay) else emptyList()
                    _uiState.update { it.copy(
                        route = RouteDataState.Loaded(
                            selectedRoute = rutero,
                            entries = entries,
                            activeDays = activeDays,
                            currentDayLocales = currentDayLocales,
                            allLocales = allLocales,
                            stats = stats,
                        ),
                    )}
                }
            } catch (e: Exception) {
                Timber.w(e, "Error selecting route")
            }
        }
    }

    fun setCurrentDay(dia: DiaSemana?) {
        if (dia == null) return
        val locales = repository.getLocalesForDay(dia)
        _uiState.update { state ->
            val currentRoute = state.route
            if (currentRoute is RouteDataState.Loaded) {
                state.copy(route = currentRoute.copy(currentDayLocales = locales))
            } else state
        }
    }

    fun updateSyncLabel() {
        val label = if (!fileRepository.excelExists()) "hoy"
        else {
            val modified = Instant.ofEpochMilli(fileRepository.excelLastModified())
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
        _uiState.update { it.copy(lastSyncRelative = label) }
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
            _uiState.update { it.copy(snackbarMessage = "Selecciona una ruta primero") }
            return
        }
        viewModelScope.launch {
            try {
                val file = routeExporter.exportAsImage(name, s.entries, s.stats)
                try {
                    routeExporter.shareImage(file, name)
                } catch (e: Exception) {
                    _uiState.update { it.copy(snackbarMessage = "Error al compartir: ${e.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Error al exportar: ${e.message}") }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
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

            val chainCatalog = allPromos
                .groupBy { normalizeChain(it.chain) }
                .map { (chain, promos) ->
                    val byBrand = promos.groupBy { it.brand.cleanBrand() }
                    buildString {
                        appendLine("═══ $chain (${promos.size} promo${if (promos.size != 1) "nes" else ""}) ═══")
                        byBrand.forEach { (brand, bp) ->
                            appendLine("    $brand → ${bp.size} promo${if (bp.size != 1) "nes" else ""}:")
                            bp.forEach { p -> appendLine("      - ${p.productName} (${p.price})") }
                        }
                    }
                }.joinToString("\n")

            val s = _uiState.value
            val currentLocaleResult = s.currentDayLocales.joinToString("\n\n") { local ->
                val norm = normalizeChain(local.cadena)
                val lines = mutableListOf("• ${local.local} (cadena: \"${local.cadena}\" → \"$norm\")")
                local.clientes.forEach { cliente ->
                    val clean = normalizeBrand(cliente.nombre).cleanBrand()
                    val promos = s.promotionsByBrand[clean].orEmpty()
                        .filter { local.cadena.isBlank() || normalizeChain(it.chain) == norm }
                    lines.add("    ${cliente.nombre} → ${promos.size} promo${if (promos.size != 1) "nes" else ""}")
                    promos.forEach { p ->
                        lines.add("      - ${p.productName} (${p.price})")
                    }
                }
                lines.joinToString("\n")
            }

            val lastUpdatedStr = if (lastUpdated > 0) {
                val modified = Instant.ofEpochMilli(lastUpdated)
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
            } else "Nunca"

            _uiState.update {
                it.copy(promotionDiagnostic = PromotionDiagnostic(
                    lastUpdated = lastUpdatedStr,
                    totalPromos = allPromos.size,
                    distinctBrands = distinctBrands,
                    first20 = first20,
                    chainCatalog = chainCatalog,
                    currentLocaleResult = currentLocaleResult,
                ))
            }
        }
    }

    fun refreshPromotions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = promotionRepository.getAllPromotions()
                if (cached.isNotEmpty()) {
                    updatePromotionState(groupPromotions(cached))
                } else {
                    _uiState.update { it.copy(promotions = PromotionsState.Loading) }
                }
                val ok = promotionRepository.refresh()
                val all = promotionRepository.getAllPromotions()
                val grouped = groupPromotions(all)
                val chainToLocales = computeChainToLocales(_uiState.value.allLocales)
                updatePromotionState(grouped)
                _uiState.update { it.copy(
                    chainToLocales = chainToLocales,
                    promotions = when (val p = _uiState.value.promotions) {
                        is PromotionsState.Loaded -> p.copy(error = if (ok) null else "No se pudieron descargar las promociones")
                        else -> PromotionsState.Loaded(error = if (ok) null else "No se pudieron descargar las promociones")
                    },
                )}
            } catch (e: Exception) {
                Timber.w(e, "Error refrescando promociones")
                _uiState.update { state ->
                    state.copy(
                        promotions = when (state.promotions) {
                            is PromotionsState.Loading -> PromotionsState.Idle
                            else -> state.promotions
                        },
                    )
                }
            }
        }
    }

    fun clearDiagnostic() {
        _uiState.update { it.copy(promotionDiagnostic = null) }
    }

    fun clearPromotionError() {
        _uiState.update { it.copy(
            promotions = when (val p = _uiState.value.promotions) {
                is PromotionsState.Loaded -> p.copy(error = null)
                else -> p
            },
        )}
    }
}

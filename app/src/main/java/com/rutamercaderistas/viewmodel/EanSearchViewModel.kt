package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.data.local.buildRankedSearch
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EAN_DATA_VERSION
import com.rutamercaderistas.services.EanExcelParser
import com.rutamercaderistas.services.compactNorm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EanSearchUiState {
    data class Loading(val progress: String = "Cargando base de datos...") : EanSearchUiState
    data class Ready(
        val query: String = "",
        val pagingFlow: Flow<PagingData<EanProductEntity>> = emptyFlow(),
        val isSearching: Boolean = false,
        val isScanning: Boolean = false,
    ) : EanSearchUiState
    data class Error(val message: String) : EanSearchUiState
    data class BarcodeResult(val barcode: String) : EanSearchUiState
}

@HiltViewModel
class EanSearchViewModel @Inject constructor(
    private val eanProductDao: EanProductDao,
    private val eanExcelParser: EanExcelParser,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EanSearchUiState>(EanSearchUiState.Loading())
    val uiState: StateFlow<EanSearchUiState> = _uiState

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory

    private var observeJob: Job? = null
    private var debounceJob: Job? = null
    private val debounceMs = 250L

    // Metadatos del catálogo para mostrar en la interfaz (versión y nº de productos).
    private val _catalogMeta = MutableStateFlow<Pair<Int, Int>?>(null)
    val catalogMeta: StateFlow<Pair<Int, Int>?> = _catalogMeta

    init {
        viewModelScope.launch {
            preferencesRepository.getSearchHistoryFlow().collect { _searchHistory.value = it }
        }
        loadDatabase()
    }

    private val _brandCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val brandCounts: StateFlow<Map<String, Int>> = _brandCounts

    private val _searchBrandCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val searchBrandCounts: StateFlow<Map<String, Int>> = _searchBrandCounts

    private val _searchCajas = MutableStateFlow<List<String>>(emptyList())
    val searchCajas: StateFlow<List<String>> = _searchCajas

    private fun loadDatabase() {
        debounceJob?.cancel()
        viewModelScope.launch {
            _uiState.value = EanSearchUiState.Loading("Cargando base de datos EAN...")
            val hasData = eanProductDao.count() > 0
            if (hasData) {
                // Caché primero: la pantalla se muestra al instante y la
                // importación (lenta en catálogos grandes) corre en fondo.
                _catalogMeta.value = EAN_DATA_VERSION to eanProductDao.count()
                refreshBrandCounts()
                observeSearch("")
            }
            val currentHash = eanExcelParser.computeAssetsHash()
            val storedHash = eanExcelParser.getEanAssetsHash()
            // La versión fuerza reimportación en instalados: sin este chequeo,
            // cambios de alias/marca (p. ej. B.TAN → BWILD) nunca llegaban a
            // equipos que ya habían importado.
            val storedVersion = try { eanExcelParser.getEanDataVersion() } catch (_: Exception) { 0 }
            val needsImport = !hasData ||
                storedVersion < EAN_DATA_VERSION ||
                (currentHash.isNotBlank() && storedHash != currentHash) ||
                eanProductDao.hasUnnormalized() > 0

            if (needsImport) {
                if (!hasData) {
                    _uiState.value = EanSearchUiState.Loading("Importando base de datos EAN...")
                }
                val result = eanExcelParser.loadFromAssets()
                result.onSuccess { count ->
                    eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
                    if (currentHash.isNotBlank()) eanExcelParser.setEanAssetsHash(currentHash)
                    _catalogMeta.value = EAN_DATA_VERSION to count
                    refreshBrandCounts()
                    if (!hasData) {
                        _uiState.value = EanSearchUiState.Loading("Base de datos lista ($count productos)")
                    }
                }.onFailure { e ->
                    // Con caché visible no se pisa con Error; sin datos no hay
                    // nada que mostrar.
                    if (!hasData) {
                        _uiState.value = EanSearchUiState.Error("Error cargando base de datos: ${e.message}")
                        return@launch
                    }
                }
            } else if (!hasData) {
                _catalogMeta.value = EAN_DATA_VERSION to eanProductDao.count()
                refreshBrandCounts()
            }
            // Re-observar con el query actual (el usuario pudo escribir
            // mientras importaba en fondo): refresca la lista con lo nuevo.
            val currentQuery = (_uiState.value as? EanSearchUiState.Ready)?.query.orEmpty()
            observeSearch(currentQuery)
        }
    }

    private suspend fun refreshBrandCounts() {
        try {
            val all = eanProductDao.getAll().first()
            _brandCounts.value = all.groupBy { it.marca.ifBlank { "Sin marca" } }
                .mapValues { it.value.size }
        } catch (_: Exception) {}
    }

    private fun observeSearch(query: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val tokens = query.split(Regex("\\s+"))
                .map { compactNorm(it) }
                .filter { it.isNotBlank() }

            // Pager debe crear un PagingSource NUEVO en cada factory call (invalidación
            // y refresh de Paging 3 lo exigen); no reutilizar una instancia.
            // Con query: ranking en SQL (buildRankedSearch) con paginación real
            // de Room — sin tope LIMIT ni re-ordenar en memoria por página.
            // Nulo (query vacía) → catálogo completo.
            val pagerFlow = Pager(
                config = PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = false,
                    maxSize = 200
                ),
                pagingSourceFactory = {
                    buildRankedSearch(query, tokens)?.toQuery()?.let { eanProductDao.rankedSearch(it) }
                        ?: eanProductDao.pagingSourceAll()
                }
            ).flow.cachedIn(viewModelScope)

            // Obtener marcas y cajas para filtros (en paralelo, no bloquea UI)
            viewModelScope.launch {
                if (query.isBlank() || tokens.isEmpty()) {
                    eanProductDao.getAllBrandCounts()
                        .collect { list ->
                            _searchBrandCounts.value = list.associate { it.marca to it.count }.toMutableMap()
                        }
                    _searchCajas.value = eanProductDao.getAllCajas().first()
                } else {
                    // Usar el primer token como aproximación; la UI filtra further
                    val firstToken = tokens.first()
                    eanProductDao.getBrandCountsForToken(firstToken)
                        .collect { list ->
                            _searchBrandCounts.value = list.associate { it.marca to it.count }.toMutableMap()
                        }
                    _searchCajas.value = eanProductDao.getCajasForToken(firstToken).first()
                }
            }

            if (query.isNotBlank()) {
                preferencesRepository.addSearchQuery(query.trim())
            }
            _uiState.value = EanSearchUiState.Ready(
                query = query,
                pagingFlow = pagerFlow,
                isSearching = false,
                isScanning = false,
            )
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = (_uiState.value as? EanSearchUiState.Ready)?.copy(
            query = query,
            isSearching = true,
            isScanning = false,
        ) ?: EanSearchUiState.Ready(query = query, isSearching = true)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(debounceMs)
            observeSearch(query)
        }
    }

    fun onHistoryClick(query: String) {
        onQueryChange(query)
    }

    fun onBarcodeScanned(barcode: String) {
        val digits = barcode.filter { it.isDigit() }
        val normalized = when {
            digits.length == 12 && digits.all { it.isDigit() } -> "0$digits"
            else -> barcode
        }
        _uiState.value = EanSearchUiState.BarcodeResult(normalized)
        onQueryChange(normalized)
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun clearSearchHistory() {
        viewModelScope.launch { preferencesRepository.clearSearchHistory() }
    }

    fun forceReload() {
        observeJob?.cancel()
        _uiState.value = EanSearchUiState.Loading("Actualizando catálogo EAN...")
        viewModelScope.launch {
            val result = eanExcelParser.loadFromAssets()
            result.onSuccess { count ->
                eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
                eanExcelParser.setEanAssetsHash(eanExcelParser.computeAssetsHash())
                _catalogMeta.value = EAN_DATA_VERSION to count
                refreshBrandCounts()
                _uiState.value = EanSearchUiState.Loading("Catálogo actualizado ($count productos)")
                observeSearch("")
            }.onFailure { e ->
                _uiState.value = EanSearchUiState.Error("Error actualizando catálogo: ${e.message}")
            }
        }
    }

    fun retryLoad() {
        observeJob?.cancel()
        loadDatabase()
    }
}

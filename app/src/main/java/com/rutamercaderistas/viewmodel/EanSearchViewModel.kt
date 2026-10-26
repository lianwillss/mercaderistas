package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rutamercaderistas.data.local.BrandCountResult
import com.rutamercaderistas.data.local.CajaCountResult
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.data.local.buildBrandFacets
import com.rutamercaderistas.data.local.buildBrowseSearch
import com.rutamercaderistas.data.local.buildCajaFacets
import com.rutamercaderistas.data.local.buildCountSearch
import com.rutamercaderistas.data.local.buildRankedSearch
import com.rutamercaderistas.data.local.eanTokens
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EAN_DATA_VERSION
import com.rutamercaderistas.services.EanExcelParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EanSearchUiState {
    data class Loading(val progress: String = "Cargando base de datos...") : EanSearchUiState
    data class Ready(
        val query: String = "",
        val pagingFlow: Flow<PagingData<EanProductEntity>> = emptyFlow(),
        /** Filtro de marca exacto; "" = "Sin marca", null = todas. */
        val brandFilter: String? = null,
        /** Filtro de caja (conversión) exacto; null = todas. */
        val cajaFilter: String? = null,
        val brandFacets: List<BrandCountResult> = emptyList(),
        val cajaFacets: List<CajaCountResult> = emptyList(),
        /** Total exacto de resultados; null mientras se calcula. */
        val totalCount: Int? = null,
    ) : EanSearchUiState
    data class Error(val message: String) : EanSearchUiState
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

    // Metadatos del catálogo para mostrar en la interfaz (versión y nº de productos).
    private val _catalogMeta = MutableStateFlow<Pair<Int, Int>?>(null)
    val catalogMeta: StateFlow<Pair<Int, Int>?> = _catalogMeta

    // Marcas del catálogo completo para los atajos inferiores.
    private val _catalogBrands = MutableStateFlow<List<BrandCountResult>>(emptyList())
    val catalogBrands: StateFlow<List<BrandCountResult>> = _catalogBrands

    private var resultsJob: Job? = null
    private var debounceJob: Job? = null
    private val debounceMs = 250L

    init {
        viewModelScope.launch {
            preferencesRepository.getSearchHistoryFlow().collect { _searchHistory.value = it }
        }
        loadDatabase()
    }

    private fun loadDatabase() {
        debounceJob?.cancel()
        resultsJob?.cancel()
        viewModelScope.launch {
            _uiState.value = EanSearchUiState.Loading("Cargando base de datos EAN...")
            val hasData = eanProductDao.count() > 0
            if (hasData) {
                // Caché primero: la pantalla se muestra al instante y la
                // importación (lenta en catálogos grandes) corre en fondo.
                _catalogMeta.value = EAN_DATA_VERSION to eanProductDao.count()
                refreshCatalogBrands()
                refreshResults("", brand = null, caja = null, notifyHistory = false)
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
                    refreshCatalogBrands()
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
                refreshCatalogBrands()
            }
            // Re-consultar con el texto y filtros vigentes (el usuario pudo
            // tocar algo mientras importaba en fondo).
            val ready = _uiState.value as? EanSearchUiState.Ready
            refreshResults(
                query = ready?.query.orEmpty(),
                brand = ready?.brandFilter,
                caja = ready?.cajaFilter,
                notifyHistory = false,
            )
        }
    }

    private suspend fun refreshCatalogBrands() {
        try {
            _catalogBrands.value = eanProductDao.brandFacets(buildBrandFacets(emptyList()).toQuery())
        } catch (_: Exception) {
            // Sin facetas los atajos del pie simplemente no se muestran.
        }
    }

    /**
     * Reconstruye la lista paginada, las facetas de filtros y el total con el
     * mismo WHERE: todo sale de SQL, así que el orden (ranking) no se rearma
     * en la UI y los conteos de los chips corresponden al resultado real.
     */
    private fun refreshResults(
        query: String,
        brand: String?,
        caja: String?,
        notifyHistory: Boolean = true,
    ) {
        resultsJob?.cancel()
        resultsJob = viewModelScope.launch {
            val tokens = eanTokens(query)
            // Pager debe crear un PagingSource NUEVO en cada factory call
            // (invalidación y refresh de Paging 3 lo exigen).
            val pagerFlow = Pager(
                config = PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = false,
                    maxSize = 200,
                ),
                pagingSourceFactory = {
                    buildRankedSearch(query, tokens, brand, caja)
                        ?.toQuery()
                        ?.let { eanProductDao.rankedSearch(it) }
                        ?: eanProductDao.browseSearch(buildBrowseSearch(brand, caja).toQuery())
                },
            ).flow.cachedIn(viewModelScope)

            val total = runCatching {
                eanProductDao.countForQuery(buildCountSearch(tokens, brand, caja).toQuery())
            }.getOrNull()
            val brands = runCatching {
                eanProductDao.brandFacets(buildBrandFacets(tokens, caja).toQuery())
            }.getOrDefault(emptyList())
            val cajas = runCatching {
                eanProductDao.cajaFacets(buildCajaFacets(tokens, brand).toQuery())
            }.getOrDefault(emptyList())

            _uiState.value = EanSearchUiState.Ready(
                query = query,
                pagingFlow = pagerFlow,
                brandFilter = brand,
                cajaFilter = caja,
                brandFacets = brands,
                cajaFacets = cajas,
                totalCount = total,
            )
            if (notifyHistory && query.isNotBlank()) {
                preferencesRepository.addSearchQuery(query.trim())
            }
        }
    }

    fun onQueryChange(query: String) {
        val ready = _uiState.value as? EanSearchUiState.Ready
        // totalCount = null: mientras corre el debounce no se muestra "0 resultados"
        // con el total de la consulta anterior.
        _uiState.value = ready?.copy(query = query, totalCount = null)
            ?: EanSearchUiState.Ready(query = query)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(debounceMs)
            val current = _uiState.value as? EanSearchUiState.Ready ?: return@launch
            refreshResults(current.query, current.brandFilter, current.cajaFilter)
        }
    }

    /** Toca un chip de marca: el mismo chip lo quita y vuelve a "Todas". */
    fun onBrandFilter(brand: String?) {
        val ready = _uiState.value as? EanSearchUiState.Ready ?: return
        val next = if (ready.brandFilter == brand) null else brand
        _uiState.value = ready.copy(brandFilter = next)
        debounceJob?.cancel()
        refreshResults(ready.query, next, ready.cajaFilter, notifyHistory = false)
    }

    /** Toca un chip de caja: el mismo chip lo quita y vuelve a "Todas". */
    fun onCajaFilter(caja: String?) {
        val ready = _uiState.value as? EanSearchUiState.Ready ?: return
        val next = if (ready.cajaFilter == caja) null else caja
        _uiState.value = ready.copy(cajaFilter = next)
        debounceJob?.cancel()
        refreshResults(ready.query, ready.brandFilter, next, notifyHistory = false)
    }

    /** Limpia ambos filtros en una sola recarga (chip de marca y de caja). */
    fun clearFilters() {
        val ready = _uiState.value as? EanSearchUiState.Ready ?: return
        if (ready.brandFilter == null && ready.cajaFilter == null) return
        _uiState.value = ready.copy(brandFilter = null, cajaFilter = null)
        debounceJob?.cancel()
        refreshResults(ready.query, brand = null, caja = null, notifyHistory = false)
    }

    fun onHistoryClick(query: String) {
        onQueryChange(query)
    }

    /**
     * Escaneo: se limpian los filtros para no ocultar el producto escaneado por
     * un chip de marca/caja que quedó activo. El UPC-A de 12 dígitos se
     * normaliza a EAN-13 con cero inicial.
     */
    fun onBarcodeScanned(barcode: String) {
        val digits = barcode.filter { it.isDigit() }
        val normalized = when {
            digits.length == 12 -> "0$digits"
            digits.isNotEmpty() -> digits
            else -> barcode.trim()
        }
        val ready = _uiState.value as? EanSearchUiState.Ready
        _uiState.value = ready?.copy(query = normalized, brandFilter = null, cajaFilter = null)
            ?: EanSearchUiState.Ready(query = normalized)
        debounceJob?.cancel()
        refreshResults(normalized, brand = null, caja = null)
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun clearSearchHistory() {
        viewModelScope.launch { preferencesRepository.clearSearchHistory() }
    }

    fun forceReload() {
        resultsJob?.cancel()
        debounceJob?.cancel()
        _uiState.value = EanSearchUiState.Loading("Actualizando catálogo EAN...")
        viewModelScope.launch {
            val result = eanExcelParser.loadFromAssets()
            result.onSuccess { count ->
                eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
                eanExcelParser.setEanAssetsHash(eanExcelParser.computeAssetsHash())
                _catalogMeta.value = EAN_DATA_VERSION to count
                refreshCatalogBrands()
                refreshResults("", brand = null, caja = null, notifyHistory = false)
            }.onFailure { e ->
                _uiState.value = EanSearchUiState.Error("Error actualizando catálogo: ${e.message}")
            }
        }
    }

    fun retryLoad() {
        resultsJob?.cancel()
        loadDatabase()
    }
}

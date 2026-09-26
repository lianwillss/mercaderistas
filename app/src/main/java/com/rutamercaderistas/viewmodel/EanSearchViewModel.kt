package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.sqlite.db.SimpleSQLiteQuery
import com.rutamercaderistas.data.local.EAN_FTS_MATCH_QUERY
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.data.local.buildFtsMatch
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
import kotlinx.coroutines.launch
import kotlin.math.min
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

    private fun loadDatabase() {
        debounceJob?.cancel()
        viewModelScope.launch {
            _uiState.value = EanSearchUiState.Loading("Cargando base de datos EAN...")
            val currentHash = eanExcelParser.computeAssetsHash()
            val storedHash = eanExcelParser.getEanAssetsHash()
            // La versión fuerza reimportación en instalados: sin este chequeo,
            // cambios de alias/marca (p. ej. B.TAN → BWILD) nunca llegaban a
            // equipos que ya habían importado.
            val storedVersion = try { eanExcelParser.getEanDataVersion() } catch (_: Exception) { 0 }
            val needsImport = eanProductDao.count() == 0 ||
                storedVersion < EAN_DATA_VERSION ||
                (currentHash.isNotBlank() && storedHash != currentHash) ||
                eanProductDao.hasUnnormalized() > 0

            if (needsImport) {
                _uiState.value = EanSearchUiState.Loading("Importando base de datos EAN...")
                val result = eanExcelParser.loadFromAssets()
                result.onSuccess { count ->
                    eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
                    if (currentHash.isNotBlank()) eanExcelParser.setEanAssetsHash(currentHash)
                    _catalogMeta.value = EAN_DATA_VERSION to count
                    refreshBrandCounts()
                    _uiState.value = EanSearchUiState.Loading("Base de datos lista ($count productos)")
                }.onFailure { e ->
                    _uiState.value = EanSearchUiState.Error("Error cargando base de datos: ${e.message}")
                    return@launch
                }
            } else {
                _catalogMeta.value = EAN_DATA_VERSION to eanProductDao.count()
                refreshBrandCounts()
            }
            observeSearch("")
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
            val pagerFlow = Pager(
                config = PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = false,
                    maxSize = 200
                ),
                pagingSourceFactory = {
                    if (query.isBlank() || tokens.isEmpty()) {
                        eanProductDao.pagingSourceAll()
                    } else {
                        MultiTokenPagingSource(eanProductDao, tokens, query)
                    }
                }
            ).flow.cachedIn(viewModelScope)

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

// Coincidencia de un token (ya compacto) contra cualquier campo del producto.
// Para los códigos se ignora el relleno de ceros a la izquierda, de modo que
// "12" encuentre "000012" y viceversa. Incluye fallback Levenshtein <=2 para
// EANs con 1-2 dígitos errados por lectura (caso PEPILU 06110112277 vs 0606110112277).
private fun EanProductEntity.containsToken(t: String): Boolean {
    val tt = t.trimStart('0')
    fun String.codeHit(): Boolean {
        val f = this.trimStart('0')
        if (f.contains(tt) || this.contains(t, ignoreCase = true)) return true
        // Fallback edit distance para códigos numéricos largos (EAN/SKU)
        if (tt.length >= 8 && f.length >= 8 && levenshtein(f, tt) <= 2) return true
        return false
    }
    return eanPrincipal.codeHit()
        || codCencosud.codeHit()
        || codProveedor.codeHit()
        || codigoBarra.codeHit()
        || descripcionProducto.contains(t, ignoreCase = true)
        || marca.contains(t, ignoreCase = true)
        || descripcionNorm.contains(t, ignoreCase = true)
        || marcaNorm.contains(t, ignoreCase = true)
        || descripcionNormNospace.contains(t, ignoreCase = true)
        || marcaNormNospace.contains(t, ignoreCase = true)
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val cur = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
            prev = cur
        }
    }
    return dp[b.length]
}

// PagingSource personalizado que combina múltiples tokens con lógica AND
// y aplica ranking en memoria (ya que SQL no soporta fácilmente este ranking complejo).
private class MultiTokenPagingSource(
    private val dao: EanProductDao,
    private val tokens: List<String>,
    private val originalQuery: String,
) : PagingSource<Int, EanProductEntity>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EanProductEntity> {
        return try {
            val page = params.key ?: 1
            val firstToken = tokens.first()
            // Candidatos LIKE (cubre códigos con ceros y Levenshtein) + FTS5
            // (prefijos y tokenización en descripciones/marcas). El ranking
            // final sigue siendo el mismo in-memory de abajo.
            val like = dao.searchCandidates(firstToken).first()
            val fts = ftsCandidates()
            val candidates = if (fts.isEmpty()) like else (like + fts).distinctBy { it.id }

            // Filtrar por AND de todos los tokens
            val filtered = candidates
                .filter { e -> tokens.all { t -> e.containsToken(t) } }
                .sortedWith(
                    compareByDescending<EanProductEntity> { e ->
                        val queryTrim = originalQuery.trim()
                        val queryTrimZero = queryTrim.trimStart('0')
                        val queryCompact = compactNorm(originalQuery)
                        // 1) EAN exacto (ignorando ceros a la izquierda)
                        val eanZero = e.eanPrincipal.trimStart('0')
                        if (e.eanPrincipal == queryTrim || (queryTrimZero.isNotEmpty() && eanZero == queryTrimZero) || e.codigoBarra.trimStart('0') == queryTrimZero) 3 else 0
                    }.thenByDescending { e ->
                        // 1b) EAN prefijo (escaner parcial)
                        val queryTrimZero = originalQuery.trim().trimStart('0')
                        val eanZero = e.eanPrincipal.trimStart('0')
                        val barraZero = e.codigoBarra.trimStart('0')
                        if (eanZero.startsWith(queryTrimZero) || barraZero.startsWith(queryTrimZero)) 2 else 0
                    }.thenByDescending { e ->
                        // 1c) EAN contiene query (o viceversa) - caso PEPILU
                        val queryTrimZero = originalQuery.trim().trimStart('0')
                        val eanZero = e.eanPrincipal.trimStart('0')
                        val barraZero = e.codigoBarra.trimStart('0')
                        if (eanZero.contains(queryTrimZero) || queryTrimZero.contains(eanZero) || barraZero.contains(queryTrimZero) || (eanZero.isNotEmpty() && queryTrimZero.isNotEmpty() && levenshtein(eanZero, queryTrimZero) <= 2)) 1 else 0
                    }.thenByDescending { e ->
                        // 2) SKU exacto
                        val queryTrim = originalQuery.trim()
                        if (e.codCencosud == queryTrim || e.codProveedor == queryTrim) 2 else 0
                    }.thenByDescending { e ->
                        // 3) Marca exacta
                        val queryCompact = compactNorm(originalQuery)
                        if (e.marcaNorm == queryCompact || e.marcaNormNospace == queryCompact) 2 else 0
                    }.thenByDescending { e ->
                        // 4) Todos los tokens golpean la marca
                        val brandHits = tokens.count { t -> e.marcaNorm.contains(t) || e.marcaNormNospace.contains(t) }
                        when {
                            brandHits == tokens.size && tokens.isNotEmpty() -> 1
                            brandHits > 0 -> 0
                            else -> -1
                        }
                    }.thenBy { e -> e.marca }
                     .thenBy { e -> e.descripcionProducto }
                )

            // Paginación simple: cada página = 50 items
            val pageSize = 50
            val start = (page - 1) * pageSize
            val end = min(start + pageSize, filtered.size)
            val pageItems = if (start < filtered.size) filtered.subList(start, end) else emptyList()

            val nextKey = if (end < filtered.size) page + 1 else null
            val prevKey = if (page > 1) page - 1 else null

            LoadResult.Page(
                data = pageItems,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, EanProductEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            (anchorPosition / 50) + 1
        } ?: 1
    }

    /** Candidatos FTS5; vacío si la tabla sidecar aún no existe. Nunca lanza. */
    private suspend fun ftsCandidates(): List<EanProductEntity> {
        val match = buildFtsMatch(tokens) ?: return emptyList()
        return try {
            dao.ftsSearch(SimpleSQLiteQuery(EAN_FTS_MATCH_QUERY, arrayOf(match)))
        } catch (_: Exception) {
            emptyList()
        }
    }
}

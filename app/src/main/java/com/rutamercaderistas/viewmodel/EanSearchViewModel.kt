package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.EAN_DATA_VERSION
import com.rutamercaderistas.services.EanExcelParser
import com.rutamercaderistas.services.compactNorm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EanSearchUiState {
    data class Loading(val progress: String = "Cargando base de datos...") : EanSearchUiState
    data class Ready(
        val query: String = "",
        val results: List<EanProductEntity> = emptyList(),
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

    private fun loadDatabase() {
        debounceJob?.cancel()
        viewModelScope.launch {
            _uiState.value = EanSearchUiState.Loading("Cargando base de datos EAN...")
            val needsImport = eanProductDao.count() == 0 ||
                eanExcelParser.getEanDataVersion() < EAN_DATA_VERSION ||
                eanProductDao.hasUnnormalized() > 0

            if (needsImport) {
                _uiState.value = EanSearchUiState.Loading("Importando base de datos EAN...")
                val result = eanExcelParser.loadFromAssets()
                result.onSuccess { count ->
                    eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
                    _catalogMeta.value = EAN_DATA_VERSION to count
                    _uiState.value = EanSearchUiState.Loading("Base de datos lista ($count productos)")
                }.onFailure { e ->
                    _uiState.value = EanSearchUiState.Error("Error cargando base de datos: ${e.message}")
                    return@launch
                }
            } else {
                _catalogMeta.value = EAN_DATA_VERSION to eanProductDao.count()
            }
            observeSearch("")
        }
    }

    private fun observeSearch(query: String) {
        observeJob?.cancel()
        // Tokenizar por espacios PRIMERO y luego compactar cada token, para que
        // "pistacho nat" sea AND de ["pistacho","nat"] (orden-independiente) y
        // "bymaria" (sin espacios) siga siendo un solo token que matchea la
        // columna *_nospace ("by maria" -> "bymaria").
        val tokens = query.split(Regex("\\s+"))
            .map { compactNorm(it) }
            .filter { it.isNotBlank() }
        val searchFlow = if (query.isBlank() || tokens.isEmpty()) {
            eanProductDao.getAll()
        } else {
            flow {
                val candidates = tokens.flatMap { tok -> eanProductDao.searchCandidates(tok).first() }
                    .distinctBy { it.id }
                val result = candidates
                    .filter { e -> tokens.all { t -> e.containsToken(t) } }
                    .take(50)
                emit(result)
            }
        }

        observeJob = viewModelScope.launch {
            searchFlow.collect { results ->
                if (query.isNotBlank() && results.isNotEmpty()) {
                    preferencesRepository.addSearchQuery(query.trim())
                }
                _uiState.value = EanSearchUiState.Ready(
                    query = query,
                    results = results,
                    isSearching = false,
                    isScanning = false,
                )
            }
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
        _uiState.value = EanSearchUiState.BarcodeResult(barcode)
        onQueryChange(barcode)
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
                _catalogMeta.value = EAN_DATA_VERSION to count
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
// "12" encuentre "000012" y viceversa.
private fun EanProductEntity.containsToken(t: String): Boolean {
    val tt = t.trimStart('0')
    fun String.codeHit(): Boolean {
        val f = this.trimStart('0')
        return f.contains(tt) || this.contains(t, ignoreCase = true)
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

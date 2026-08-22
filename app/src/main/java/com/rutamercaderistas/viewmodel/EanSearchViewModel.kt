package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.data.local.EanProductDao
import com.rutamercaderistas.data.local.EanProductEntity
import com.rutamercaderistas.services.EAN_DATA_VERSION
import com.rutamercaderistas.services.EanExcelParser
import com.rutamercaderistas.services.normalizeSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<EanSearchUiState>(EanSearchUiState.Loading())
    val uiState: StateFlow<EanSearchUiState> = _uiState

    private var observeJob: Job? = null
    private var debounceJob: Job? = null
    private val debounceMs = 250L

    init {
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
                    _uiState.value = EanSearchUiState.Loading("Base de datos lista ($count productos)")
                }.onFailure { e ->
                    _uiState.value = EanSearchUiState.Error("Error cargando base de datos: ${e.message}")
                    return@launch
                }
            }
            observeSearch("")
        }
    }

    private fun observeSearch(query: String) {
        observeJob?.cancel()
        val normalized = normalizeSearch(query)
        val searchFlow = if (query.isBlank()) {
            eanProductDao.getAll().map { it.take(100) }
        } else {
            eanProductDao.searchAll(normalized).map { it.take(50) }
        }

        observeJob = viewModelScope.launch {
            searchFlow.collect { results ->
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

    fun onBarcodeScanned(barcode: String) {
        _uiState.value = EanSearchUiState.BarcodeResult(barcode)
        onQueryChange(barcode)
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun forceReload() {
        observeJob?.cancel()
        _uiState.value = EanSearchUiState.Loading("Actualizando catálogo EAN...")
        viewModelScope.launch {
            val result = eanExcelParser.loadFromAssets()
            result.onSuccess { count ->
                eanExcelParser.setEanDataVersion(EAN_DATA_VERSION)
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
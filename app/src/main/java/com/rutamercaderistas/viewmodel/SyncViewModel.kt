package com.rutamercaderistas.viewmodel

import androidx.compose.runtime.Stable
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.services.PromotionRepository
import timber.log.Timber
import com.rutamercaderistas.data.result.SyncResult
import com.rutamercaderistas.data.result.messageOrNull
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val phase: String? = null) : SyncState
}

@Stable
data class SyncUiState(
    val isOnline: Boolean = false,
    val state: SyncState = SyncState.Idle,
    val snackbarMessage: String? = null,
) {
    val isSyncing: Boolean get() = state is SyncState.Syncing
    val syncPhase: String? get() = (state as? SyncState.Syncing)?.phase
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    private val ruteroManager: RuteroManager,
    private val repository: RuteroRepository,
    private val promotionRepository: PromotionRepository,
    private val brandReference: BrandReference,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    private val connectivityManager =
        getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        checkConnectivity()
        registerNetworkMonitor()
    }

    private fun checkConnectivity() {
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _state.value = _state.value.copy(isOnline = online)
    }

    private fun registerNetworkMonitor() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _state.value = _state.value.copy(isOnline = true)
            }
            override fun onLost(network: Network) {
                _state.value = _state.value.copy(isOnline = false)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _state.value = _state.value.copy(
                    isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                )
            }
        }
        val cb = networkCallback ?: return
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb,
            )
        } catch (_: Exception) {
            Timber.w("connectivityManager.registerNetworkCallback failed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        networkCallback?.let { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
                Timber.w("unregisterNetworkCallback failed in onCleared")
            }
        }
    }

    fun syncFromDrive() {
        syncJob?.cancel()
        _state.value = _state.value.copy(state = SyncState.Syncing())
        syncJob = viewModelScope.launch {
            val result = performDriveSync()
            _state.value = _state.value.copy(state = SyncState.Idle)
            result.messageOrNull()?.let { msg ->
                _state.value = _state.value.copy(snackbarMessage = msg)
            }
        }
    }

    private suspend fun performDriveSync(): SyncResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(state = SyncState.Syncing(phase = "Descargando Excel…"))
                val cacheBustedUrl = "${Constants.DRIVE_EXPORT_URL}&ts=${System.currentTimeMillis()}"
                val bytes = downloadWithRetries(cacheBustedUrl)
                    ?: return@withContext SyncResult.Error(
                        if (!_state.value.isOnline) "Sin conexión a Internet"
                        else "Error de descarga desde Drive. Revisa tu conexión o inténtalo más tarde."
                    ).also {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                    }

                _state.value = _state.value.copy(state = SyncState.Syncing(phase = "Procesando archivo…"))
                val changed = ruteroManager.saveMasterExcel(bytes)
                if (changed) {
                    _state.value = _state.value.copy(state = SyncState.Syncing(phase = "Indexando rutas…"))
                    val indexOk = ruteroManager.createIndex()
                    if (indexOk) {
                        repository.clear()
                        _state.value = _state.value.copy(state = SyncState.Syncing(phase = "Actualizando promociones…"))
                        promotionRepository.refresh()
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        SyncResult.Success(true)
                    } else {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        SyncResult.Error("No se pudo leer el Excel")
                    }
                } else {
                    _state.value = _state.value.copy(state = SyncState.Syncing(phase = "Actualizando promociones…"))
                    promotionRepository.refresh()
                    _state.value = _state.value.copy(state = SyncState.Idle)
                    SyncResult.NoChange
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(state = SyncState.Idle)
                SyncResult.Error(e.message ?: "Error de sincronización")
            }
        }
    }

    fun syncFromDriveWithRouteReload(currentRoute: String?) {
        syncJob?.cancel()
        _state.value = _state.value.copy(state = SyncState.Syncing())
        syncJob = viewModelScope.launch {
            val result = performDriveSync()
            when (result) {
                is SyncResult.Success -> {
                    val index = ruteroManager.loadIndex()
                    val routeToLoad = if (currentRoute != null && index.contains(currentRoute)) {
                        currentRoute
                    } else {
                        index.firstOrNull()
                    }
                    if (routeToLoad != null) {
                        val entries = ruteroManager.loadRoute(routeToLoad)
                        if (entries.isNotEmpty()) {
                            repository.setEntries(entries, routeToLoad)
                        }
                    }
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        snackbarMessage = "Datos actualizados",
                    )
                }
                is SyncResult.Error -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        snackbarMessage = result.message,
                    )
                }
                is SyncResult.NoChange -> {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                }
                is SyncResult.Offline -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        snackbarMessage = "Sin conexión",
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    private suspend fun downloadWithRetries(url: String, retries: Int = Constants.MAX_RETRIES): ByteArray? {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < retries) {
                val result = downloadBytes(
                    url = url,
                    connectTimeout = Constants.CONNECT_TIMEOUT_MS,
                    readTimeout = Constants.READ_TIMEOUT_MS,
                )
                result.onSuccess { return@withContext it }
                result.onFailure { lastError = it as? Exception ?: Exception(it) }
                attempt++
                if (attempt >= retries) {
                    Timber.w(lastError, "downloadBytes agotó %d intentos", retries)
                    return@withContext null
                }
                kotlinx.coroutines.delay(Constants.RETRY_BACKOFF_MS * (1 shl attempt))
            }
            null
        }
    }

    private fun convertDriveUrl(url: String): String {
        return when {
            url.contains("/file/d/") -> {
                val id = url.substringAfter("/file/d/").substringBefore("/")
                "https://drive.google.com/uc?export=download&id=$id"
            }
            url.contains("/spreadsheets/d/") -> {
                val id = url.substringAfter("/spreadsheets/d/").substringBefore("/")
                "https://docs.google.com/spreadsheets/d/$id/export?format=xlsx"
            }
            else -> url
        }
    }
}

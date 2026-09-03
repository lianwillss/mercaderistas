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
import com.rutamercaderistas.R
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.services.PromotionRepository
import timber.log.Timber
import com.rutamercaderistas.data.result.SyncResult
import com.rutamercaderistas.data.result.messageOrNull
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.models.EntradaRuta
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

data class PlanillaChanges(
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val moved: List<MovedLocales> = emptyList(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && moved.isEmpty()
}

data class MovedLocales(
    val local: String,
    val fromDays: String,
    val toDays: String,
)

@Stable
data class SyncUiState(
    val isOnline: Boolean = false,
    val state: SyncState = SyncState.Idle,
    val snackbarMessage: String? = null,
    val syncError: String? = null,
    val syncChanges: PlanillaChanges? = null,
    val validationErrors: List<com.rutamercaderistas.domain.validation.ValidationError> = emptyList(),
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
    private val preferencesRepository: com.rutamercaderistas.data.preferences.PreferencesRepository,
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
        _state.value = _state.value.copy(state = SyncState.Syncing(), syncError = null, syncChanges = null, validationErrors = emptyList())
        syncJob = viewModelScope.launch {
            val result = performDriveSync()
            _state.value = _state.value.copy(state = SyncState.Idle)
            result.messageOrNull()?.let { msg ->
                _state.value = _state.value.copy(syncError = msg)
            }
        }
    }

    private suspend fun performDriveSync(): SyncResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val oldEntries = ruteroManager.loadAllEntries()
                // Incremental: si el ETag no cambió, no hay nada que hacer
                val lastETag = try { preferencesRepository.getLastSyncETag() } catch (_: Exception) { null }
                if (lastETag != null) {
                    val currentETag = try { com.rutamercaderistas.data.network.headForETag(Constants.DRIVE_EXPORT_URL) } catch (_: Exception) { null }
                    if (currentETag != null && currentETag == lastETag) {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        return@withContext SyncResult.NoChange
                    }
                }
                _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_descargando)))
                val cacheBustedUrl = "${Constants.DRIVE_EXPORT_URL}&ts=${System.currentTimeMillis()}"
                val bytes = downloadWithRetries(cacheBustedUrl)
                    ?: return@withContext SyncResult.Error(
                        if (!_state.value.isOnline) getApplication<Application>().getString(R.string.sync_sin_internet)
                        else getApplication<Application>().getString(R.string.sync_error_drive)
                    ).also {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                    }

                _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_procesando)))
                // Hash incremental: si el contenido no cambió, no re-procesar
                val lastHash = try { preferencesRepository.getLastSyncHash() } catch (_: Exception) { null }
                val currentHash = try {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    md.update(bytes)
                    md.digest().joinToString("") { "%02x".format(it) }
                } catch (_: Exception) { null }
                if (lastHash != null && currentHash != null && lastHash == currentHash) {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                    return@withContext SyncResult.NoChange
                }
                val changed = ruteroManager.saveMasterExcel(bytes)
                if (currentHash != null) {
                    try { preferencesRepository.setLastSyncHash(currentHash) } catch (_: Exception) {}
                    try {
                        val etag = com.rutamercaderistas.data.network.headForETag(Constants.DRIVE_EXPORT_URL)
                        if (etag != null) preferencesRepository.setLastSyncETag(etag)
                    } catch (_: Exception) {}
                }
                if (changed) {
                    _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_indexando)))
                    val indexOk = ruteroManager.createIndex()
                    if (indexOk) {
                        _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_actualizando_promos)))
                        promotionRepository.refresh()
                        val newEntries = ruteroManager.loadAllEntries()
                        val changes = if (oldEntries.isEmpty()) PlanillaChanges() else computePlanillaChanges(oldEntries, newEntries)
                        val validationErrors = com.rutamercaderistas.domain.validation.PlanillaValidator.validateRutero(newEntries)
                        _state.value = _state.value.copy(state = SyncState.Idle, syncChanges = changes, validationErrors = validationErrors)
                        SyncResult.Success(true)
                    } else {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
                    }
                } else {
                    _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_actualizando_promos)))
                    promotionRepository.refresh()
                    _state.value = _state.value.copy(state = SyncState.Idle)
                    SyncResult.NoChange
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(state = SyncState.Idle)
                SyncResult.Error(e.message ?: getApplication<Application>().getString(R.string.sync_error_general))
            }
        }
    }

    fun syncFromDriveWithRouteReload(currentRoute: String?) {
        syncJob?.cancel()
        _state.value = _state.value.copy(state = SyncState.Syncing(), syncError = null, syncChanges = null, validationErrors = emptyList())
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
                            repository.clear()
                            repository.setEntries(entries, routeToLoad)
                        }
                    }
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        snackbarMessage = getApplication<Application>().getString(R.string.sync_datos_actualizados),
                    )
                }
                is SyncResult.Error -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        syncError = result.message,
                    )
                }
                is SyncResult.NoChange -> {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                }
                is SyncResult.Offline -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        syncError = getApplication<Application>().getString(R.string.sync_sin_conexion),
                    )
                }
            }
        }
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    fun clearChanges() {
        _state.value = _state.value.copy(syncChanges = null)
    }

    fun clearValidationErrors() {
        _state.value = _state.value.copy(validationErrors = emptyList())
    }

    private fun computePlanillaChanges(old: List<EntradaRuta>, new: List<EntradaRuta>): PlanillaChanges {
        fun key(e: EntradaRuta) = e.codigo.uppercase() + "|" + e.local.uppercase()
        fun days(entries: List<EntradaRuta>): Set<String> = entries.map { it.rutero }.toSet()
        val oldMap = old.groupBy(::key).mapValues { (_, v) -> days(v) to v.first().local }
        val newMap = new.groupBy(::key).mapValues { (_, v) -> days(v) to v.first().local }
        val added = newMap.keys.subtract(oldMap.keys).mapNotNull { newMap[it]?.second }.filter { it.isNotBlank() }
        val removed = oldMap.keys.subtract(newMap.keys).mapNotNull { oldMap[it]?.second }.filter { it.isNotBlank() }
        val moved = newMap.keys.intersect(oldMap.keys).mapNotNull { k ->
            val oldVal = oldMap[k] ?: return@mapNotNull null
            val newVal = newMap[k] ?: return@mapNotNull null
            if (oldVal.first != newVal.first) {
                MovedLocales(
                    local = oldVal.second,
                    fromDays = oldVal.first.sorted().joinToString(", "),
                    toDays = newVal.first.sorted().joinToString(", "),
                )
            } else null
        }
        return PlanillaChanges(added = added, removed = removed, moved = moved)
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

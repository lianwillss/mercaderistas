package com.rutamercaderistas.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.result.SyncResult
import com.rutamercaderistas.data.result.messageOrNull
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.services.ExcelParser
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
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class SyncUiState(
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    @ApplicationContext private val context: Context,
    private val ruteroManager: RuteroManager,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var syncJob: kotlinx.coroutines.Job? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback!!
            )
        } catch (_: Exception) { }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }

    fun refresh() {
        syncFromDrive()
    }

    fun syncFromDrive() {
        syncJob?.cancel()
        _state.value = _state.value.copy(isSyncing = true)
        syncJob = viewModelScope.launch {
            val result = performDriveSync()
            _state.value = _state.value.copy(isSyncing = false)
            result.messageOrNull()?.let { msg ->
                _state.value = _state.value.copy(snackbarMessage = msg)
            }
        }
    }

    private suspend fun performDriveSync(): SyncResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val cacheBustedUrl = "${Constants.DRIVE_EXPORT_URL}&ts=${System.currentTimeMillis()}"
                val bytes = downloadWithRetries(cacheBustedUrl)
                    ?: return@withContext SyncResult.Error("Error de descarga desde Drive")

                val changed = ruteroManager.saveMasterExcel(bytes)
                if (changed) {
                    ruteroManager.invalidateAllCaches()
                    RuteroRepository.clear()
                    val indexOk = ruteroManager.createIndex()
                    if (indexOk) {
                        SyncResult.Success(true)
                    } else {
                        SyncResult.Error("No se pudo leer el Excel")
                    }
                } else {
                    SyncResult.NoChange
                }
            } catch (e: Exception) {
                SyncResult.Error(e.message ?: "Error de sincronización")
            }
        }
    }

    fun syncFromDriveWithRouteReload(currentRoute: String?) {
        syncJob?.cancel()
        _state.value = _state.value.copy(isSyncing = true)
        syncJob = viewModelScope.launch {
            val result = performDriveSync()
            when (result) {
                is SyncResult.Success -> {
                    if (currentRoute != null) {
                        val index = ruteroManager.loadIndex()
                        if (index.contains(currentRoute)) {
                            val entries = ruteroManager.loadRoute(currentRoute)
                            if (entries.isNotEmpty()) {
                                RuteroRepository.setEntries(entries, currentRoute)
                            }
                        }
                    }
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        snackbarMessage = "Datos actualizados",
                    )
                }
                is SyncResult.Error -> {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        snackbarMessage = result.message,
                    )
                }
                is SyncResult.NoChange -> {
                    _state.value = _state.value.copy(isSyncing = false)
                }
                is SyncResult.Offline -> {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        snackbarMessage = "Sin conexión",
                    )
                }
            }
        }
    }

    fun loadLocalFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isSyncing = true)
                val success = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ruteroManager.saveMasterExcel(stream.readBytes())
                    } ?: false
                }
                if (success) {
                    ruteroManager.invalidateAllCaches()
                    RuteroRepository.clear()
                    ruteroManager.createIndex()
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        snackbarMessage = "Archivo cargado correctamente",
                    )
                } else {
                    _state.value = _state.value.copy(
                        isSyncing = false,
                        snackbarMessage = "No se pudo guardar el archivo",
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSyncing = false,
                    snackbarMessage = "Error: ${e.message}",
                )
            }
        }
    }

    fun pasteLink() {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.contains("drive.google.com") || text.contains("docs.google.com")) {
                val url = convertDriveUrl(text)
                syncFromDrive()
            } else {
                _state.value = _state.value.copy(
                    snackbarMessage = "Copia primero el link de Google Drive"
                )
            }
        }
    }

    fun downloadPdf() {
        viewModelScope.launch {
            BrandReference.descargarPdf(context)
        }
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    private suspend fun downloadBytes(url: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            var currentUrl = url
            var limit = 5
            while (limit > 0) {
                var conn: HttpURLConnection? = null
                try {
                    conn = URL(currentUrl).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = Constants.CONNECT_TIMEOUT_MS
                    conn.readTimeout = Constants.READ_TIMEOUT_MS
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")

                    val status = conn.responseCode
                    if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                        currentUrl = conn.getHeaderField("Location") ?: return@withContext null
                        limit--
                        continue
                    }
                    return@withContext conn.inputStream.use { it.readBytes() }
                } catch (e: Exception) {
                    return@withContext null
                } finally { conn?.disconnect() }
            }
            null
        }
    }

    private suspend fun downloadWithRetries(url: String, retries: Int = 3): ByteArray? {
        var attempt = 0
        while (attempt < retries) {
            try {
                return downloadBytes(url)
            } catch (_: Exception) {
                attempt++
                if (attempt >= retries) return null
                kotlinx.coroutines.delay(1000L * (1 shl attempt))
            }
        }
        return null
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

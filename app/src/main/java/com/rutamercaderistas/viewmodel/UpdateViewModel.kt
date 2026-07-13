package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class UpdateUiState(
    val showDialog: Boolean = false,
    val versionName: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val isChecking: Boolean = false,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
    private val preferencesRepository: PreferencesRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate(force: Boolean = false, showFeedback: Boolean = true) {
        viewModelScope.launch {
            if (!force) {
                val suprimidoHasta = withContext(Dispatchers.IO) {
                    preferencesRepository.getUpdateSuppressedUntil()
                }
                if (System.currentTimeMillis() < suprimidoHasta) return@launch
            }

            _state.value = _state.value.copy(isChecking = true)
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_CODE) }
                if (info.available) {
                    _state.value = _state.value.copy(
                        isChecking = false,
                        showDialog = true,
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        apkUrl = info.apkUrl,
                    )
                } else if (showFeedback) {
                    _state.value = _state.value.copy(
                        isChecking = false,
                        snackbarMessage = "Sin actualizaciones disponibles",
                    )
                } else {
                    _state.value = _state.value.copy(isChecking = false)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    isChecking = false,
                    snackbarMessage = if (showFeedback) "Error al buscar actualización" else null,
                )
            }
        }
    }

    fun downloadAndInstall() {
        val context = getApplication<Application>()
        _state.value = _state.value.copy(downloading = true, downloadProgress = 0)
        viewModelScope.launch {
            val ok = ApkDownloader.downloadAndInstall(
                context,
                _state.value.apkUrl,
            ) { pct -> _state.value = _state.value.copy(downloadProgress = pct) }
            if (ok) {
                _state.value = _state.value.copy(showDialog = false, downloading = false)
            } else {
                _state.value = _state.value.copy(
                    downloading = false,
                    snackbarMessage = "Error al descargar la actualización",
                )
            }
        }
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    fun suppressUntilTomorrow() {
        viewModelScope.launch(Dispatchers.IO) {
            val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
            preferencesRepository.setUpdateSuppressedUntil(manana)
            _state.value = _state.value.copy(showDialog = false)
        }
    }

    fun dismissDialog() {
        if (!_state.value.downloading) {
            _state.value = _state.value.copy(showDialog = false)
        }
    }
}

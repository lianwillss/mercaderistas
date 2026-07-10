package com.rutamercaderistas.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.Constants
import com.rutamercaderistas.data.result.UpdateResult
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val showDialog: Boolean = false,
    val versionName: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val isChecked: Boolean = false,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun checkForUpdate() {
        val suprimidoHasta = prefs.getLong(Constants.KEY_UPDATE_SUPPRESSED_UNTIL, 0L)
        if (System.currentTimeMillis() < suprimidoHasta) return

        viewModelScope.launch {
            try {
                val info = UpdateChecker.check(BuildConfig.VERSION_CODE)
                if (info.available) {
                    _state.value = _state.value.copy(
                        showDialog = true,
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        apkUrl = info.apkUrl,
                        isChecked = true,
                    )
                } else {
                    _state.value = _state.value.copy(isChecked = true)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(isChecked = true)
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
            ) { pct -> _downloadProgress.value = pct; _state.value = _state.value.copy(downloadProgress = pct) }
            if (ok) {
                _state.value = _state.value.copy(showDialog = false, downloading = false)
            } else {
                _state.value = _state.value.copy(downloading = false)
            }
        }
    }

    fun suppressUntilTomorrow() {
        val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
        prefs.edit().putLong(Constants.KEY_UPDATE_SUPPRESSED_UNTIL, manana).apply()
        _state.value = _state.value.copy(showDialog = false)
    }

    fun dismissDialog() {
        if (!_state.value.downloading) {
            _state.value = _state.value.copy(showDialog = false)
        }
    }
}

package com.rutamercaderistas.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationCompat
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.Constants
import com.rutamercaderistas.MainActivity
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

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Dialog(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val downloading: Boolean = false,
        val downloadProgress: Int = 0,
    ) : UpdateUiState
    data class Message(val text: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
    private val preferencesRepository: PreferencesRepository,
) : AndroidViewModel(application) {

    companion object {
        const val UPDATE_NOTIFICATION_ID = 2001
        const val UPDATE_CHANNEL_ID = "app_updates"
        const val EXTRA_SHOW_UPDATE = "show_update"
    }

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var pendingVersionName = ""
    private var pendingVersionCode = 0
    private var pendingApkUrl = ""

    fun checkForUpdate(force: Boolean = false, showFeedback: Boolean = true) {
        viewModelScope.launch {
            if (!force) {
                val suprimidoHasta = withContext(Dispatchers.IO) {
                    preferencesRepository.getUpdateSuppressedUntil()
                }
                if (System.currentTimeMillis() < suprimidoHasta) return@launch
            }

            _state.value = UpdateUiState.Checking
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_CODE) }
                if (info.available) {
                    pendingVersionName = info.versionName
                    pendingVersionCode = info.versionCode
                    pendingApkUrl = info.apkUrl
                    _state.value = UpdateUiState.Dialog(
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        apkUrl = info.apkUrl,
                    )
                    postUpdateNotification(info.versionName)
                } else if (showFeedback) {
                    _state.value = UpdateUiState.Message("Sin actualizaciones disponibles")
                } else {
                    _state.value = UpdateUiState.Idle
                }
            } catch (_: Exception) {
                _state.value = if (showFeedback) {
                    UpdateUiState.Message("Error al buscar actualización")
                } else {
                    UpdateUiState.Idle
                }
            }
        }
    }

    fun downloadAndInstall() {
        val context = getApplication<Application>()
        _state.value = UpdateUiState.Dialog(
            versionName = pendingVersionName,
            versionCode = pendingVersionCode,
            apkUrl = pendingApkUrl,
            downloading = true,
        )
        viewModelScope.launch {
            val ok = ApkDownloader.downloadAndInstall(
                context,
                pendingApkUrl,
            ) { pct -> _state.value = UpdateUiState.Dialog(
                versionName = pendingVersionName,
                versionCode = pendingVersionCode,
                apkUrl = pendingApkUrl,
                downloading = true,
                downloadProgress = pct,
            ) }
            if (ok) {
                _state.value = UpdateUiState.Idle
            } else {
                _state.value = UpdateUiState.Message("Error al descargar la actualización")
            }
        }
    }

    fun clearSnackbar() {
        _state.value = UpdateUiState.Idle
    }

    fun suppressUntilTomorrow() {
        viewModelScope.launch {
            val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
            withContext(Dispatchers.IO) {
                preferencesRepository.setUpdateSuppressedUntil(manana)
            }
            _state.value = UpdateUiState.Idle
        }
    }

    fun dismissDialog() {
        val current = _state.value
        if (current is UpdateUiState.Dialog && !current.downloading) {
            _state.value = UpdateUiState.Idle
        }
    }

    private fun postUpdateNotification(versionName: String) {
        val context = getApplication<Application>()
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Actualización disponible")
            .setContentText("Nueva versión $versionName disponible")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(UPDATE_NOTIFICATION_ID, notification)
    }
}

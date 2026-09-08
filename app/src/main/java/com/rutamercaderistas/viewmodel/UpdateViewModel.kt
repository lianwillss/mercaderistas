package com.rutamercaderistas.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationCompat
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.Constants
import com.rutamercaderistas.MainActivity
import com.rutamercaderistas.R
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.DownloadResult
import com.rutamercaderistas.services.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _pendingUpdate = MutableStateFlow<PendingUpdate?>(null)
    val pendingUpdate: StateFlow<PendingUpdate?> = _pendingUpdate.asStateFlow()

    private val _suppressed = MutableStateFlow(false)

    /** Banner persistente: hay update pendiente y no está pospuesto. */
    val showUpdateBanner: StateFlow<Boolean> =
        combine(_pendingUpdate, _suppressed) { pending, suppressed ->
            pending != null && !suppressed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var pendingVersionName = ""
    private var pendingVersionCode = 0
    private var pendingApkUrl = ""

    init {
        restorePendingUpdate()
    }

    /** Recupera el update pendiente guardado si sigue siendo más nuevo que lo instalado. */
    private fun restorePendingUpdate() {
        viewModelScope.launch {
            _suppressed.value = withContext(Dispatchers.IO) {
                System.currentTimeMillis() < preferencesRepository.getUpdateSuppressedUntil()
            }
            val stored = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
            if (stored != null && stored.versionCode > BuildConfig.VERSION_CODE) {
                _pendingUpdate.value = stored
            } else if (stored != null) {
                withContext(Dispatchers.IO) { preferencesRepository.clearPendingUpdate() }
            }
        }
    }

    /** Reabre el diálogo con el update pendiente guardado (sin red). */
    fun showPendingDialog() {
        val pending = _pendingUpdate.value ?: return
        pendingVersionName = pending.versionName
        pendingVersionCode = pending.versionCode
        pendingApkUrl = pending.apkUrl
        _state.value = UpdateUiState.Dialog(
            versionName = pending.versionName,
            versionCode = pending.versionCode,
            apkUrl = pending.apkUrl,
        )
    }

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
                    withContext(Dispatchers.IO) {
                        preferencesRepository.setPendingUpdate(
                            PendingUpdate(info.versionName, info.versionCode, info.apkUrl)
                        )
                    }
                    _pendingUpdate.value = PendingUpdate(info.versionName, info.versionCode, info.apkUrl)
                    _state.value = UpdateUiState.Dialog(
                        versionName = info.versionName,
                        versionCode = info.versionCode,
                        apkUrl = info.apkUrl,
                    )
                    notifyOnce(info.versionCode, info.versionName)
                } else {
                    clearStoredPending()
                    _state.value = if (showFeedback) {
                        UpdateUiState.Message(
                            getApplication<Application>().getString(R.string.update_no_disponible)
                        )
                    } else {
                        UpdateUiState.Idle
                    }
                }
            } catch (_: Exception) {
                _state.value = if (showFeedback) {
                    UpdateUiState.Message(
                        getApplication<Application>().getString(R.string.update_error_buscar)
                    )
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
            val result = ApkDownloader.download(
                context,
                pendingApkUrl,
            ) { pct -> _state.value = UpdateUiState.Dialog(
                versionName = pendingVersionName,
                versionCode = pendingVersionCode,
                apkUrl = pendingApkUrl,
                downloading = true,
                downloadProgress = pct,
            ) }
            when (result) {
                is DownloadResult.Error -> {
                    _state.value = UpdateUiState.Message(result.message)
                }
                is DownloadResult.Success -> {
                    val apkVersion = ApkDownloader.readApkVersionCode(context, result.file)
                    if (apkVersion <= BuildConfig.VERSION_CODE) {
                        _state.value = UpdateUiState.Message(
                            getApplication<Application>().getString(R.string.update_version_no_reciente)
                        )
                        return@launch
                    }
                    postReadyNotification(pendingVersionName)
                    val installError = withContext(Dispatchers.Main) {
                        ApkDownloader.installApk(context, result.file)
                    }
                    if (installError != null) {
                        _state.value = UpdateUiState.Message(installError)
                    } else {
                        _state.value = UpdateUiState.Idle
                    }
                }
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
            _suppressed.value = true
            _state.value = UpdateUiState.Idle
        }
    }

    private suspend fun clearStoredPending() {
        withContext(Dispatchers.IO) { preferencesRepository.clearPendingUpdate() }
        _pendingUpdate.value = null
    }

    /** Notifica una sola vez por versión (evita spam en cada apertura). */
    private suspend fun notifyOnce(versionCode: Int, versionName: String) {
        val lastNotified = withContext(Dispatchers.IO) {
            preferencesRepository.getLastNotifiedUpdateCode()
        }
        if (lastNotified == versionCode) return
        withContext(Dispatchers.IO) {
            preferencesRepository.setLastNotifiedUpdateCode(versionCode)
        }
        postUpdateNotification(versionName)
    }

    fun dismissDialog() {
        val current = _state.value
        if (current is UpdateUiState.Dialog && !current.downloading) {
            _state.value = UpdateUiState.Idle
        }
    }

    private fun postUpdateNotification(versionName: String) {
        val context = getApplication<Application>()
        postChannelNotification(
            title = context.getString(R.string.update_notif_disponible),
            text = context.getString(R.string.update_notif_nueva, versionName),
        )
    }

    private fun postReadyNotification(versionName: String) {
        val context = getApplication<Application>()
        postChannelNotification(
            title = context.getString(R.string.update_notif_lista),
            text = context.getString(R.string.update_notif_toca, versionName),
        )
    }

    private fun postChannelNotification(title: String, text: String) {
        val context = getApplication<Application>()
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(UPDATE_NOTIFICATION_ID, notification)
    }
}

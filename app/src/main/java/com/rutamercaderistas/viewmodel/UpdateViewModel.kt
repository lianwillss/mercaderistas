package com.rutamercaderistas.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.rutamercaderistas.BuildConfig
import com.rutamercaderistas.Constants
import com.rutamercaderistas.R
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateNotifier
import com.rutamercaderistas.worker.UpdateDownloadManager
import com.rutamercaderistas.worker.UpdateDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** Fase visible del aviso de actualización. */
enum class UpdatePhase { AVAILABLE, DOWNLOADING, READY, FAILED }

sealed interface UpdateCardUiState {
    val versionName: String
    val versionCode: Int

    data class Available(
        override val versionName: String,
        override val versionCode: Int,
    ) : UpdateCardUiState

    data class Downloading(
        override val versionName: String,
        override val versionCode: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateCardUiState {
        val percent: Int
            get() = if (totalBytes <= 0L) 0
            else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

    data class Ready(
        override val versionName: String,
        override val versionCode: Int,
    ) : UpdateCardUiState

    data class Failed(
        override val versionName: String,
        override val versionCode: Int,
        val message: String,
        val retryable: Boolean = true,
    ) : UpdateCardUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
    private val preferencesRepository: PreferencesRepository,
    private val downloadManager: UpdateDownloadManager,
) : AndroidViewModel(application) {

    companion object {
        const val UPDATE_NOTIFICATION_ID = UpdateNotifier.PENDING_NOTIFICATION_ID
        const val UPDATE_CHANNEL_ID = UpdateNotifier.CHANNEL_ID
        const val EXTRA_SHOW_UPDATE = UpdateNotifier.EXTRA_SHOW_UPDATE
        const val EXTRA_INSTALL_UPDATE = UpdateNotifier.EXTRA_INSTALL_UPDATE

        /** Al volver a la app no se consulta GitHub más de una vez cada 15 min. */
        private const val UPDATE_CHECK_MIN_INTERVAL_MS = 15 * 60_000L
    }

    private data class DownloadSnapshot(
        val phase: UpdatePhase,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val message: String = "",
        val retryable: Boolean = true,
    )

    private val _pendingUpdate = MutableStateFlow<PendingUpdate?>(null)
    val pendingUpdate: StateFlow<PendingUpdate?> = _pendingUpdate.asStateFlow()

    private val _suppressed = MutableStateFlow(false)
    private val _download = MutableStateFlow<DownloadSnapshot?>(null)
    private val _showSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showSheet.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Banner persistente: hay update pendiente y no está pospuesto. */
    val showUpdateBanner: StateFlow<Boolean> =
        combine(_pendingUpdate, _suppressed) { pending, suppressed ->
            pending != null && !suppressed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Estado que pinta la tarjeta y el modal. */
    val card: StateFlow<UpdateCardUiState?> =
        combine(_pendingUpdate, _download) { pending, snapshot ->
            if (pending == null) return@combine null
            when (snapshot?.phase) {
                UpdatePhase.DOWNLOADING -> UpdateCardUiState.Downloading(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    downloadedBytes = snapshot.downloadedBytes,
                    totalBytes = snapshot.totalBytes,
                )
                UpdatePhase.READY -> UpdateCardUiState.Ready(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                )
                UpdatePhase.FAILED -> UpdateCardUiState.Failed(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    message = snapshot.message,
                    retryable = snapshot.retryable,
                )
                UpdatePhase.AVAILABLE, null -> UpdateCardUiState.Available(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var isForeground = false
    private var wasPaused = false
    private var awaitingInstallPermission = false
    private var autoInstallHandled = false

    private var restoreJob: Job? = null

    init {
        restoreJob = viewModelScope.launch { restorePendingUpdate() }
        observeDownloadWork()
    }

    /** Recupera el pendiente guardado y, si ya hay APK válido, lo marca listo. */
    private suspend fun restorePendingUpdate() {
        _suppressed.value = withContext(Dispatchers.IO) {
            System.currentTimeMillis() < preferencesRepository.getUpdateSuppressedUntil()
        }
        val stored = withContext(Dispatchers.IO) {
            preferencesRepository.getPendingUpdate()
        }
        if (stored == null) return

        if (!UpdateChecker.isPendingNewer(
                stored.versionTag,
                stored.versionCode,
                BuildConfig.VERSION_CODE,
            )
        ) {
            cleanupResolved(showMessage = false)
            return
        }

        val context = getApplication<Application>()
        _pendingUpdate.value = stored

        // Reafirma el aviso persistente al abrir: si el sistema lo quitó,
        // vuelve a aparecer sin sonar otra vez (mismo ID y onlyAlertOnce).
        if (UpdateNotifier.hasPermission(context)) {
            UpdateNotifier.postPending(context, stored.versionName)
        }
        if (stored.versionTag.isNotBlank()) {
            withContext(Dispatchers.IO) {
                preferencesRepository.setLastNotifiedUpdateTag(stored.versionTag)
            }
        }

        val ready = withContext(Dispatchers.IO) {
            val apk = ApkDownloader.apkFile(context)
            if (!apk.exists()) {
                null
            } else {
                val code = ApkDownloader.readApkVersionCode(context, apk)
                if (code > BuildConfig.VERSION_CODE) {
                    DownloadSnapshot(
                        phase = UpdatePhase.READY,
                        downloadedBytes = apk.length(),
                        totalBytes = apk.length(),
                    )
                } else {
                    ApkDownloader.clearFinalApk(context)
                    null
                }
            }
        }
        // No pisa un progreso que el observer ya esté mostrando.
        if (ready != null) _download.value = ready
    }

    private fun observeDownloadWork() {
        viewModelScope.launch {
            downloadManager.observe().collect { infos ->
                // Con unique work REPLACE quedan trabajos viejos en el
                // historial: se prioriza el activo y, si no hay, el último.
                val active = infos.lastOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                }
                val info = active ?: infos.lastOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        if (_pendingUpdate.value != null &&
                            _download.value?.phase != UpdatePhase.READY &&
                            _download.value?.phase != UpdatePhase.FAILED
                        ) {
                            _download.value = DownloadSnapshot(UpdatePhase.DOWNLOADING)
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        val bytes = info.progress.getLong(UpdateDownloadWorker.KEY_BYTES, 0L)
                        val total = info.progress.getLong(UpdateDownloadWorker.KEY_TOTAL_BYTES, 0L)
                        _download.value = DownloadSnapshot(
                            phase = UpdatePhase.DOWNLOADING,
                            downloadedBytes = bytes,
                            totalBytes = total,
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> onDownloadSucceeded()
                    WorkInfo.State.FAILED -> {
                        val context = getApplication<Application>()
                        // Puede quedar un APK completo de un intento anterior:
                        // en ese caso la tarjeta sigue en "lista para instalar".
                        val readySnapshot = withContext(Dispatchers.IO) {
                            val apk = ApkDownloader.apkFile(context)
                            val apkInfo = if (apk.exists()) {
                                ApkDownloader.readApkInfo(context, apk)
                            } else {
                                null
                            }
                            if (apkInfo != null && apkInfo.versionCode > BuildConfig.VERSION_CODE) {
                                DownloadSnapshot(
                                    phase = UpdatePhase.READY,
                                    downloadedBytes = apk.length(),
                                    totalBytes = apk.length(),
                                )
                            } else {
                                null
                            }
                        }
                        if (readySnapshot != null) {
                            _download.value = readySnapshot
                        } else {
                            val msg = info.outputData
                                .getString(UpdateDownloadWorker.KEY_ERROR)
                                .orEmpty()
                                .ifBlank { context.getString(R.string.update_descarga_error) }
                            val retryable = info.outputData
                                .getBoolean(UpdateDownloadWorker.KEY_RETRYABLE, true)
                            _download.value = DownloadSnapshot(
                                phase = UpdatePhase.FAILED,
                                message = msg,
                                retryable = retryable,
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        // Un APK ya validado no se pierde por un trabajo viejo.
                        if (_download.value?.phase != UpdatePhase.READY) {
                            _download.value = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun onDownloadSucceeded() {
        val context = getApplication<Application>()
        if (_pendingUpdate.value == null) {
            // El ViewModel todavía puede estar restaurando el pendiente;
            // no borrar el APK en esa carrera (lo valida restorePendingUpdate).
            return
        }
        val snapshot = withContext(Dispatchers.IO) {
            val apk = ApkDownloader.apkFile(context)
            val info = ApkDownloader.readApkInfo(context, apk)
            if (info == null || info.versionCode <= BuildConfig.VERSION_CODE) {
                ApkDownloader.clearDownloadedApk(context)
                null
            } else {
                DownloadSnapshot(
                    phase = UpdatePhase.READY,
                    downloadedBytes = apk.length(),
                    totalBytes = apk.length(),
                )
            }
        }
        if (snapshot == null) {
            _download.value = DownloadSnapshot(
                phase = UpdatePhase.FAILED,
                message = context.getString(R.string.update_version_no_reciente),
                retryable = true,
            )
            return
        }
        _download.value = snapshot
        // Si el usuario estaba en la app, el instalador aparece solo. En
        // segundo plano queda la notificación persistente "lista".
        if (isForeground && !autoInstallHandled) {
            autoInstallHandled = true
            installApk()
        }
    }

    fun checkForUpdate(force: Boolean = false, showFeedback: Boolean = true) {
        viewModelScope.launch {
            // Restaurar primero evita que un pendiente viejo borre el nuevo.
            restoreJob?.join()
            if (_checking.value) return@launch
            if (!force) {
                val suprimidoHasta = withContext(Dispatchers.IO) {
                    preferencesRepository.getUpdateSuppressedUntil()
                }
                if (System.currentTimeMillis() < suprimidoHasta) return@launch
            }

            _checking.value = true
            val info = try {
                withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_CODE) }
            } catch (e: Exception) {
                Timber.w(e, "Error consultando actualización")
                null
            } finally {
                _checking.value = false
            }

            if (info == null) {
                if (showFeedback) {
                    _message.value = getApplication<Application>()
                        .getString(R.string.update_error_buscar)
                }
                return@launch
            }
            if (info.error != null) {
                // Un fallo transitorio no borra el pendiente ni el aviso.
                if (showFeedback) {
                    _message.value = if (info.error.contains("conexión", ignoreCase = true)) {
                        getApplication<Application>().getString(R.string.update_sin_internet)
                    } else {
                        getApplication<Application>().getString(R.string.update_error_buscar)
                    }
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                preferencesRepository.setLastUpdateCheck(System.currentTimeMillis())
            }

            if (info.available) {
                val pending = PendingUpdate(
                    versionName = info.versionName,
                    versionCode = info.versionCode,
                    apkUrl = info.apkUrl,
                    versionTag = info.tag,
                )
                withContext(Dispatchers.IO) {
                    preferencesRepository.setPendingUpdate(pending)
                }
                val same = _pendingUpdate.value?.versionTag == pending.versionTag &&
                    _pendingUpdate.value?.versionCode == pending.versionCode
                _pendingUpdate.value = pending
                if (!same && _download.value?.phase != UpdatePhase.DOWNLOADING) {
                    // Si el pendiente cambió de tag pero el APK ya está
                    // completo, conservar "lista para instalar".
                    _download.value = withContext(Dispatchers.IO) {
                        val context = getApplication<Application>()
                        val apk = ApkDownloader.apkFile(context)
                        val apkInfo = if (apk.exists()) {
                            ApkDownloader.readApkInfo(context, apk)
                        } else {
                            null
                        }
                        if (apkInfo != null && apkInfo.versionCode > BuildConfig.VERSION_CODE) {
                            DownloadSnapshot(UpdatePhase.READY, apk.length(), apk.length())
                        } else {
                            null
                        }
                    }
                    autoInstallHandled = false
                }
                notifyOnce(info.tag, info.versionName)
                if (showFeedback) _showSheet.value = true
            } else {
                cleanupResolved(showMessage = showFeedback)
            }
        }
    }

    /** Consulta al volver a la app, con throttle para no golpear GitHub. */
    fun checkForUpdateIfStale() {
        viewModelScope.launch {
            val last = withContext(Dispatchers.IO) {
                preferencesRepository.getLastUpdateCheck()
            }
            if (System.currentTimeMillis() - last >= UPDATE_CHECK_MIN_INTERVAL_MS) {
                checkForUpdate(force = false, showFeedback = false)
            }
        }
    }

    /** Abre el modal y dispara (o reanuda) la descarga del pendiente. */
    fun startUpdate() {
        val pending = _pendingUpdate.value
        _showSheet.value = true
        if (pending == null) return

        val phase = _download.value?.phase
        if (phase == UpdatePhase.READY) {
            installApk()
            return
        }
        if (phase == UpdatePhase.DOWNLOADING) return

        autoInstallHandled = false
        _download.value = DownloadSnapshot(UpdatePhase.DOWNLOADING)
        downloadManager.enqueue(pending)
    }

    fun retryDownload() {
        startUpdate()
    }

    /** Cancela y borra el .part: el próximo intento empieza limpio. */
    fun cancelDownload() {
        autoInstallHandled = false
        downloadManager.cancel()
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ApkDownloader.clearPartial(context)
        }
        _download.value = null
        _showSheet.value = false
    }

    fun installApk() {
        val context = getApplication<Application>()
        _showSheet.value = true

        // No se exige _pendingUpdate: al abrir desde la notificación el
        // ViewModel puede estar restaurando el pendiente todavía. El APK ya
        // fue validado por el worker antes de publicar "lista para instalar".
        val apk = ApkDownloader.apkFile(context)
        if (!apk.exists()) {
            val message = context.getString(R.string.update_archivo_faltante)
            _download.value = DownloadSnapshot(
                phase = UpdatePhase.FAILED,
                message = message,
                retryable = true,
            )
            _message.value = message
            return
        }

        val apkInfo = ApkDownloader.readApkInfo(context, apk)
        if (apkInfo == null || apkInfo.versionCode <= BuildConfig.VERSION_CODE) {
            ApkDownloader.clearDownloadedApk(context)
            val message = context.getString(R.string.update_version_no_reciente)
            _download.value = DownloadSnapshot(
                phase = UpdatePhase.FAILED,
                message = message,
                retryable = true,
            )
            _message.value = message
            return
        }

        if (!ApkDownloader.canInstallPackages(context)) {
            awaitingInstallPermission = true
            _message.value = context.getString(R.string.update_permiso_instalar)
            if (!ApkDownloader.openInstallPermissionSettings(context)) {
                awaitingInstallPermission = false
                _message.value = context.getString(R.string.update_error_abrir_ajustes)
            }
            return
        }

        awaitingInstallPermission = false
        val error = ApkDownloader.installApk(context, apk)
        if (error != null) {
            _download.value = DownloadSnapshot(
                phase = UpdatePhase.FAILED,
                message = error,
                retryable = true,
            )
        } else {
            _showSheet.value = false
        }
    }

    /** Acción "Instalar" de la notificación o de un intent externo. */
    fun onInstallRequested() {
        _showSheet.value = true
        installApk()
    }

    fun onActivityResumed() {
        isForeground = true
        // Solo se retoma el permiso al volver de Ajustes, no en el primer
        // onResume que ocurre justo después de abrirlos.
        val returningFromSettings = wasPaused
        wasPaused = false
        if (returningFromSettings && awaitingInstallPermission) {
            awaitingInstallPermission = false
            val context = getApplication<Application>()
            if (ApkDownloader.canInstallPackages(context)) {
                installApk()
            } else {
                _message.value = context.getString(R.string.update_permiso_no_concedido)
            }
        }
    }

    fun onActivityPaused() {
        isForeground = false
        wasPaused = true
    }

    /** Reabre el modal con el pendiente guardado (sin red). */
    fun showPendingDialog() {
        _showSheet.value = true
    }

    fun dismissSheet() {
        _showSheet.value = false
    }

    fun suppressUntilTomorrow() {
        viewModelScope.launch {
            val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
            withContext(Dispatchers.IO) {
                preferencesRepository.setUpdateSuppressedUntil(manana)
            }
            _suppressed.value = true
            _showSheet.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Compatibilidad con el nombre anterior del snackbar. */
    fun clearSnackbar() = clearMessage()

    private suspend fun cleanupResolved(showMessage: Boolean) {
        val context = getApplication<Application>()
        withContext(Dispatchers.IO) { preferencesRepository.clearPendingUpdate() }
        _pendingUpdate.value = null
        _download.value = null
        _showSheet.value = false
        downloadManager.cancel()
        UpdateNotifier.cancelUpdate(context)
        withContext(Dispatchers.IO) { ApkDownloader.clearDownloadedApk(context) }
        if (showMessage) _message.value = context.getString(R.string.update_no_disponible)
    }

    /**
     * Notifica una sola vez por tag (evita spam en cada apertura). Por tag y
     * no por code: v12.20.1 colisiona con v12.20 en versionCode.
     */
    private suspend fun notifyOnce(tag: String, versionName: String) {
        if (tag.isBlank()) {
            UpdateNotifier.postPending(getApplication(), versionName)
            return
        }
        val lastNotified = withContext(Dispatchers.IO) {
            preferencesRepository.getLastNotifiedUpdateTag()
        }
        if (lastNotified == tag) return
        withContext(Dispatchers.IO) {
            preferencesRepository.setLastNotifiedUpdateTag(tag)
        }
        UpdateNotifier.postPending(getApplication(), versionName)
    }

    /** ¿Falta el permiso de notificaciones (Android 13+)? */
    fun hasNotifPermission(): Boolean = UpdateNotifier.hasPermission(getApplication())

    /** true si corresponde pedir el permiso una vez (hay update visible). */
    suspend fun shouldAskNotifPermission(): Boolean {
        if (hasNotifPermission()) return false
        return withContext(Dispatchers.IO) { !preferencesRepository.wasUpdateNotifAsked() }
    }

    fun markNotifAsked() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { preferencesRepository.setUpdateNotifAsked() }
        }
    }

    /** Tras conceder el permiso con un update pendiente, avisar de inmediato. */
    fun onNotifPermissionResult(granted: Boolean) {
        if (!granted) return
        val pending = _pendingUpdate.value ?: return
        UpdateNotifier.postPending(getApplication(), pending.versionName)
    }
}

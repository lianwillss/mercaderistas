package com.rutamercaderistas.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.R
import com.rutamercaderistas.data.preferences.PendingUpdate
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.services.ApkDownloader
import com.rutamercaderistas.services.ApkValidation
import com.rutamercaderistas.services.DownloadProgress
import com.rutamercaderistas.services.DownloadResult
import com.rutamercaderistas.services.InstallOutcome
import com.rutamercaderistas.services.InstallStart
import com.rutamercaderistas.services.InstalledVersion
import com.rutamercaderistas.services.UpdateChecker
import com.rutamercaderistas.services.UpdateInstallBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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

/**
 * Único estado de actualización que consume la UI. No hay notificación ni
 * diálogo en paralelo: la tarjeta del banner es la única superficie.
 */
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
) : AndroidViewModel(application) {

    companion object {
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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Banner persistente: hay update pendiente y no está pospuesto. */
    val showUpdateBanner: StateFlow<Boolean> =
        combine(_pendingUpdate, _suppressed) { pending, suppressed ->
            pending != null && !suppressed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Estado que pinta la tarjeta: Disponible, Descargando(%), Lista, Error. */
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

    private var awaitingInstallPermission = false

    /** Evita abrir dos sesiones de PackageInstaller si se toca dos veces. */
    private var installSessionActive = false

    private var restoreJob: Job? = null
    private var downloadJob: Job? = null
    private var installJob: Job? = null

    init {
        restoreJob = viewModelScope.launch { restorePendingUpdate() }
        observeInstallOutcomes()
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

        // Un release ya rechazado no se restaura: evita mostrar la tarjeta
        // de un APK que nunca se podrá instalar.
        val rejected = withContext(Dispatchers.IO) {
            preferencesRepository.getRejectedUpdateTag()
        }
        if (UpdateChecker.isRejectedTag(stored.versionTag, rejected)) {
            cleanupResolved(showMessage = false)
            return
        }

        val installed = withContext(Dispatchers.IO) {
            InstalledVersion.read(getApplication())
        }
        if (!UpdateChecker.isPendingNewer(stored.versionTag, stored.versionCode, installed)) {
            cleanupResolved(showMessage = false)
            return
        }

        _pendingUpdate.value = stored
        val ready = evaluateDownloadedApk(stored)
        // No pisa un progreso que ya esté en curso.
        if (ready != null && downloadJob?.isActive != true) {
            _download.value = ready
        }
    }

    /**
     * Consulta GitHub y deja el resultado en el único estado compartido.
     * En arranque (autoStartDownload) arranca la descarga dentro de la app;
     * nunca instala sin que el usuario toque "Instalar".
     */
    fun checkForUpdate(
        force: Boolean = false,
        showFeedback: Boolean = true,
        autoStartDownload: Boolean = false,
    ) {
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
            val context = getApplication<Application>()
            val installed = withContext(Dispatchers.IO) { InstalledVersion.read(context) }
            val rejected = withContext(Dispatchers.IO) {
                preferencesRepository.getRejectedUpdateTag()
            }
            val info = try {
                withContext(Dispatchers.IO) {
                    UpdateChecker.check(installed, rejected)
                }
            } catch (e: Exception) {
                Timber.w(e, "Error consultando actualización")
                null
            } finally {
                _checking.value = false
            }

            if (info == null) {
                if (showFeedback) {
                    _message.value = context.getString(R.string.update_error_buscar)
                }
                return@launch
            }
            if (info.error != null) {
                // Un fallo transitorio no borra el pendiente ni el aviso.
                if (showFeedback) {
                    _message.value = if (info.error.contains("conexión", ignoreCase = true)) {
                        context.getString(R.string.update_sin_internet)
                    } else {
                        info.error
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
                    apkSize = info.apkSize,
                    apkSha256 = info.apkSha256,
                )
                // Si no hay espacio para descargar e instalar (Android duplica el
                // APK al instalar), no se guarda el pendiente ni se muestra el
                // banner: el usuario solo vería una actualización que no puede
                // aplicar. Se re-detecta en la próxima apertura si libera espacio.
                if (pending.apkSize > 0L) {
                    val missingMb = withContext(Dispatchers.IO) {
                        val dir = context.filesDir
                        val free = dir?.usableSpace ?: 0L
                        if (free == 0L) return@withContext null
                        val needed = ApkDownloader.espacioNecesarioParaInstalacion(pending.apkSize)
                        if (free >= needed) null else ((needed - free) / 1_000_000.0)
                    }
                    missingMb?.let { mb ->
                        _message.value = context.getString(
                            R.string.update_poco_espacio,
                            ApkDownloader.formatMb(mb),
                        )
                        return@launch
                    }
                }

                val previous = _pendingUpdate.value
                val sameRelease = previous?.versionTag == pending.versionTag &&
                    previous.versionCode == pending.versionCode

                if (!sameRelease) {
                    // Otro release: el APK y el .part viejos no sirven. Se
                    // borran ANTES de publicar el pendiente, para que una
                    // descarga recién arrancada no pierda su estado.
                    downloadJob?.cancel()
                    downloadJob = null
                    withContext(Dispatchers.IO) {
                        ApkDownloader.clearDownloadedApk(context)
                    }
                    _download.value = null
                }

                withContext(Dispatchers.IO) {
                    preferencesRepository.setPendingUpdate(pending)
                }
                _pendingUpdate.value = pending

                if (sameRelease && _download.value == null) {
                    // Mismo release: si ya hay un APK verificado, se muestra
                    // "Listo para instalar" sin volver a descargar.
                    val evaluated = evaluateDownloadedApk(pending)
                    if (evaluated != null) _download.value = evaluated
                }

                if (autoStartDownload) {
                    if (_suppressed.value) {
                        withContext(Dispatchers.IO) {
                            preferencesRepository.setUpdateSuppressedUntil(0)
                        }
                        _suppressed.value = false
                    }
                    startUpdate()
                }
            } else {
                cleanupResolved(showMessage = showFeedback)
            }
        }
    }

    /** Consulta obligatoria al abrir: si hay versión nueva, descarga sola. */
    fun checkForUpdateOnLaunch() {
        checkForUpdate(force = true, showFeedback = false, autoStartDownload = true)
    }

    /** Consulta al volver a la app, con throttle para no golpear GitHub. */
    fun checkForUpdateIfStale() {
        viewModelScope.launch {
            val last = withContext(Dispatchers.IO) {
                preferencesRepository.getLastUpdateCheck()
            }
            if (System.currentTimeMillis() - last >= UPDATE_CHECK_MIN_INTERVAL_MS) {
                checkForUpdate(force = false, showFeedback = false, autoStartDownload = true)
            }
        }
    }

    /**
     * Descarga el pendiente en el proceso de la app, actualizando el
     * porcentaje en la tarjeta. No usa WorkManager ni notificaciones.
     */
    fun startUpdate() {
        val pending = _pendingUpdate.value ?: return
        val phase = _download.value?.phase
        if (phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.READY) return
        if (downloadJob?.isActive == true) return

        val context = getApplication<Application>()
        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _download.value = DownloadSnapshot(UpdatePhase.DOWNLOADING)
            try {
                val result = ApkDownloader.download(
                    context = context,
                    apkUrl = pending.apkUrl,
                    expectedSize = pending.apkSize,
                    expectedSha256 = pending.apkSha256,
                    expectedVersionName = pending.versionName,
                    expectedVersionCode = pending.versionCode,
                ) { progress: DownloadProgress ->
                    _download.value = DownloadSnapshot(
                        phase = UpdatePhase.DOWNLOADING,
                        downloadedBytes = progress.bytes,
                        totalBytes = progress.totalBytes,
                    )
                }

                when (result) {
                    is DownloadResult.Success -> {
                        val evaluated = evaluateDownloadedApk(pending)
                        _download.value = evaluated ?: DownloadSnapshot(
                            phase = UpdatePhase.FAILED,
                            message = context.getString(R.string.update_archivo_faltante),
                            retryable = true,
                        )
                    }
                    is DownloadResult.Error -> {
                        if (result.releaseRejected) {
                            markReleaseRejected(pending.versionTag)
                        }
                        _download.value = DownloadSnapshot(
                            phase = UpdatePhase.FAILED,
                            message = result.message,
                            retryable = result.retryable,
                        )
                    }
                    DownloadResult.Cancelled -> _download.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error inesperado descargando la actualización")
                _download.value = DownloadSnapshot(
                    phase = UpdatePhase.FAILED,
                    message = context.getString(R.string.update_descarga_error),
                    retryable = true,
                )
            }
        }
    }

    fun retryDownload() {
        val pending = _pendingUpdate.value ?: return
        val failed = _download.value
        if (failed?.phase == UpdatePhase.FAILED && !failed.retryable) {
            // Error permanente del release: se olvida en vez de reintentar.
            viewModelScope.launch {
                markReleaseRejected(pending.versionTag)
                cleanupResolved(showMessage = false)
            }
            return
        }
        startUpdate()
    }

    /** Cancela y borra el .part: la tarjeta vuelve a "Disponible". */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _download.value = null
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ApkDownloader.clearPartial(context)
        }
    }

    /**
     * Verifica el APK descargado y recién entonces abre la sesión de
     * PackageInstaller. La confirmación del sistema queda fuera de la app,
     * pero el flujo (progreso, error, reintento) vive en la tarjeta.
     */
    fun installApk() {
        if (installSessionActive) return
        val context = getApplication<Application>()
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
        installJob?.cancel()
        installJob = viewModelScope.launch {
            restoreJob?.join()
            val pending = _pendingUpdate.value
            val validation = withContext(Dispatchers.IO) {
                ApkDownloader.validateApk(
                    context,
                    apk,
                    pending?.apkSize ?: 0L,
                    pending?.apkSha256.orEmpty(),
                    pending?.versionName.orEmpty(),
                    pending?.versionCode ?: 0,
                )
            }
            if (validation is ApkValidation.Failed) {
                installSessionActive = false
                withContext(Dispatchers.IO) {
                    ApkDownloader.clearDownloadedApk(context)
                    if (validation.releaseRejected && pending != null) {
                        markReleaseRejected(pending.versionTag)
                    }
                }
                _download.value = DownloadSnapshot(
                    phase = UpdatePhase.FAILED,
                    message = validation.message,
                    retryable = validation.retryable,
                )
                _message.value = validation.message
                return@launch
            }

            when (val start = withContext(Dispatchers.IO) {
                ApkDownloader.installApk(context, apk)
            }) {
                InstallStart.Started -> {
                    installSessionActive = true
                    _message.value = context.getString(R.string.update_confirmar_instalacion)
                }
                is InstallStart.Error -> {
                    installSessionActive = false
                    _download.value = DownloadSnapshot(
                        phase = UpdatePhase.FAILED,
                        message = start.message,
                        retryable = start.retryable,
                    )
                }
            }
        }
    }

    private fun observeInstallOutcomes() {
        viewModelScope.launch {
            UpdateInstallBus.events.collect { outcome ->
                when (outcome) {
                    InstallOutcome.Success -> onInstalled()
                    is InstallOutcome.Failed -> {
                        installSessionActive = false
                        val context = getApplication<Application>()
                        _download.value = DownloadSnapshot(
                            phase = UpdatePhase.FAILED,
                            message = outcome.message.ifBlank {
                                context.getString(R.string.update_instalacion_error)
                            },
                            retryable = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun onInstalled() {
        val context = getApplication<Application>()
        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = null
        withContext(Dispatchers.IO) {
            preferencesRepository.clearPendingUpdate()
            preferencesRepository.setRejectedUpdateTag("")
            ApkDownloader.clearDownloadedApk(context)
        }
        _pendingUpdate.value = null
        _download.value = null
    }

    fun onActivityResumed() {
        // Solo se retoma el permiso al volver de Ajustes.
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        val context = getApplication<Application>()
        if (ApkDownloader.canInstallPackages(context)) {
            installApk()
        } else {
            _message.value = context.getString(R.string.update_permiso_no_concedido)
        }
    }

    fun suppressUntilTomorrow() {
        val failed = _download.value
        if (failed?.phase == UpdatePhase.FAILED && !failed.retryable) {
            viewModelScope.launch { cleanupResolved(showMessage = false) }
            return
        }
        viewModelScope.launch {
            val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
            withContext(Dispatchers.IO) {
                preferencesRepository.setUpdateSuppressedUntil(manana)
            }
            _suppressed.value = true
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Compatibilidad con el nombre anterior del snackbar. */
    fun clearSnackbar() = clearMessage()

    /**
     * Evalúa un APK ya descargado. Solo devuelve READY si pasa tamaño,
     * SHA-256, release anunciado, versión, paquete y firma. Si no, borra el
     * archivo y reporta el motivo.
     */
    private suspend fun evaluateDownloadedApk(pending: PendingUpdate?): DownloadSnapshot? =
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val apk = ApkDownloader.apkFile(context)
            if (!apk.exists()) return@withContext null

            val info = ApkDownloader.readApkInfo(context, apk)
            if (info == null) {
                ApkDownloader.clearDownloadedApk(context)
                return@withContext null
            }

            when (
                val validation = ApkDownloader.validateApk(
                    context,
                    apk,
                    pending?.apkSize ?: 0L,
                    pending?.apkSha256.orEmpty(),
                    pending?.versionName.orEmpty(),
                    pending?.versionCode ?: 0,
                )
            ) {
                ApkValidation.Ok -> DownloadSnapshot(
                    phase = UpdatePhase.READY,
                    downloadedBytes = apk.length(),
                    totalBytes = apk.length(),
                )
                is ApkValidation.Failed -> {
                    ApkDownloader.clearDownloadedApk(context)
                    if (validation.releaseRejected && pending != null) {
                        markReleaseRejected(pending.versionTag)
                    }
                    DownloadSnapshot(
                        phase = UpdatePhase.FAILED,
                        message = validation.message,
                        retryable = validation.retryable,
                    )
                }
            }
        }

    private suspend fun markReleaseRejected(tag: String) {
        if (tag.isBlank()) return
        withContext(Dispatchers.IO) {
            preferencesRepository.setRejectedUpdateTag(tag)
        }
        Timber.w("Release %s rechazado: no se vuelve a ofrecer", tag)
    }

    private suspend fun cleanupResolved(showMessage: Boolean) {
        val context = getApplication<Application>()
        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = null
        withContext(Dispatchers.IO) {
            preferencesRepository.clearPendingUpdate()
            ApkDownloader.clearDownloadedApk(context)
        }
        _pendingUpdate.value = null
        _download.value = null
        if (showMessage) _message.value = context.getString(R.string.update_no_disponible)
    }
}

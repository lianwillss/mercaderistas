package com.rutamercaderistas.viewmodel

import android.app.Application
import android.os.SystemClock
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
import com.rutamercaderistas.services.DownloadSpeedMeter
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
enum class UpdatePhase { AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

/**
 * Único estado de actualización que consume la UI. No hay notificación ni
 * diálogo en paralelo: la tarjeta del banner es la única superficie.
 * La descarga no instala en primer plano: al quedar Ready se espera a que la
 * app pase a segundo plano (o a que el usuario toque Instalar). Installing
 * cubre la validación y la confirmación de Android.
 */
sealed interface UpdateCardUiState {
    val versionName: String
    val versionCode: Int

    data class Available(
        override val versionName: String,
        override val versionCode: Int,
        /** Versión que está instalada ahora: la tarjeta muestra "12.52 → 12.53". */
        val installedVersionName: String = "",
        /** Tamaño publicado del APK; 0 si el release no lo informa. */
        val apkSize: Long = 0L,
    ) : UpdateCardUiState

    data class Downloading(
        override val versionName: String,
        override val versionCode: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        /** Velocidad suavizada en bytes/s; 0 mientras se calcula. */
        val bytesPerSecond: Long = 0L,
        /** Segundos restantes estimados; null si aún no se puede calcular. */
        val etaSeconds: Long? = null,
        val installedVersionName: String = "",
        val apkSize: Long = 0L,
    ) : UpdateCardUiState {
        val percent: Int
            get() = if (totalBytes <= 0L) 0
            else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

    data class Ready(
        override val versionName: String,
        override val versionCode: Int,
        /** Ya se intentó instalar sola y Android no completó la instalación. */
        val installAttempted: Boolean = false,
    ) : UpdateCardUiState

    data class Installing(
        override val versionName: String,
        override val versionCode: Int,
        val installedVersionName: String = "",
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
        val bytesPerSecond: Long = 0L,
        val etaSeconds: Long? = null,
        val message: String = "",
        val retryable: Boolean = true,
    )

    private val _pendingUpdate = MutableStateFlow<PendingUpdate?>(null)
    val pendingUpdate: StateFlow<PendingUpdate?> = _pendingUpdate.asStateFlow()

    private val _suppressed = MutableStateFlow(false)
    private val _download = MutableStateFlow<DownloadSnapshot?>(null)
    private val _installedVersionName = MutableStateFlow(
        InstalledVersion.buildConfig().versionName,
    )

    /** versionCode del último intento automático, para no repetirlo al abrir. */
    private val _installAttemptedCode = MutableStateFlow(0)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Banner persistente: hay update pendiente y no está pospuesto. */
    val showUpdateBanner: StateFlow<Boolean> =
        combine(_pendingUpdate, _suppressed) { pending, suppressed ->
            pending != null && !suppressed
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Estado que pinta la tarjeta: Disponible, Descargando(%), Lista, Instalando, Error. */
    val card: StateFlow<UpdateCardUiState?> =
        combine(
            _pendingUpdate,
            _download,
            _installedVersionName,
            _installAttemptedCode,
        ) { pending, snapshot, installedName, attemptedCode ->
            if (pending == null) return@combine null
            when (snapshot?.phase) {
                UpdatePhase.DOWNLOADING -> UpdateCardUiState.Downloading(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    downloadedBytes = snapshot.downloadedBytes,
                    totalBytes = snapshot.totalBytes,
                    bytesPerSecond = snapshot.bytesPerSecond,
                    etaSeconds = snapshot.etaSeconds,
                    installedVersionName = installedName,
                    apkSize = pending.apkSize,
                )
                UpdatePhase.READY -> UpdateCardUiState.Ready(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    installAttempted = attemptedCode == pending.versionCode,
                )
                UpdatePhase.INSTALLING -> UpdateCardUiState.Installing(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    installedVersionName = installedName,
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
                    installedVersionName = installedName,
                    apkSize = pending.apkSize,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var awaitingInstallPermission = false

    /** Evita abrir dos sesiones de PackageInstaller si se toca dos veces. */
    private var installSessionActive = false

    /** Alguna Activity de la app está visible: la instalación espera a que se vaya. */
    private var isForeground = false
    /** versionCode para el que ya se intentó instalar sola al salir. */
    private var installAttemptedCode = 0

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
        _installedVersionName.value = installed.versionName
        if (!UpdateChecker.isPendingNewer(stored.versionTag, stored.versionCode, installed)) {
            cleanupResolved(showMessage = false)
            return
        }

        installAttemptedCode = withContext(Dispatchers.IO) {
            preferencesRepository.getUpdateInstallAttemptedCode()
        }
        _installAttemptedCode.value = installAttemptedCode

        _pendingUpdate.value = stored
        val ready = evaluateDownloadedApk(stored)
        // No pisa un progreso que ya esté en curso. No instala al restaurar:
        // el intento automático anterior ya quedó persistido y, si no se
        // completó, la tarjeta ofrece el botón Instalar.
        if (ready != null && downloadJob?.isActive != true) {
            _download.value = ready
        }
    }

    /**
     * Consulta GitHub y deja el resultado en el único estado compartido.
     * En arranque (autoStartDownload) arranca la descarga dentro de la app.
     * La instalación se dispara al salir de la app o al tocar "Instalar".
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
            _installedVersionName.value = installed.versionName
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
                        preferencesRepository.clearUpdateInstallAttemptedCode()
                    }
                    installAttemptedCode = 0
                    _installAttemptedCode.value = 0
                    _download.value = null
                }

                withContext(Dispatchers.IO) {
                    preferencesRepository.setPendingUpdate(pending)
                }
                _pendingUpdate.value = pending

                if (sameRelease && _download.value == null) {
                    // Mismo release: si ya hay un APK verificado, se instala
                    // sin volver a descargar.
                    val evaluated = evaluateDownloadedApk(pending)
                    if (evaluated != null) {
                        _download.value = evaluated
                    }
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
        if (phase == UpdatePhase.DOWNLOADING ||
            phase == UpdatePhase.READY ||
            phase == UpdatePhase.INSTALLING
        ) {
            return
        }
        if (downloadJob?.isActive == true) return

        val context = getApplication<Application>()
        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _download.value = DownloadSnapshot(UpdatePhase.DOWNLOADING)
            val speedMeter = DownloadSpeedMeter()
            try {
                val result = ApkDownloader.download(
                    context = context,
                    apkUrl = pending.apkUrl,
                    expectedSize = pending.apkSize,
                    expectedSha256 = pending.apkSha256,
                    expectedVersionName = pending.versionName,
                    expectedVersionCode = pending.versionCode,
                ) { progress: DownloadProgress ->
                    val bytesPerSecond = speedMeter.update(
                        bytes = progress.bytes,
                        nowMillis = SystemClock.elapsedRealtime(),
                    )
                    val remainingBytes = progress.totalBytes - progress.bytes
                    val etaSeconds = if (bytesPerSecond > 0L && remainingBytes > 0L) {
                        remainingBytes / bytesPerSecond
                    } else {
                        null
                    }
                    _download.value = DownloadSnapshot(
                        phase = UpdatePhase.DOWNLOADING,
                        downloadedBytes = progress.bytes,
                        totalBytes = progress.totalBytes,
                        bytesPerSecond = bytesPerSecond,
                        etaSeconds = etaSeconds,
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
                        if (evaluated?.phase == UpdatePhase.READY && !isForeground) {
                            // La descarga terminó con la app en segundo plano:
                            // se instala sin volver a abrirla.
                            maybeInstallWhenBackgrounded()
                        }
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
        // Si el APK ya está descargado, reintentar significa reinstalar;
        // no volver a bajar 52 MB por una confirmación cancelada.
        val context = getApplication<Application>()
        if (ApkDownloader.apkFile(context).exists()) {
            installApk()
            return
        }
        startUpdate()
    }

    /** Cancela y borra el .part: la tarjeta vuelve a "Disponible". */
    fun cancelDownload() {
        if (_download.value?.phase == UpdatePhase.INSTALLING) return
        downloadJob?.cancel()
        downloadJob = null
        _download.value = null
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            ApkDownloader.clearPartial(context)
        }
    }

    /**
     * Verifica el APK y abre la sesión de PackageInstaller. Se llama al tocar
     * "Instalar" o al salir de la app (automático); nunca al quedar Ready en
     * primer plano. Si el sistema exige confirmación, el receiver la abre
     * sola; el flujo (progreso, error, reintento) vive en la tarjeta.
     */
    fun installApk(automatic: Boolean = false) {
        if (installSessionActive || installJob?.isActive == true) return
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
            // Desde segundo plano no se pueden abrir Ajustes: se deja para
            // que el usuario toque Instalar al volver.
            if (automatic) return
            awaitingInstallPermission = true
            _message.value = context.getString(R.string.update_permiso_instalar)
            if (!ApkDownloader.openInstallPermissionSettings(context)) {
                awaitingInstallPermission = false
                _message.value = context.getString(R.string.update_error_abrir_ajustes)
            }
            return
        }

        awaitingInstallPermission = false
        _download.value = DownloadSnapshot(UpdatePhase.INSTALLING)
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

            if (automatic && isForeground) {
                // El usuario volvió antes de abrir la sesión: no se instala
                // encima de lo que está haciendo.
                _download.value = DownloadSnapshot(
                    phase = UpdatePhase.READY,
                    downloadedBytes = apk.length(),
                    totalBytes = apk.length(),
                )
                return@launch
            }

            if (pending != null) markInstallAttempted(pending.versionCode)

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

    /** Persiste el intento antes de commitear: un arranque en frío no lo repite. */
    private suspend fun markInstallAttempted(versionCode: Int) {
        installAttemptedCode = versionCode
        _installAttemptedCode.value = versionCode
        withContext(Dispatchers.IO) {
            preferencesRepository.setUpdateInstallAttemptedCode(versionCode)
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
        installAttemptedCode = 0
        _installAttemptedCode.value = 0
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
        isForeground = true
        // Solo se retoma el permiso al volver de Ajustes.
        if (awaitingInstallPermission) {
            awaitingInstallPermission = false
            val context = getApplication<Application>()
            if (ApkDownloader.canInstallPackages(context)) {
                installApk()
            } else {
                _message.value = context.getString(R.string.update_permiso_no_concedido)
            }
            return
        }
    }

    /**
     * La app dejó de estar visible: es el momento de instalar sin que el
     * cierre se sienta como una salida inesperada. Si el usuario vuelve antes
     * de que se abra la sesión, el intento automático se aborta.
     */
    fun onActivityStopped() {
        isForeground = false
        maybeInstallWhenBackgrounded()
    }

    /**
     * Instala sola solo si la app ya no está visible, el APK está listo y no
     * hubo un intento previo para esta versión. No abre Ajustes desde
     * segundo plano ni insiste después de un fallo.
     */
    private fun maybeInstallWhenBackgrounded() {
        if (isForeground) return
        if (_suppressed.value) return
        if (installSessionActive || installJob?.isActive == true) return
        if (awaitingInstallPermission) return
        val pending = _pendingUpdate.value ?: return
        if (_download.value?.phase != UpdatePhase.READY) return
        if (installAttemptedCode == pending.versionCode) return
        if (!ApkDownloader.canInstallPackages(getApplication())) return
        installApk(automatic = true)
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
        installAttemptedCode = 0
        _installAttemptedCode.value = 0
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

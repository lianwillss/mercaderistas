package com.rutamercaderistas.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.BuildConfig
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

@HiltViewModel
class UpdateViewModel @Inject constructor(
    application: Application,
    private val preferencesRepository: PreferencesRepository,
) : AndroidViewModel(application) {

    companion object {
        /** Al volver a la app no se consulta GitHub más de una vez cada 15 min. */
        private const val UPDATE_CHECK_MIN_INTERVAL_MS = 15 * 60_000L
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
        .stateIn(viewModelScope, SharingStarted.Eagerly, UpdateState.Idle)

    private val _pendingUpdate = MutableStateFlow<PendingUpdate?>(null)
    val pendingUpdate: StateFlow<PendingUpdate?> = _pendingUpdate.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _checking.asStateFlow()

    private var awaitingInstallPermission = false
    /** Evita abrir dos sesiones de PackageInstaller si se toca dos veces. */
    private var installSessionActive = false
    /** Serializa reintentar/cancelar para no abandonar la sesión recién creada. */
    private var sessionMaintenanceActive = false

    private var restoreJob: Job? = null
    private var downloadJob: Job? = null
    private var installJob: Job? = null

    init {
        restoreJob = viewModelScope.launch { restoreState() }
        observeInstallOutcomes()
    }

    /** Recupera el estado persistido y, si hay APK válido, lo verifica y avanza. */
    private suspend fun restoreState() {
        val storedState = withContext(Dispatchers.IO) {
            preferencesRepository.getUpdateState()
        }
        if (storedState == null) return

        // Un release ya rechazado no se restaura
        val rejected = withContext(Dispatchers.IO) {
            preferencesRepository.getRejectedUpdateTag()
        }
        if (storedState is UpdateState.Downloading ||
            storedState is UpdateState.Verifying ||
            storedState is UpdateState.ReadyToInstall ||
            storedState is UpdateState.Installing
        ) {
            if (UpdateChecker.isRejectedTag(storedState.versionName, rejected)) {
                cleanupResolved(showMessage = false)
                return
            }
        }

        val installed = withContext(Dispatchers.IO) {
            InstalledVersion.read(getApplication())
        }

        // Si la versión instalada ya es la misma o más nueva, limpiar estado
        if (storedState is UpdateState.Installed) {
            if (installed.versionCode >= storedState.versionCode) {
                cleanupResolved(showMessage = false)
                return
            }
        } else if (storedState is UpdateState.ReadyToInstall ||
            storedState is UpdateState.Installing ||
            storedState is UpdateState.Downloading ||
            storedState is UpdateState.Verifying
        ) {
            if (!UpdateChecker.isPendingNewer(storedState.versionName, storedState.versionCode, installed)) {
                cleanupResolved(showMessage = false)
                return
            }
        }

        _state.value = storedState

        // Restaurar el pending desde preferencias si el estado no es Idle/Installed/Error
        if (storedState !is UpdateState.Idle && storedState !is UpdateState.Installed && storedState !is UpdateState.Error) {
            val pending = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
            if (pending != null) _pendingUpdate.value = pending
        }

        // Si hay sesión de instalación pendiente, restaurar estado Installing
        if (storedState is UpdateState.Installing) {
            val context = getApplication<Application>()
            val activeSessions = withContext(Dispatchers.IO) {
                ApkDownloader.activeInstallSessionIds(context)
            }
            val storedSessionActive = storedState.sessionId != 0 && withContext(Dispatchers.IO) {
                ApkDownloader.isInstallSessionActive(context, storedState.sessionId)
            }
            if (activeSessions.isNotEmpty() || storedSessionActive) {
                installSessionActive = true
                _state.value = storedState.copy(waitingForUser = true)
                return
            }
            // Sesión ya no existe, limpiar y volver a ReadyToInstall
            if (storedState.sessionId != 0) {
                withContext(Dispatchers.IO) {
                    preferencesRepository.clearInstallSessionId()
                }
            }
        }

        // Si está en Downloading/Verifying/ReadyToInstall, verificar APK existente
        when (storedState) {
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.ReadyToInstall -> {
                val evaluated = evaluateDownloadedApk(storedState.versionName, storedState.versionCode)
                if (evaluated != null) {
                    _state.value = evaluated
                }
            }
            is UpdateState.Error -> {
                // Mantener error para que el usuario vea el mensaje
            }
            is UpdateState.Installed -> {
                // Mostrar aviso de actualizado
            }
            else -> {}
        }
    }

    /**
     * Consulta GitHub y deja el resultado en el único estado compartido.
     * En arranque (autoStartDownload) arranca la descarga dentro de la app.
     * Al terminar la descarga, verifica y lanza el instalador automáticamente.
     */
    fun checkForUpdate(
        force: Boolean = false,
        showFeedback: Boolean = true,
        autoStartDownload: Boolean = false,
    ) {
        viewModelScope.launch {
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

                val currentState = _state.value
                val sameRelease = when (currentState) {
                    is UpdateState.Downloading,
                    is UpdateState.Verifying,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Installing,
                    is UpdateState.Error -> currentState.versionName == pending.versionName &&
                        currentState.versionCode == pending.versionCode
                    else -> false
                }

                if (!sameRelease) {
                    downloadJob?.cancel()
                    downloadJob = null
                    discardInstallSession(context)
                    withContext(Dispatchers.IO) {
                        ApkDownloader.clearDownloadedApk(context)
                        preferencesRepository.clearUpdateInstallAttemptedCode()
                    }
                    _state.value = UpdateState.Idle
                }

                withContext(Dispatchers.IO) {
                    preferencesRepository.setPendingUpdate(pending)
                }
                _pendingUpdate.value = pending

                if (sameRelease) {
                    // Mismo release: si ya hay APK verificado, ir a Verifying/ReadyToInstall
                    val evaluated = evaluateDownloadedApk(pending.versionName, pending.versionCode)
                    if (evaluated != null) {
                        _state.value = evaluated
                    }
                }

                if (autoStartDownload || !sameRelease) {
                    startUpdate(pending)
                }
            } else {
                cleanupResolved(showMessage = showFeedback)
            }
        }
    }

    /** Inicia la descarga usando el pendiente guardado. */
    fun startUpdate() {
        val pending = _pendingUpdate.value ?: return
        startUpdate(pending)
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
    private fun startUpdate(pending: PendingUpdate) {
        val currentState = _state.value
        if (currentState is UpdateState.Downloading ||
            currentState is UpdateState.Verifying ||
            currentState is UpdateState.ReadyToInstall ||
            currentState is UpdateState.Installing
        ) {
            return
        }
        if (downloadJob?.isActive == true) return

        val context = getApplication<Application>()
        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.value = UpdateState.Downloading(
                versionName = pending.versionName,
                versionCode = pending.versionCode,
                downloadedBytes = 0L,
                totalBytes = pending.apkSize,
                installedVersionName = InstalledVersion.buildConfig().versionName,
                apkSize = pending.apkSize,
            )
            val speedMeter = DownloadSpeedMeter(windowMillis = 1000L, maxSamples = 10)
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
                    val etaSeconds = if (speedMeter.hasReliableEta() && bytesPerSecond > 0L && remainingBytes > 0L) {
                        remainingBytes / bytesPerSecond
                    } else {
                        null
                    }
                    _state.value = UpdateState.Downloading(
                        versionName = pending.versionName,
                        versionCode = pending.versionCode,
                        downloadedBytes = progress.bytes,
                        totalBytes = progress.totalBytes,
                        bytesPerSecond = bytesPerSecond,
                        etaSeconds = etaSeconds,
                        installedVersionName = InstalledVersion.buildConfig().versionName,
                        apkSize = pending.apkSize,
                    )
                }

                when (result) {
                    is DownloadResult.Success -> {
                        // Pasar a Verifying antes de instalar
                        _state.value = UpdateState.Verifying(
                            versionName = pending.versionName,
                            versionCode = pending.versionCode,
                            installedVersionName = InstalledVersion.buildConfig().versionName,
                            apkSize = pending.apkSize,
                        )
                        // Verificar y luego instalar automáticamente
                        verifyAndAutoInstall(pending)
                    }
                    is DownloadResult.Error -> {
                        if (result.releaseRejected) {
                            markReleaseRejected(pending.versionTag)
                        }
                        _state.value = UpdateState.Error(
                            versionName = pending.versionName,
                            versionCode = pending.versionCode,
                            message = result.message,
                            retryable = result.retryable,
                        )
                    }
                    DownloadResult.Cancelled -> {
                        // Volver a estado previo o Idle
                        val evaluated = evaluateDownloadedApk(pending.versionName, pending.versionCode)
                        _state.value = evaluated ?: UpdateState.Idle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error inesperado descargando la actualización")
                _state.value = UpdateState.Error(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    message = context.getString(R.string.update_descarga_error),
                    retryable = true,
                )
            }
        }
    }

    /** Verifica el APK descargado y lanza el instalador automáticamente. */
    private suspend fun verifyAndAutoInstall(pending: PendingUpdate) {
        val context = getApplication<Application>()
        val validation = withContext(Dispatchers.IO) {
            ApkDownloader.validateApk(
                context,
                ApkDownloader.apkFile(context),
                pending.apkSize,
                pending.apkSha256,
                pending.versionName,
                pending.versionCode,
            )
        }
        if (validation is ApkValidation.Failed) {
            withContext(Dispatchers.IO) {
                ApkDownloader.clearDownloadedApk(context)
                preferencesRepository.clearInstallSessionId()
                if (validation.releaseRejected) {
                    markReleaseRejected(pending.versionTag)
                }
            }
            _state.value = UpdateState.Error(
                versionName = pending.versionName,
                versionCode = pending.versionCode,
                message = validation.message,
                retryable = validation.retryable,
            )
            return
        }

        // APK válido: ir a ReadyToInstall y lanzar instalador
        _state.value = UpdateState.ReadyToInstall(
            versionName = pending.versionName,
            versionCode = pending.versionCode,
            installedVersionName = InstalledVersion.buildConfig().versionName,
            apkSize = pending.apkSize,
            installAttempted = false,
        )

        // Lanzar instalador automáticamente
        installApk()
    }

    fun retryDownload() {
        viewModelScope.launch {
            val currentState = _state.value
            val pending = when (currentState) {
                is UpdateState.Downloading,
                is UpdateState.Verifying,
                is UpdateState.ReadyToInstall,
                is UpdateState.Installing,
                is UpdateState.Error -> currentState
                else -> return@launch
            }
            if (currentState is UpdateState.Error && !currentState.retryable) {
                markReleaseRejected(pending.versionName)
                cleanupResolved(showMessage = false)
                return@launch
            }
            val context = getApplication<Application>()
            if (ApkDownloader.apkFile(context).exists()) {
                installApk()
                return@launch
            }
            val storedPending = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
            storedPending?.let { startUpdate(it) }
        }
    }

    /** Cancela y borra el .part: la tarjeta vuelve a "Disponible". */
    fun cancelDownload() {
        viewModelScope.launch {
            val currentState = _state.value
            if (currentState is UpdateState.Installing) return@launch
            downloadJob?.cancel()
            downloadJob = null
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) {
                ApkDownloader.clearPartial(context)
            }
            // Si hay pending guardado, volver a ReadyToInstall o Idle
            val storedPending = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
            if (storedPending != null) {
                val evaluated = evaluateDownloadedApk(storedPending.versionName, storedPending.versionCode)
                _state.value = evaluated ?: UpdateState.Idle
            } else {
                _state.value = UpdateState.Idle
            }
        }
    }

    /**
     * Verifica el APK y abre la sesión de PackageInstaller.
     * Se llama desde "Instalar" (fallback si auto-install falla), "Reintentar",
     * o automáticamente al terminar la descarga y verificación.
     */
    fun installApk() {
        if (installSessionActive || installJob?.isActive == true) return
        val currentState = _state.value
        val versionName = when (currentState) {
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.ReadyToInstall,
            is UpdateState.Installing,
            is UpdateState.Error -> currentState.versionName
            else -> return
        }
        val versionCode = when (currentState) {
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.ReadyToInstall,
            is UpdateState.Installing,
            is UpdateState.Error -> currentState.versionCode
            else -> return
        }

        val context = getApplication<Application>()
        val apk = ApkDownloader.apkFile(context)
        if (!apk.exists()) {
            val message = context.getString(R.string.update_archivo_faltante)
            _state.value = UpdateState.Error(
                versionName = versionName,
                versionCode = versionCode,
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
        _state.value = UpdateState.Installing(
            versionName = versionName,
            versionCode = versionCode,
            installedVersionName = InstalledVersion.buildConfig().versionName,
            waitingForUser = false,
            sessionId = 0,
        )
        installJob = viewModelScope.launch {
            restoreJob?.join()
            val pending = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
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
                    preferencesRepository.clearInstallSessionId()
                    if (validation.releaseRejected && pending != null) {
                        markReleaseRejected(pending.versionTag)
                    }
                }
                _state.value = UpdateState.Error(
                    versionName = versionName,
                    versionCode = versionCode,
                    message = validation.message,
                    retryable = validation.retryable,
                )
                _message.value = validation.message
                return@launch
            }

            if (pending != null) markInstallAttempted(pending.versionCode)

            when (val start = withContext(Dispatchers.IO) {
                ApkDownloader.installApk(context, apk)
            }) {
                is InstallStart.Started -> {
                    installSessionActive = true
                    withContext(Dispatchers.IO) {
                        preferencesRepository.setInstallSessionId(start.sessionId)
                    }
                    _state.value = UpdateState.Installing(
                        versionName = versionName,
                        versionCode = versionCode,
                        installedVersionName = InstalledVersion.buildConfig().versionName,
                        waitingForUser = true,
                        sessionId = start.sessionId,
                    )
                    _message.value = context.getString(R.string.update_confirmar_instalacion)
                }
                is InstallStart.Error -> {
                    installSessionActive = false
                    withContext(Dispatchers.IO) {
                        preferencesRepository.clearInstallSessionId()
                    }
                    _state.value = UpdateState.Error(
                        versionName = versionName,
                        versionCode = versionCode,
                        message = start.message,
                        retryable = start.retryable,
                    )
                }
            }
        }
    }

    /**
     * Reintenta una instalación cuya sesión quedó esperando confirmación.
     * Abandona la sesión vieja (y su notificación) y abre una nueva con la
     * app en primer plano.
     */
    fun retryInstall() {
        if (installJob?.isActive == true || sessionMaintenanceActive) return
        sessionMaintenanceActive = true
        val context = getApplication<Application>()
        installSessionActive = false
        viewModelScope.launch {
            try {
                discardInstallSession(context)
                val currentState = _state.value
                val versionName = when (currentState) {
                    is UpdateState.Installing,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Error -> currentState.versionName
                    else -> return@launch
                }
                val versionCode = when (currentState) {
                    is UpdateState.Installing,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Error -> currentState.versionCode
                    else -> return@launch
                }
                _state.value = UpdateState.ReadyToInstall(
                    versionName = versionName,
                    versionCode = versionCode,
                    installedVersionName = InstalledVersion.buildConfig().versionName,
                    installAttempted = true,
                )
                installApk()
            } finally {
                sessionMaintenanceActive = false
            }
        }
    }

    /** Cancela la sesión pendiente y deja el APK listo para instalar luego. */
    fun cancelInstall() {
        if (installJob?.isActive == true || sessionMaintenanceActive) return
        sessionMaintenanceActive = true
        val context = getApplication<Application>()
        installSessionActive = false
        viewModelScope.launch {
            try {
                discardInstallSession(context)
                val currentState = _state.value
                val versionName = when (currentState) {
                    is UpdateState.Installing,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Error -> currentState.versionName
                    else -> return@launch
                }
                val versionCode = when (currentState) {
                    is UpdateState.Installing,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Error -> currentState.versionCode
                    else -> return@launch
                }
                _state.value = UpdateState.ReadyToInstall(
                    versionName = versionName,
                    versionCode = versionCode,
                    installedVersionName = InstalledVersion.buildConfig().versionName,
                    installAttempted = true,
                )
            } finally {
                sessionMaintenanceActive = false
            }
        }
    }

    /** Abandona la sesión vieja y su confirmación pendiente. */
    private suspend fun discardInstallSession(context: Application) {
        installSessionActive = false
        val storedSessionId = withContext(Dispatchers.IO) {
            preferencesRepository.getInstallSessionId()
        }
        withContext(Dispatchers.IO) {
            ApkDownloader.abandonInstallSessions(context)
            if (storedSessionId != 0) {
                ApkDownloader.abandonInstallSession(context, storedSessionId)
            }
            preferencesRepository.clearInstallSessionId()
        }
    }

    /** Persiste el intento antes de commitear: un arranque en frío no lo repite. */
    private suspend fun markInstallAttempted(versionCode: Int) {
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
                        val context = getApplication<Application>()
                        discardInstallSession(context)
                        val currentState = _state.value
                        val versionName = when (currentState) {
                            is UpdateState.Installing,
                            is UpdateState.ReadyToInstall,
                            is UpdateState.Error -> currentState.versionName
                            else -> "desconocida"
                        }
                        val versionCode = when (currentState) {
                            is UpdateState.Installing,
                            is UpdateState.ReadyToInstall,
                            is UpdateState.Error -> currentState.versionCode
                            else -> 0
                        }
                        _state.value = UpdateState.Error(
                            versionName = versionName,
                            versionCode = versionCode,
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
        discardInstallSession(context)
        val currentState = _state.value
        val versionName = when (currentState) {
            is UpdateState.Installing -> currentState.versionName
            else -> BuildConfig.VERSION_NAME
        }
        val versionCode = when (currentState) {
            is UpdateState.Installing -> currentState.versionCode
            else -> BuildConfig.VERSION_CODE
        }
        downloadJob?.cancel()
        downloadJob = null
        withContext(Dispatchers.IO) {
            preferencesRepository.clearPendingUpdate()
            preferencesRepository.setRejectedUpdateTag("")
            ApkDownloader.clearDownloadedApk(context)
        }
        _state.value = UpdateState.Installed(
            versionName = versionName,
            versionCode = versionCode,
        )
    }

    fun onActivityResumed() {
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

        // Si hay estado Installed, mostrar aviso y limpiar
        val currentState = _state.value
        if (currentState is UpdateState.Installed) {
            val installed = InstalledVersion.read(getApplication())
            if (installed.versionCode >= currentState.versionCode) {
                _message.value = getApplication<Application>().getString(
                    R.string.update_installed_notice,
                    currentState.versionName
                )
                viewModelScope.launch { cleanupResolved(showMessage = false) }
            }
        }
    }

    fun suppressUntilTomorrow() {
        val currentState = _state.value
        if (currentState is UpdateState.Error && !currentState.retryable) {
            viewModelScope.launch { cleanupResolved(showMessage = false) }
            return
        }
        viewModelScope.launch {
            val manana = System.currentTimeMillis() + Constants.UPDATE_SUPPRESS_DAYS_MS
            withContext(Dispatchers.IO) {
                preferencesRepository.setUpdateSuppressedUntil(manana)
            }
            _state.value = UpdateState.Idle
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Compatibilidad con el nombre anterior del snackbar. */
    fun clearSnackbar() = clearMessage()

    /**
     * Evalúa un APK ya descargado. Solo devuelve ReadyToInstall si pasa tamaño,
     * SHA-256, release anunciado, versión, paquete y firma. Si no, borra el
     * archivo y reporta el motivo.
     */
    private suspend fun evaluateDownloadedApk(
        versionName: String,
        versionCode: Int,
    ): UpdateState? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val apk = ApkDownloader.apkFile(context)
        if (!apk.exists()) return@withContext null

        val info = ApkDownloader.readApkInfo(context, apk)
        if (info == null) {
            ApkDownloader.clearDownloadedApk(context)
            return@withContext null
        }

        val pending = preferencesRepository.getPendingUpdate()
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
            ApkValidation.Ok -> UpdateState.ReadyToInstall(
                versionName = versionName,
                versionCode = versionCode,
                installedVersionName = InstalledVersion.buildConfig().versionName,
                apkSize = apk.length(),
                installAttempted = false,
            )
            is ApkValidation.Failed -> {
                ApkDownloader.clearDownloadedApk(context)
                if (validation.releaseRejected && pending != null) {
                    markReleaseRejected(pending.versionTag)
                }
                UpdateState.Error(
                    versionName = versionName,
                    versionCode = versionCode,
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
        discardInstallSession(context)
        downloadJob?.cancel()
        downloadJob = null
        withContext(Dispatchers.IO) {
            preferencesRepository.clearPendingUpdate()
            ApkDownloader.clearDownloadedApk(context)
        }
        _pendingUpdate.value = null
        _state.value = UpdateState.Idle
        if (showMessage) _message.value = context.getString(R.string.update_no_disponible)
    }
}

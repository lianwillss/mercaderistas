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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Serializa la persistencia: nunca escribir un estado viejo sobre uno nuevo. */
    private val persistMutex = Mutex()
    private var stateGeneration = 0L
    /** El receiver ya resolvió la instalación: el job no debe pisar el resultado. */
    private var installResultReceived = false

    init {
        restoreJob = viewModelScope.launch { restoreState() }
        observeInstallOutcomes()
    }

    /**
     * Actualiza la tarjeta y persiste la transición en DataStore. El progreso
     * de descarga (cada %) no se persiste: sólo las fases, para no escribir
     * decenas de veces por segundo.
     */
    private fun setState(next: UpdateState, persist: Boolean = true) {
        _state.value = next
        if (!persist) return
        val generation = ++stateGeneration
        viewModelScope.launch {
            persistMutex.withLock {
                if (generation != stateGeneration) return@withLock
                persistState(next)
            }
        }
    }

    private suspend fun persistState(next: UpdateState) {
        if (next is UpdateState.Idle || next is UpdateState.Installed) {
            preferencesRepository.clearUpdateState()
        } else {
            preferencesRepository.setUpdateState(next)
        }
    }

    /**
     * Recupera el estado persistido. Si el JSON no existe (instalaciones de
     * v12.72 o un proceso que murió antes de persistir), reconstruye desde el
     * pendiente y la sesión de PackageInstaller: el arranque en frío nunca
     * debe borrar el APK ni crear otra sesión.
     */
    private suspend fun restoreState() {
        val storedState = withContext(Dispatchers.IO) {
            preferencesRepository.getUpdateState()
        }
        val pending = withContext(Dispatchers.IO) {
            preferencesRepository.getPendingUpdate()
        }
        val installed = withContext(Dispatchers.IO) {
            InstalledVersion.read(getApplication())
        }
        val rejected = withContext(Dispatchers.IO) {
            preferencesRepository.getRejectedUpdateTag()
        }
        val attemptedCode = withContext(Dispatchers.IO) {
            preferencesRepository.getUpdateInstallAttemptedCode()
        }

        if (storedState == null) {
            restorePending(pending, installed, rejected, attemptedCode)
            return
        }

        val rejectedTag = pending?.versionTag?.ifBlank { storedState.versionName }
            ?: storedState.versionName
        if (storedState is UpdateState.Downloading ||
            storedState is UpdateState.Verifying ||
            storedState is UpdateState.ReadyToInstall ||
            storedState is UpdateState.Installing
        ) {
            if (UpdateChecker.isRejectedTag(rejectedTag, rejected)) {
                cleanupResolved(showMessage = false)
                return
            }
        }

        // Si la versión instalada ya es la misma o más nueva, limpiar estado.
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

        pending?.let { _pendingUpdate.value = it }
        val context = getApplication<Application>()

        when (storedState) {
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.ReadyToInstall -> {
                // Un proceso murió durante la descarga/verificación: si el APK
                // está completo se revalida; si sólo queda el .part, el chequeo
                // de arranque lo reanuda.
                val evaluated = evaluateDownloadedApk(
                    storedState.versionName,
                    storedState.versionCode,
                    installAttempted = attemptedCode == storedState.versionCode,
                )
                setState(evaluated ?: UpdateState.Idle)
            }
            is UpdateState.Installing -> {
                val storedSessionActive = storedState.sessionId != 0 && withContext(Dispatchers.IO) {
                    ApkDownloader.isInstallSessionActive(context, storedState.sessionId)
                }
                val activeSessions = withContext(Dispatchers.IO) {
                    ApkDownloader.activeInstallSessionIds(context)
                }
                if (storedSessionActive || activeSessions.isNotEmpty()) {
                    installSessionActive = true
                    // Si el id persistido no está activo pero sí otra sesión del
                    // paquete (OEM que reasigna id), usar la que existe de verdad.
                    val sessionId = if (storedSessionActive) {
                        storedState.sessionId
                    } else {
                        activeSessions.firstOrNull() ?: storedState.sessionId
                    }
                    setState(storedState.copy(waitingForUser = true, sessionId = sessionId))
                    return
                }
                // La sesión ya no existe: el APK sigue listo para reintentar.
                withContext(Dispatchers.IO) {
                    preferencesRepository.clearInstallSessionId()
                }
                val evaluated = evaluateDownloadedApk(
                    storedState.versionName,
                    storedState.versionCode,
                    installAttempted = true,
                )
                setState(evaluated ?: UpdateState.Idle)
            }
            is UpdateState.Error -> _state.value = storedState
            is UpdateState.Installed -> _state.value = storedState
            is UpdateState.Idle -> setState(UpdateState.Idle)
        }
    }

    /** Reconstruye el estado cuando no hay JSON persistido pero sí un pendiente. */
    private suspend fun restorePending(
        pending: PendingUpdate?,
        installed: InstalledVersion,
        rejected: String,
        attemptedCode: Int,
    ) {
        if (pending == null) {
            val storedSessionId = withContext(Dispatchers.IO) {
                preferencesRepository.getInstallSessionId()
            }
            if (storedSessionId != 0) {
                withContext(Dispatchers.IO) { preferencesRepository.clearInstallSessionId() }
            }
            return
        }

        val rejectedTag = pending.versionTag.ifBlank { pending.versionName }
        if (UpdateChecker.isRejectedTag(rejectedTag, rejected) ||
            !UpdateChecker.isPendingNewer(pending.versionTag, pending.versionCode, installed)
        ) {
            cleanupResolved(showMessage = false)
            return
        }

        _pendingUpdate.value = pending
        val context = getApplication<Application>()
        val storedSessionId = withContext(Dispatchers.IO) {
            preferencesRepository.getInstallSessionId()
        }
        val sessionActive = storedSessionId != 0 && withContext(Dispatchers.IO) {
            ApkDownloader.isInstallSessionActive(context, storedSessionId)
        }
        val activeSessions = withContext(Dispatchers.IO) {
            ApkDownloader.activeInstallSessionIds(context)
        }
        if (sessionActive || activeSessions.isNotEmpty()) {
            installSessionActive = true
            val sessionId = if (sessionActive) {
                storedSessionId
            } else {
                activeSessions.firstOrNull() ?: storedSessionId
            }
            setState(
                UpdateState.Installing(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    installedVersionName = InstalledVersion.buildConfig().versionName,
                    waitingForUser = true,
                    sessionId = sessionId,
                ),
            )
            return
        }
        if (storedSessionId != 0) {
            withContext(Dispatchers.IO) { preferencesRepository.clearInstallSessionId() }
        }
        val evaluated = evaluateDownloadedApk(
            pending.versionName,
            pending.versionCode,
            installAttempted = attemptedCode == pending.versionCode,
        )
        if (evaluated != null) setState(evaluated)
    }

    /**
     * Consulta GitHub y deja el resultado en el único estado compartido.
     * Si hay versión nueva y la actualización no está pospuesta, la descarga
     * arranca sola; al terminar queda "Lista para instalar" y el instalador
     * se abre únicamente con la acción del usuario.
     *
     * [respectSuppression] es para el chequeo de arranque: si el usuario tocó
     * "Más tarde", el pendiente se guarda pero no se descarga ni se instala.
     */
    fun checkForUpdate(
        force: Boolean = false,
        showFeedback: Boolean = true,
        respectSuppression: Boolean = false,
    ) {
        viewModelScope.launch {
            restoreJob?.join()
            if (_checking.value) return@launch
            val suppressedUntil = withContext(Dispatchers.IO) {
                preferencesRepository.getUpdateSuppressedUntil()
            }
            val suppressed = System.currentTimeMillis() < suppressedUntil
            if (!force && suppressed) return@launch

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

            if (!info.available) {
                cleanupResolved(showMessage = showFeedback)
                return@launch
            }

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

            // Se compara contra el pendiente persistido (no contra el estado en
            // memoria) para no borrar el APK si el proceso murió en medio.
            val storedPending = withContext(Dispatchers.IO) {
                preferencesRepository.getPendingUpdate()
            }
            val releaseChanged = storedPending == null ||
                storedPending.versionName != pending.versionName ||
                storedPending.versionCode != pending.versionCode

            if (releaseChanged) {
                downloadJob?.cancel()
                downloadJob = null
                discardInstallSession(context)
                installResultReceived = false
                withContext(Dispatchers.IO) {
                    ApkDownloader.clearDownloadedApk(context)
                    preferencesRepository.clearUpdateInstallAttemptedCode()
                }
                setState(UpdateState.Idle)
            }

            withContext(Dispatchers.IO) {
                preferencesRepository.setPendingUpdate(pending)
            }
            _pendingUpdate.value = pending

            if (suppressed && respectSuppression) {
                // "Más tarde": el pendiente queda guardado, sin descargar ni
                // mostrar la tarjeta hasta que pase la posposición.
                return@launch
            }

            if (!releaseChanged && _state.value !is UpdateState.Installing) {
                val attempted = withContext(Dispatchers.IO) {
                    preferencesRepository.getUpdateInstallAttemptedCode()
                }
                val evaluated = evaluateDownloadedApk(
                    pending.versionName,
                    pending.versionCode,
                    installAttempted = attempted == pending.versionCode,
                )
                if (evaluated != null) setState(evaluated)
            }
            if (suppressed && !respectSuppression) {
                // Chequeo manual: el usuario pidió actualizarse, se levanta la posposición.
                withContext(Dispatchers.IO) {
                    preferencesRepository.setUpdateSuppressedUntil(0)
                }
            }
            startUpdate(pending)
        }
    }

    /** Inicia la descarga usando el pendiente guardado. */
    fun startUpdate() {
        val pending = _pendingUpdate.value ?: return
        viewModelScope.launch { startUpdate(pending) }
    }

    /** Consulta obligatoria al abrir: si hay versión nueva, descarga sola. */
    fun checkForUpdateOnLaunch() {
        checkForUpdate(force = true, showFeedback = false, respectSuppression = true)
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

    /**
     * Descarga el pendiente en el proceso de la app, actualizando el
     * porcentaje en la tarjeta. No usa WorkManager ni notificaciones.
     */
    private suspend fun startUpdate(pending: PendingUpdate) {
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

        // Chequeo de espacio ANTES de empezar la descarga: si se llenó el
        // almacenamiento entre el chequeo automático y la acción del usuario,
        // evitamos descargar para luego fallar con error genérico del instalador.
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
                return
            }
        }

        installSessionActive = false
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            setState(
                UpdateState.Downloading(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    downloadedBytes = 0L,
                    totalBytes = pending.apkSize,
                    installedVersionName = InstalledVersion.buildConfig().versionName,
                    apkSize = pending.apkSize,
                ),
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
                    // El % no se persiste: sólo las transiciones de fase.
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
                        setState(
                            UpdateState.Verifying(
                                versionName = pending.versionName,
                                versionCode = pending.versionCode,
                                installedVersionName = InstalledVersion.buildConfig().versionName,
                                apkSize = pending.apkSize,
                            ),
                        )
                        verifyDownloadedApk(pending)
                    }
                    is DownloadResult.Error -> {
                        if (result.releaseRejected) {
                            markReleaseRejected(pending.versionTag)
                        }
                        setState(
                            UpdateState.Error(
                                versionName = pending.versionName,
                                versionCode = pending.versionCode,
                                message = result.message,
                                retryable = result.retryable,
                            ),
                        )
                    }
                    DownloadResult.Cancelled -> {
                        // Volver a estado previo o Idle
                        val evaluated = evaluateDownloadedApk(pending.versionName, pending.versionCode)
                        setState(evaluated ?: UpdateState.Idle)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error inesperado descargando la actualización")
                setState(
                    UpdateState.Error(
                        versionName = pending.versionName,
                        versionCode = pending.versionCode,
                        message = context.getString(R.string.update_descarga_error),
                        retryable = true,
                    ),
                )
            }
        }
    }

    /**
     * Verifica el APK descargado y deja la tarjeta en "Lista para instalar".
     * La instalación no arranca sola: la pide el usuario con "Instalar".
     */
    private suspend fun verifyDownloadedApk(pending: PendingUpdate) {
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
            setState(
                UpdateState.Error(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    message = validation.message,
                    retryable = validation.retryable,
                ),
            )
            return
        }

        // APK válido: queda listo para que el usuario toque "Instalar".
        setState(
            UpdateState.ReadyToInstall(
                versionName = pending.versionName,
                versionCode = pending.versionCode,
                installedVersionName = InstalledVersion.buildConfig().versionName,
                apkSize = pending.apkSize,
                installAttempted = false,
            ),
        )
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
                setState(evaluated ?: UpdateState.Idle)
            } else {
                setState(UpdateState.Idle)
            }
        }
    }

    /**
     * Verifica el APK y abre la sesión de PackageInstaller. Se llama sólo
     * desde "Instalar" o "Reintentar", con la app en primer plano para que
     * Android muestre la confirmación nativa.
     */
    fun installApk() {
        if (installSessionActive || installJob?.isActive == true) return
        installResultReceived = false
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
            setState(
                UpdateState.Error(
                    versionName = versionName,
                    versionCode = versionCode,
                    message = message,
                    retryable = true,
                ),
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
        setState(
            UpdateState.Installing(
                versionName = versionName,
                versionCode = versionCode,
                installedVersionName = InstalledVersion.buildConfig().versionName,
                waitingForUser = false,
                sessionId = 0,
            ),
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
                setState(
                    UpdateState.Error(
                        versionName = versionName,
                        versionCode = versionCode,
                        message = validation.message,
                        retryable = validation.retryable,
                    ),
                )
                _message.value = validation.message
                return@launch
            }

            if (pending != null) markInstallAttempted(pending.versionCode)

            // Persistir la fase ANTES del commit: si Android mata el proceso
            // durante la instalación, el próximo arranque muestra
            // "Instalación pendiente" en vez de re-descargar todo.
            (_state.value as? UpdateState.Installing)?.let { persistState(it) }

            // Intento 1: instalación normal (requiere APK en disco, 2× espacio).
            var start = withContext(Dispatchers.IO) {
                ApkDownloader.installApk(context, apk)
            }
            // Fallback: si falla por espacio y tenemos URL/tamaño, streaming (1.2×).
            if (start is InstallStart.Error &&
                start.message.contains("espacio", ignoreCase = true) &&
                pending != null &&
                pending.apkUrl.isNotBlank() &&
                pending.apkSize > 0
            ) {
                Timber.i("Instalación normal falló por espacio, probando streaming…")
                start = withContext(Dispatchers.IO) {
                    ApkDownloader.installApkStreaming(
                        context = context,
                        apkUrl = pending.apkUrl,
                        expectedSize = pending.apkSize,
                        expectedSha256 = pending.apkSha256.orEmpty(),
                        expectedVersionName = pending.versionName.orEmpty(),
                        expectedVersionCode = pending.versionCode,
                    )
                }
            }

            when (start) {
                is InstallStart.Started -> {
                    if (installResultReceived) {
                        // El receiver ya informó el resultado: no pisar Error/Installed.
                        installSessionActive = false
                    } else {
                        installSessionActive = true
                        withContext(Dispatchers.IO) {
                            preferencesRepository.setInstallSessionId(start.sessionId)
                        }
                        setState(
                            UpdateState.Installing(
                                versionName = versionName,
                                versionCode = versionCode,
                                installedVersionName = InstalledVersion.buildConfig().versionName,
                                waitingForUser = true,
                                sessionId = start.sessionId,
                            ),
                        )
                        _message.value = context.getString(R.string.update_confirmar_instalacion)
                    }
                }
                is InstallStart.Error -> {
                    installSessionActive = false
                    withContext(Dispatchers.IO) {
                        preferencesRepository.clearInstallSessionId()
                    }
                    val isSpaceError = start.message.contains("espacio", ignoreCase = true)
                    _lastErrorWasSpace = isSpaceError
                    setState(
                        UpdateState.Error(
                            versionName = versionName,
                            versionCode = versionCode,
                            message = start.message,
                            retryable = start.retryable,
                        ),
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
                setState(
                    UpdateState.ReadyToInstall(
                        versionName = versionName,
                        versionCode = versionCode,
                        installedVersionName = InstalledVersion.buildConfig().versionName,
                        installAttempted = true,
                    ),
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
                setState(
                    UpdateState.ReadyToInstall(
                        versionName = versionName,
                        versionCode = versionCode,
                        installedVersionName = InstalledVersion.buildConfig().versionName,
                        installAttempted = true,
                    ),
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

    /** Persiste el intento antes de commitear para que la restauración lo muestre. */
    private suspend fun markInstallAttempted(versionCode: Int) {
        withContext(Dispatchers.IO) {
            preferencesRepository.setUpdateInstallAttemptedCode(versionCode)
        }
    }

    private fun observeInstallOutcomes() {
        viewModelScope.launch {
            UpdateInstallBus.events.collect { outcome ->
                val currentState = _state.value
                if (currentState !is UpdateState.Installing) {
                    // Sin sesión en curso (proceso reiniciado o ya resuelta): el
                    // resultado es atrasado y no debe resucitar un intento.
                    Timber.w("Se ignora un resultado de instalación sin sesión activa")
                    return@collect
                }
                if (outcome.sessionId != 0 && outcome.sessionId != currentState.sessionId) {
                    // Resultado atrasado de una sesión abandonada: no pisar el intento actual.
                    Timber.w(
                        "Se ignora el resultado de la sesión %d (actual %d)",
                        outcome.sessionId,
                        currentState.sessionId,
                    )
                    return@collect
                }
                installResultReceived = true
                when (outcome) {
                    is InstallOutcome.Success -> onInstalled()
                    is InstallOutcome.Failed -> {
                        val context = getApplication<Application>()
                        discardInstallSession(context)
                        setState(
                            UpdateState.Error(
                                versionName = currentState.versionName,
                                versionCode = currentState.versionCode,
                                message = outcome.message.ifBlank {
                                    context.getString(R.string.update_instalacion_error)
                                },
                                retryable = true,
                            ),
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
        setState(
            UpdateState.Installed(
                versionName = versionName,
                versionCode = versionCode,
            ),
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

        // Si volvimos de la confirmación del sistema y la sesión ya no existe
        // (MIUI la cerró al volver), no dejar la tarjeta colgada en "Instalando".
        if (currentState is UpdateState.Installing && currentState.waitingForUser) {
            viewModelScope.launch {
                val context = getApplication<Application>()
                val activeSessions = withContext(Dispatchers.IO) {
                    ApkDownloader.activeInstallSessionIds(context)
                }
                val storedSessionActive = currentState.sessionId != 0 && withContext(Dispatchers.IO) {
                    ApkDownloader.isInstallSessionActive(context, currentState.sessionId)
                }
                if (activeSessions.isEmpty() && !storedSessionActive) {
                    installSessionActive = false
                    setState(
                        UpdateState.ReadyToInstall(
                            versionName = currentState.versionName,
                            versionCode = currentState.versionCode,
                            installedVersionName = InstalledVersion.buildConfig().versionName,
                            installAttempted = true,
                        ),
                    )
                }
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
            setState(UpdateState.Idle)
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
        installAttempted: Boolean = false,
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
                installAttempted = installAttempted,
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
        _lastErrorWasSpace = false
        setState(UpdateState.Idle)
        if (showMessage) _message.value = context.getString(R.string.update_no_disponible)
    }

    /** True si el último error mostrado fue por espacio insuficiente. */
    private var _lastErrorWasSpace = false
    val lastErrorWasSpace: Boolean get() = _lastErrorWasSpace

    /** Abre el limpiador de almacenamiento (Samsung OneUI o genérico). */
    fun openStorageCleaner() {
        val context = getApplication<Application>()
        ApkDownloader.openStorageCleaner(context)
    }
}

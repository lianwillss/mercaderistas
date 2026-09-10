package com.rutamercaderistas.viewmodel

import androidx.compose.runtime.Stable
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutamercaderistas.Constants
import com.rutamercaderistas.R
import com.rutamercaderistas.data.network.downloadBytes
import com.rutamercaderistas.services.PromotionRepository
import timber.log.Timber
import com.rutamercaderistas.data.result.SyncResult
import com.rutamercaderistas.data.result.messageOrNull
import com.rutamercaderistas.models.BrandReference
import com.rutamercaderistas.models.DiaSemana
import com.rutamercaderistas.models.EntradaRuta
import com.rutamercaderistas.services.RuteroManager
import com.rutamercaderistas.services.RuteroRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val phase: String? = null) : SyncState
}

data class PlanillaChanges(
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val moved: List<MovedLocales> = emptyList(),
    val changedAddress: List<AddressChange> = emptyList(),
    val changedBrands: List<BrandChange> = emptyList(),
    val affectsToday: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && moved.isEmpty() &&
        changedAddress.isEmpty() && changedBrands.isEmpty() && affectsToday.isEmpty()
}

data class SyncPreview(
    val changes: PlanillaChanges,
    val routeCount: Int,
    val entryCount: Int,
)

data class MovedLocales(
    val local: String,
    val fromDays: String,
    val toDays: String,
    val fromRoute: String = "",
    val toRoute: String = "",
)

data class AddressChange(
    val local: String,
    val oldAddress: String,
    val newAddress: String,
)

data class BrandChange(
    val local: String,
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
)

/**
 * Diff puro y testeable entre planilla vieja y nueva.
 *
 * - added/removed: locales por clave código|local.
 * - moved: cambió el conjunto de días de visita (de los booleanos
 *   lunes..domingo, no del nombre de ruta) y/o la ruta.
 * - changedAddress/changedBrands: mismo local, cambió dirección o marcas.
 * - affectsToday: quitados o movidos-fuera que se visitaban hoy en la
 *   ruta activa.
 */
internal fun diffPlanilla(
    old: List<EntradaRuta>,
    new: List<EntradaRuta>,
    activeRoute: String? = null,
    today: DiaSemana? = null,
): PlanillaChanges {
    fun key(e: EntradaRuta) = e.codigo.uppercase() + "|" + e.local.uppercase()
    fun visitDays(entries: List<EntradaRuta>): Set<DiaSemana> = buildSet {
        if (entries.any { it.lunes }) add(DiaSemana.LUNES)
        if (entries.any { it.martes }) add(DiaSemana.MARTES)
        if (entries.any { it.miercoles }) add(DiaSemana.MIERCOLES)
        if (entries.any { it.jueves }) add(DiaSemana.JUEVES)
        if (entries.any { it.viernes }) add(DiaSemana.VIERNES)
        if (entries.any { it.sabado }) add(DiaSemana.SABADO)
        if (entries.any { it.domingo }) add(DiaSemana.DOMINGO)
    }
    fun routesOf(entries: List<EntradaRuta>): Set<String> =
        entries.map { it.rutero.trim() }.filter { it.isNotBlank() }.toSet()
    fun daysLabel(days: Set<DiaSemana>): String =
        DiaSemana.todos().filter { it in days }.joinToString(", ") { it.abreviacion }
    fun visitsToday(entries: List<EntradaRuta>): Boolean {
        if (today == null) return false
        val inRoute = activeRoute == null || entries.any { it.rutero.trim().equals(activeRoute.trim(), ignoreCase = true) }
        if (!inRoute) return false
        return entries.any { it.visitaEl(today) }
    }

    val oldMap = old.groupBy(::key)
    val newMap = new.groupBy(::key)

    val added = newMap.keys.subtract(oldMap.keys)
        .mapNotNull { newMap[it]?.firstOrNull()?.local }.filter { it.isNotBlank() }
    val removedKeys = oldMap.keys.subtract(newMap.keys)
    val removed = removedKeys
        .mapNotNull { oldMap[it]?.firstOrNull()?.local }.filter { it.isNotBlank() }

    val moved = mutableListOf<MovedLocales>()
    val changedAddress = mutableListOf<AddressChange>()
    val changedBrands = mutableListOf<BrandChange>()
    for (k in newMap.keys.intersect(oldMap.keys)) {
        val oldEntries = oldMap[k].orEmpty()
        val newEntries = newMap[k].orEmpty()
        val oldDays = visitDays(oldEntries)
        val newDays = visitDays(newEntries)
        val oldRoutes = routesOf(oldEntries)
        val newRoutes = routesOf(newEntries)
        val name = newEntries.firstOrNull()?.local ?: oldEntries.firstOrNull()?.local ?: ""
        if (oldDays != newDays || oldRoutes != newRoutes) {
            moved.add(
                MovedLocales(
                    local = name,
                    fromDays = daysLabel(oldDays),
                    toDays = daysLabel(newDays),
                    fromRoute = oldRoutes.sorted().joinToString(", "),
                    toRoute = newRoutes.sorted().joinToString(", "),
                )
            )
        }
        val oldAddr = oldEntries.firstOrNull()?.direccion?.trim().orEmpty()
        val newAddr = newEntries.firstOrNull()?.direccion?.trim().orEmpty()
        if (oldAddr.isNotBlank() && newAddr.isNotBlank() && !oldAddr.equals(newAddr, ignoreCase = true)) {
            changedAddress.add(AddressChange(local = name, oldAddress = oldAddr, newAddress = newAddr))
        }
        val oldBrands = oldEntries.map { it.cliente.trim() }.filter { it.isNotBlank() }.toSet()
        val newBrands = newEntries.map { it.cliente.trim() }.filter { it.isNotBlank() }.toSet()
        val addedBrands = (newBrands - oldBrands).sorted()
        val removedBrands = (oldBrands - newBrands).sorted()
        if (addedBrands.isNotEmpty() || removedBrands.isNotEmpty()) {
            changedBrands.add(BrandChange(local = name, added = addedBrands, removed = removedBrands))
        }
    }

    val affectsToday = mutableListOf<String>()
    if (today != null) {
        for (k in removedKeys) {
            val entries = oldMap[k].orEmpty()
            if (visitsToday(entries)) {
                entries.firstOrNull()?.local?.takeIf { it.isNotBlank() }?.let { affectsToday.add(it) }
            }
        }
        for (k in newMap.keys.intersect(oldMap.keys)) {
            val oldEntries = oldMap[k].orEmpty()
            val newEntries = newMap[k].orEmpty()
            if (visitsToday(oldEntries) && !visitsToday(newEntries)) {
                (newEntries.firstOrNull() ?: oldEntries.firstOrNull())?.local
                    ?.takeIf { it.isNotBlank() }?.let { affectsToday.add(it) }
            }
        }
    }

    return PlanillaChanges(
        added = added,
        removed = removed,
        moved = moved,
        changedAddress = changedAddress,
        changedBrands = changedBrands,
        affectsToday = affectsToday.distinct(),
    )
}

private fun EntradaRuta.visitaEl(dia: DiaSemana): Boolean = when (dia) {
    DiaSemana.LUNES -> lunes
    DiaSemana.MARTES -> martes
    DiaSemana.MIERCOLES -> miercoles
    DiaSemana.JUEVES -> jueves
    DiaSemana.VIERNES -> viernes
    DiaSemana.SABADO -> sabado
    DiaSemana.DOMINGO -> domingo
}

@Stable
data class SyncUiState(
    val isOnline: Boolean = false,
    val state: SyncState = SyncState.Idle,
    val snackbarMessage: String? = null,
    val syncError: String? = null,
    val syncChanges: PlanillaChanges? = null,
    val syncPreview: SyncPreview? = null,
) {
    val isSyncing: Boolean get() = state is SyncState.Syncing
    val syncPhase: String? get() = (state as? SyncState.Syncing)?.phase
}

@HiltViewModel
class SyncViewModel @Inject constructor(
    application: Application,
    private val ruteroManager: RuteroManager,
    private val repository: RuteroRepository,
    private val promotionRepository: PromotionRepository,
    private val brandReference: BrandReference,
    private val preferencesRepository: com.rutamercaderistas.data.preferences.PreferencesRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var syncJob: kotlinx.coroutines.Job? = null
    private var pendingPreview: PendingSyncPreview? = null

    private data class PendingSyncPreview(
        val preview: SyncPreview,
        val oldEntries: List<EntradaRuta>,
        val activeRoute: String?,
        val currentHash: String?,
    )

    private val connectivityManager =
        getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        checkConnectivity()
        registerNetworkMonitor()
        restoreStagedPreview()
    }

    private fun restoreStagedPreview() {
        viewModelScope.launch {
            val staged = ruteroManager.withSyncLock { ruteroManager.readStagedMasterExcel() }
                ?: return@launch
            val oldEntries = ruteroManager.loadAllEntries()
            val activeRoute = repository.getActiveRuteroName()
            val changes = if (oldEntries.isEmpty()) {
                PlanillaChanges()
            } else {
                computePlanillaChanges(oldEntries, staged.entries, activeRoute, activeRoute?.let { todayDia() })
            }
            val preview = SyncPreview(
                changes = changes,
                routeCount = staged.ruteros.size,
                entryCount = staged.entries.size,
            )
            if (changes.isEmpty) {
                ruteroManager.withSyncLock { ruteroManager.discardStagedMasterExcel() }
                return@launch
            }
            pendingPreview = PendingSyncPreview(preview, oldEntries, activeRoute, staged.contentHash)
            _state.update { it.copy(syncPreview = preview) }
        }
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
        val cb = networkCallback ?: return
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb,
            )
        } catch (_: Exception) {
            Timber.w("connectivityManager.registerNetworkCallback failed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        networkCallback?.let { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
                Timber.w("unregisterNetworkCallback failed in onCleared")
            }
        }
    }

    fun syncFromDrive() {
        syncJob?.cancel()
        _state.value = _state.value.copy(state = SyncState.Syncing(), syncError = null, syncChanges = null, syncPreview = null)
        syncJob = viewModelScope.launch {
            val result = performDriveSync(previewOnly = false)
            _state.value = _state.value.copy(state = SyncState.Idle)
            result.messageOrNull()?.let { msg ->
                _state.value = _state.value.copy(syncError = msg)
            }
        }
    }

    private suspend fun performDriveSync(previewOnly: Boolean): SyncResult<Boolean> {
        return ruteroManager.withSyncLock {
            withContext(Dispatchers.IO) {
            try {
                pendingPreview = null
                ruteroManager.discardStagedMasterExcel()
                val oldEntries = ruteroManager.loadAllEntries()
                val activeRoute = repository.getActiveRuteroName()
                // Incremental: si el ETag no cambió, no hay nada que hacer
                val lastETag = try { preferencesRepository.getLastSyncETag() } catch (_: Exception) { null }
                if (lastETag != null) {
                    val currentETag = try { com.rutamercaderistas.data.network.headForETag(Constants.DRIVE_EXPORT_URL) } catch (_: Exception) { null }
                    if (currentETag != null && currentETag == lastETag) {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        return@withContext SyncResult.NoChange
                    }
                }
                _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_descargando)))
                val cacheBustedUrl = "${Constants.DRIVE_EXPORT_URL}&ts=${System.currentTimeMillis()}"
                val bytes = downloadWithRetries(cacheBustedUrl)
                    ?: return@withContext SyncResult.Error(
                        if (!_state.value.isOnline) getApplication<Application>().getString(R.string.sync_sin_internet)
                        else getApplication<Application>().getString(R.string.sync_error_drive)
                    ).also {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                    }

                _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_procesando)))
                // Hash incremental: si el contenido no cambió, no re-procesar
                val lastHash = try { preferencesRepository.getLastSyncHash() } catch (_: Exception) { null }
                val currentHash = try {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    md.update(bytes)
                    md.digest().joinToString("") { "%02x".format(it) }
                } catch (_: Exception) { null }
                if (lastHash != null && currentHash != null && lastHash == currentHash) {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                    return@withContext SyncResult.NoChange
                }
                if (previewOnly) {
                    val staged = ruteroManager.stageMasterExcel(bytes).getOrElse {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        return@withContext SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
                    }
                    val changes = if (oldEntries.isEmpty()) {
                        PlanillaChanges()
                    } else {
                        computePlanillaChanges(oldEntries, staged.entries, activeRoute, todayDia())
                    }
                    val preview = SyncPreview(
                        changes = changes,
                        routeCount = staged.ruteros.size,
                        entryCount = staged.entries.size,
                    )
                    if (!changes.isEmpty) {
                        pendingPreview = PendingSyncPreview(preview, oldEntries, activeRoute, currentHash)
                        _state.value = _state.value.copy(state = SyncState.Idle, syncPreview = preview)
                        return@withContext SyncResult.NoChange
                    }
                    if (!ruteroManager.commitStagedMasterExcel()) {
                        _state.value = _state.value.copy(state = SyncState.Idle)
                        return@withContext SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
                    }
                } else if (!ruteroManager.saveMasterExcel(bytes)) {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                    return@withContext SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
                }

                completeCommittedSync(oldEntries, activeRoute, currentHash)
            } catch (e: Exception) {
                _state.value = _state.value.copy(state = SyncState.Idle)
                SyncResult.Error(e.message ?: getApplication<Application>().getString(R.string.sync_error_general))
            }
            }
        }
    }

    private suspend fun completeCommittedSync(
        oldEntries: List<EntradaRuta>,
        activeRoute: String?,
        currentHash: String?,
    ): SyncResult<Boolean> {
        _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_indexando)))
        if (!ruteroManager.createIndex()) {
            _state.value = _state.value.copy(state = SyncState.Idle)
            return SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
        }
        try { preferencesRepository.setLastSyncTime(System.currentTimeMillis()) } catch (_: Exception) {}

        // Only mark the source as committed after the file and Room index are valid.
        if (currentHash != null) {
            try { preferencesRepository.setLastSyncHash(currentHash) } catch (_: Exception) {}
            try {
                val etag = com.rutamercaderistas.data.network.headForETag(Constants.DRIVE_EXPORT_URL)
                if (etag != null) preferencesRepository.setLastSyncETag(etag)
            } catch (_: Exception) {}
        }

        _state.value = _state.value.copy(state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_actualizando_promos)))
        promotionRepository.refresh()
        val newEntries = ruteroManager.loadAllEntries()
        val changes = if (oldEntries.isEmpty()) {
            PlanillaChanges()
        } else {
            computePlanillaChanges(oldEntries, newEntries, activeRoute, todayDia())
        }
        if (!changes.isEmpty) {
            preferencesRepository.addSyncHistoryEntry(
                com.rutamercaderistas.data.preferences.SyncHistoryEntry(
                    timestamp = System.currentTimeMillis(),
                    summary = changesSummary(changes),
                )
            )
            if (changes.affectsToday.isNotEmpty()) {
                postTodayNotification(changes.affectsToday)
            }
        }
        _state.value = _state.value.copy(state = SyncState.Idle, syncChanges = changes)
        return SyncResult.Success(true)
    }

    fun syncFromDriveWithRouteReload(currentRoute: String?) {
        syncJob?.cancel()
        _state.value = _state.value.copy(state = SyncState.Syncing(), syncError = null, syncChanges = null, syncPreview = null)
        syncJob = viewModelScope.launch {
            val result = performDriveSync(previewOnly = currentRoute != null)
            when (result) {
                is SyncResult.Success -> {
                    val index = ruteroManager.loadIndex()
                    val routeToLoad = if (currentRoute != null && index.contains(currentRoute)) {
                        currentRoute
                    } else {
                        index.firstOrNull()
                    }
                    if (routeToLoad != null) {
                        val entries = ruteroManager.loadRoute(routeToLoad)
                        if (entries.isNotEmpty()) {
                            repository.clear()
                            repository.setEntries(entries, routeToLoad)
                        }
                    }
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        snackbarMessage = getApplication<Application>().getString(R.string.sync_datos_actualizados),
                    )
                }
                is SyncResult.Error -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        syncError = result.message,
                    )
                }
                is SyncResult.NoChange -> {
                    _state.value = _state.value.copy(state = SyncState.Idle)
                }
                is SyncResult.Offline -> {
                    _state.value = _state.value.copy(
                        state = SyncState.Idle,
                        syncError = getApplication<Application>().getString(R.string.sync_sin_conexion),
                    )
                }
            }
        }
    }

    fun confirmSyncPreview(currentRoute: String?) {
        val pending = pendingPreview ?: return
        syncJob?.cancel()
        _state.value = _state.value.copy(
            state = SyncState.Syncing(phase = getApplication<Application>().getString(R.string.sync_indexando)),
            syncPreview = null,
            syncError = null,
        )
        syncJob = viewModelScope.launch {
            val result = ruteroManager.withSyncLock {
                withContext(Dispatchers.IO) {
                    pendingPreview = null
                    if (!ruteroManager.commitStagedMasterExcel()) {
                        SyncResult.Error(getApplication<Application>().getString(R.string.sync_error_excel))
                    } else {
                        completeCommittedSync(pending.oldEntries, pending.activeRoute, pending.currentHash)
                    }
                }
            }
            when (result) {
                is SyncResult.Success -> reloadRouteAfterSync(currentRoute)
                is SyncResult.Error -> _state.value = _state.value.copy(state = SyncState.Idle, syncError = result.message)
                is SyncResult.NoChange -> _state.value = _state.value.copy(state = SyncState.Idle)
                is SyncResult.Offline -> _state.value = _state.value.copy(state = SyncState.Idle, syncError = getApplication<Application>().getString(R.string.sync_sin_conexion))
            }
        }
    }

    fun cancelSyncPreview() {
        pendingPreview = null
        _state.value = _state.value.copy(syncPreview = null)
        viewModelScope.launch {
            ruteroManager.withSyncLock { ruteroManager.discardStagedMasterExcel() }
        }
    }

    private suspend fun reloadRouteAfterSync(currentRoute: String?) {
        val index = ruteroManager.loadIndex()
        val routeToLoad = if (currentRoute != null && index.contains(currentRoute)) {
            currentRoute
        } else {
            index.firstOrNull()
        }
        if (routeToLoad != null) {
            val entries = ruteroManager.loadRoute(routeToLoad)
            if (entries.isNotEmpty()) {
                repository.clear()
                repository.setEntries(entries, routeToLoad)
            }
        }
        _state.value = _state.value.copy(
            state = SyncState.Idle,
            snackbarMessage = getApplication<Application>().getString(R.string.sync_datos_actualizados),
        )
    }

    fun clearSnackbar() {
        _state.value = _state.value.copy(snackbarMessage = null)
    }

    fun clearChanges() {
        _state.value = _state.value.copy(syncChanges = null)
    }

    private fun changesSummary(changes: PlanillaChanges): String {
        val app = getApplication<Application>()
        return buildList {
            if (changes.added.isNotEmpty()) add(app.getString(R.string.sync_cambios_agregados, changes.added.size))
            if (changes.removed.isNotEmpty()) add(app.getString(R.string.sync_cambios_eliminados, changes.removed.size))
            if (changes.moved.isNotEmpty()) add(app.getString(R.string.sync_cambios_movidos, changes.moved.size))
        }.joinToString(" · ")
    }

    private fun postTodayNotification(locales: List<String>) {
        val app = getApplication<Application>()
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                app, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = android.content.Intent(app, com.rutamercaderistas.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            app, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val names = locales.take(3).joinToString(", ") +
            if (locales.size > 3) " " + app.getString(R.string.notif_y_mas, locales.size - 3) else ""
        val notification = androidx.core.app.NotificationCompat.Builder(app, SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(app.getString(R.string.sync_ruta_hoy_titulo))
            .setContentText(names)
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(app.getString(R.string.sync_ruta_hoy_texto, locales.joinToString("\n• ", prefix = "• ")))
            )
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(app).notify(1002, notification)
    }

    companion object {
        const val SYNC_CHANNEL_ID = "sincronizacion"
    }

    internal fun computePlanillaChanges(
        old: List<EntradaRuta>,
        new: List<EntradaRuta>,
        activeRoute: String? = null,
        today: DiaSemana? = null,
    ): PlanillaChanges = diffPlanilla(old, new, activeRoute, today)

    private fun todayDia(): DiaSemana = when (java.time.LocalDate.now().dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> DiaSemana.LUNES
        java.time.DayOfWeek.TUESDAY -> DiaSemana.MARTES
        java.time.DayOfWeek.WEDNESDAY -> DiaSemana.MIERCOLES
        java.time.DayOfWeek.THURSDAY -> DiaSemana.JUEVES
        java.time.DayOfWeek.FRIDAY -> DiaSemana.VIERNES
        java.time.DayOfWeek.SATURDAY -> DiaSemana.SABADO
        else -> DiaSemana.DOMINGO
    }

    private suspend fun downloadWithRetries(url: String, retries: Int = Constants.MAX_RETRIES): ByteArray? {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < retries) {
                val result = downloadBytes(
                    url = url,
                    connectTimeout = Constants.CONNECT_TIMEOUT_MS,
                    readTimeout = Constants.READ_TIMEOUT_MS,
                )
                result.onSuccess { return@withContext it }
                result.onFailure { lastError = it as? Exception ?: Exception(it) }
                attempt++
                if (attempt >= retries) {
                    Timber.w(lastError, "downloadBytes agotó %d intentos", retries)
                    return@withContext null
                }
                kotlinx.coroutines.delay(Constants.RETRY_BACKOFF_MS * (1 shl attempt))
            }
            null
        }
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

package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockStatus
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.stock.StockSyncPhase
import com.rutamercaderistas.data.stock.StockSyncProgress
import com.rutamercaderistas.data.stock.StockSyncRequest
import com.rutamercaderistas.data.stock.StockSyncScheduler
import com.rutamercaderistas.data.stock.StockUpdater
import com.rutamercaderistas.data.stock.hayCriterio
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import com.rutamercaderistas.data.stock.remote.SessionExpiredException
import com.rutamercaderistas.worker.StockSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StockUiState(
    val catalog: StockCatalog = StockCatalog(),
    val result: StockResult = StockResult(),
    val status: StockStatus = StockStatus.Idle,
    val isDemo: Boolean = false,
    val hasSession: Boolean = true,
    val fechaDatos: String? = null,
    /** Instante ISO en que se generó el snapshot; alimenta "Actualizado hoy 07:21". */
    val generadoEn: String? = null,
    val actualizando: Boolean = false,
    val mensajeActualizacion: String? = null,
    val actualizacionError: Boolean = false,
    val progreso: StockSyncProgress? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class StockViewModel @Inject constructor(
    private val repo: StockRepository,
    private val cookies: KpiSessionStore,
    private val updater: StockUpdater,
    private val prefs: PreferencesRepository,
    private val scheduler: StockSyncScheduler,
    private val client: KpiOneClient,
) : ViewModel() {

    private val _filters = MutableStateFlow(StockFilters())
    val filters: StateFlow<StockFilters> = _filters.asStateFlow()

    private val _state = MutableStateFlow(StockUiState())
    val state: StateFlow<StockUiState> = _state.asStateFlow()

    private var trabajoActual: UUID? = null
    private var ultimoTerminado: UUID? = null

    init {
        // Recuerda el último local y marca: abrir Stock y seguir donde quedó.
        viewModelScope.launch {
            val local = prefs.getStockLocal()
            val marca = prefs.getStockMarca()
            val ruta = prefs.getStockRuta()
            if (local.isNotBlank() || marca.isNotBlank() || ruta.isNotBlank()) {
                _filters.update { it.copy(local = local, marca = marca, ruta = ruta) }
            }
        }
        viewModelScope.launch {
            _filters.debounce(600).collect { filtros ->
                prefs.setStockFilters(filtros.local, filtros.marca, filtros.ruta)
            }
        }

        // Al abrir, la app intenta entrar sola: el usuario no debería tocar nada.
        viewModelScope.launch {
            _state.update { it.copy(status = StockStatus.Loading) }
            val listo = runCatching { repo.ensureSession() }.getOrDefault(false)
            _state.update { it.copy(hasSession = listo) }
            if (listo) {
                cargarCatalogo()
                cargar(_filters.value)
            } else {
                _state.update { it.copy(status = StockStatus.SinSesion) }
            }
        }
        // Cambios posteriores: login manual de respaldo o sesión que expira.
        // El snapshot local no depende de la cookie; solo el modo en vivo.
        if (repo.requiereSesion) {
            viewModelScope.launch {
                cookies.hasSession.drop(1).collect { haySesion ->
                    _state.update { it.copy(hasSession = haySesion) }
                    if (!haySesion) {
                        _state.update {
                            it.copy(status = StockStatus.SinSesion, result = StockResult())
                        }
                    } else if (_state.value.status == StockStatus.SinSesion) {
                        cargarCatalogo()
                        cargar(_filters.value)
                    }
                }
            }
        }
        viewModelScope.launch {
            // El debounce evita una consulta por tecla al escribir el SKU.
            _filters.debounce(200).collect { filtros -> cargar(filtros) }
        }
        observarTrabajo()
    }

    /**
     * La barra no se alimenta de un contador local: se reengancha al WorkInfo
     * para que siga viva aunque se rote la pantalla o se apague la app.
     */
    private fun observarTrabajo() {
        viewModelScope.launch {
            scheduler.observar().collect { infos ->
                // La lista puede traer generaciones viejas del mismo nombre.
                // Priorizamos la que está activa; si no, la que arrancamos.
                val info = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING ||
                        it.state == WorkInfo.State.ENQUEUED ||
                        it.state == WorkInfo.State.BLOCKED
                } ?: infos.firstOrNull { it.id == trabajoActual }
                    ?: infos.lastOrNull()
                    ?: return@collect
                // Si el trabajo ya existía, KEEP conserva el id viejo: al verlo
                // corriendo adoptamos ese id para no confundirlo con un
                // SUCCEEDED viejo que quedó en la cola de WorkManager.
                if (info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
                ) {
                    trabajoActual = info.id
                }
                val esActual = info.id == trabajoActual
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED -> {
                        _state.update {
                            it.copy(
                                actualizando = true,
                                progreso = info.progress.aProgreso()
                                    ?: StockSyncProgress(StockSyncPhase.CONNECTING),
                                mensajeActualizacion = null,
                                actualizacionError = false,
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (!esActual || info.id == ultimoTerminado) return@collect
                        ultimoTerminado = info.id
                        val filas = info.outputData.getInt(StockSyncWorker.KEY_ROWS, 0)
                        val locales = info.outputData.getInt(StockSyncWorker.KEY_LOCALES, 0)
                        val fallidos = info.outputData.getInt(StockSyncWorker.KEY_FAILED, 0)
                        cargarCatalogo()
                        cargar(_filters.value)
                        val mensaje = buildString {
                            append("Stock actualizado: ").append(filas).append(" filas · ")
                            append(locales).append(" locales")
                            if (fallidos > 0) append(" · ").append(fallidos).append(" sin datos")
                        }
                        _state.update {
                            it.copy(
                                actualizando = false,
                                progreso = null,
                                mensajeActualizacion = mensaje,
                                actualizacionError = false,
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        if (!esActual || info.id == ultimoTerminado) return@collect
                        ultimoTerminado = info.id
                        val error = info.outputData.getString(StockSyncWorker.KEY_ERROR)
                            ?: "No se pudo actualizar el stock"
                        _state.update {
                            it.copy(
                                actualizando = false,
                                progreso = null,
                                mensajeActualizacion = error,
                                actualizacionError = true,
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        if (!esActual) return@collect
                        _state.update {
                            it.copy(
                                actualizando = false,
                                progreso = null,
                                mensajeActualizacion = "Actualización cancelada",
                                actualizacionError = false,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Genera el stock dentro de la app (KPI One directo). */
    fun iniciarActualizacion(force: Boolean = false) {
        if (_state.value.actualizando) return
        if (!client.puedeEntrar()) {
            _state.update {
                it.copy(
                    actualizando = false,
                    progreso = null,
                    mensajeActualizacion = "Falta configurar el acceso a KPI One en Ajustes.",
                    actualizacionError = true,
                )
            }
            return
        }
        val id = runCatching { scheduler.start(StockSyncRequest(force = force)) }.getOrNull()
        if (id == null) {
            _state.update {
                it.copy(
                    actualizando = false,
                    progreso = null,
                    mensajeActualizacion = "No se pudo iniciar la actualización",
                    actualizacionError = true,
                )
            }
            return
        }
        trabajoActual = id
        _state.update {
            it.copy(
                actualizando = true,
                progreso = StockSyncProgress(StockSyncPhase.CONNECTING),
                mensajeActualizacion = null,
                actualizacionError = false,
            )
        }
    }

    fun cancelarActualizacion() {
        scheduler.cancel()
    }

    private fun androidx.work.Data.aProgreso(): StockSyncProgress? {
        val fase = getString(StockSyncWorker.KEY_PHASE) ?: return null
        val phase = runCatching { StockSyncPhase.valueOf(fase) }.getOrNull() ?: return null
        return StockSyncProgress(
            phase = phase,
            done = getInt(StockSyncWorker.KEY_DONE, 0),
            total = getInt(StockSyncWorker.KEY_TOTAL, 0),
            rows = getInt(StockSyncWorker.KEY_ROWS, 0),
            current = getString(StockSyncWorker.KEY_CURRENT).orEmpty(),
            failed = getInt(StockSyncWorker.KEY_FAILED, 0),
        )
    }

    private suspend fun cargarCatalogo() {
        // catalog() recorre locales x items (miles de comparaciones): en Main
        // congelaba la app al cambiar a la pestaña Stock.
        runCatching { withContext(Dispatchers.Default) { repo.catalog() } }
            .onSuccess { catalogo ->
                _state.update {
                    it.copy(
                        catalog = catalogo,
                        isDemo = repo.isDemo,
                        fechaDatos = runCatching { repo.ultimaActualizacion() }.getOrNull(),
                        generadoEn = runCatching { repo.generadoEn() }.getOrNull(),
                    )
                }
            }
            .onFailure { error ->
                if (error is SessionExpiredException) {
                    _state.update { it.copy(status = StockStatus.SinSesion) }
                }
            }
    }

    private suspend fun cargar(filtros: StockFilters) {
        if (repo.requiereSesion && !_state.value.hasSession) {
            _state.update { it.copy(status = StockStatus.SinSesion) }
            return
        }
        // Sin criterio de búsqueda la pantalla queda en blanco: el stock se
        // muestra recién cuando el usuario elige local, marca o producto.
        if (!filtros.hayCriterio) {
            _state.update { it.copy(result = StockResult(), status = StockStatus.Idle) }
            return
        }
        _state.update { it.copy(status = StockStatus.Loading) }
        runCatching { withContext(Dispatchers.Default) { repo.search(filtros) } }
            .onSuccess { resultado ->
                _state.update {
                    it.copy(
                        result = resultado,
                        status = if (resultado.items.isEmpty()) StockStatus.Empty else StockStatus.Ready,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        status = if (error is SessionExpiredException) StockStatus.SinSesion
                        else StockStatus.Error,
                    )
                }
            }
    }

    fun onFilter(cambio: (StockFilters) -> StockFilters) {
        _filters.update(cambio)
    }

    fun reintentar() {
        viewModelScope.launch { cargar(_filters.value) }
    }

    /** Respaldo: aplica un stock.json publicado por URL (Drive). */
    fun actualizarStock(url: String) {
        if (_state.value.actualizando) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    actualizando = true,
                    progreso = null,
                    mensajeActualizacion = null,
                    actualizacionError = false,
                )
            }
            val resultado = updater.actualizar(url)
            val filas = resultado.getOrNull()
            if (filas != null) {
                cargarCatalogo()
                _state.update {
                    it.copy(
                        fechaDatos = runCatching { repo.ultimaActualizacion() }.getOrNull(),
                        generadoEn = runCatching { repo.generadoEn() }.getOrNull(),
                    )
                }
                cargar(_filters.value)
                _state.update {
                    it.copy(
                        actualizando = false,
                        mensajeActualizacion = "Stock actualizado: " + filas + " filas",
                        actualizacionError = false,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        actualizando = false,
                        mensajeActualizacion = resultado.exceptionOrNull()?.message
                            ?: "No se pudo actualizar",
                        actualizacionError = true,
                    )
                }
            }
        }
    }
}

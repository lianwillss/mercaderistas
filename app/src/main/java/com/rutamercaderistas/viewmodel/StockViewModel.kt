package com.rutamercaderistas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.WorkInfo
import com.rutamercaderistas.data.stock.CachedStock
import com.rutamercaderistas.data.stock.StockAlcance
import com.rutamercaderistas.data.stock.StockCache
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockScope
import com.rutamercaderistas.data.stock.StockScopeStore
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.StockStatus
import com.rutamercaderistas.data.preferences.PreferencesRepository
import com.rutamercaderistas.data.stock.StockSyncPhase
import com.rutamercaderistas.data.stock.StockSyncProgress
import com.rutamercaderistas.data.stock.StockSyncRequest
import com.rutamercaderistas.data.stock.StockSyncScheduler
import com.rutamercaderistas.data.stock.StockUpdater
import com.rutamercaderistas.data.stock.construirStockResult
import com.rutamercaderistas.data.stock.hayCriterio
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import com.rutamercaderistas.data.stock.remote.SessionExpiredException
import com.rutamercaderistas.worker.StockSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class StockUiState(
    val catalog: StockCatalog = StockCatalog(),
    val result: StockResult = StockResult(),
    /** Rutas visibles/descargables: "Mis rutas" o la RM completa. */
    val alcance: StockAlcance = StockAlcance(),
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
    /** Si la última consulta fue en vivo o contra el snapshot. */
    val fuente: StockSource = StockSource.SNAPSHOT,
    /** Instante ISO de la última consulta en vivo. */
    val consultadoEn: String? = null,
    /** Hay una consulta nueva en curso y se conserva la lista anterior. */
    val refrescando: Boolean = false,
    /** 0f..1f real de la carga actual; null cuando no hay barra que mostrar. */
    val progresoCarga: Float? = null,
    /** true durante el breve "Stock actualizado · 100%". */
    val progresoCompleto: Boolean = false,
    /** La consulta en vivo se cortó por el tope de páginas. */
    val truncado: Boolean = false,
    /** Aviso cuando se cayó al snapshot por falta de red o error. */
    val aviso: String? = null,
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
    private val stockCache: StockCache,
    private val scopeStore: StockScopeStore,
) : ViewModel() {

    private val _filters = MutableStateFlow(StockFilters())
    val filters: StateFlow<StockFilters> = _filters.asStateFlow()

    private val _state = MutableStateFlow(StockUiState())
    val state: StateFlow<StockUiState> = _state.asStateFlow()

    private var trabajoActual: UUID? = null
    private var ultimoTerminado: UUID? = null
    /** Última búsqueda resuelta contra la fuente; evita repetir la misma consulta. */
    private var ultimaConsulta: StockFilters? = null

    /** Filtros de las filas que están en pantalla (pueden venir de la caché). */
    private var filtrosMostrados: StockFilters? = null

    /** Consulta que está en vuelo para no dispararla dos veces. */
    private var consultaEnCurso: StockFilters? = null

    /** Cuándo se actualizaron por última vez las filas mostradas. */
    private var ultimaCargaEn = 0L

    /** Descarta respuestas viejas si se dispararon dos búsquedas a la vez. */
    private var generacionConsulta = 0

    /**
     * El catálogo y las búsquedas esperan a que el alcance esté fijado en el
     * repositorio: si no, la primera consulta saldría sin el límite de rutas.
     */
    private val alcanceResuelto = CompletableDeferred<StockAlcance>()

    init {
        // Primero el alcance: el resto de los flujos lo espera.
        viewModelScope.launch {
            val alcance = runCatching { scopeStore.cargar() }.getOrElse { StockAlcance() }
            aplicarAlcance(alcance)
            alcanceResuelto.complete(alcance)
            // Si el snapshot en disco no cubre el alcance elegido, se baja sólo
            // esa parte. Sin credenciales no se fuerza un error al abrir.
            val cubierto = runCatching { scopeStore.snapshotCubre(alcance) }
                .getOrDefault(true)
            if (!cubierto && client.puedeEntrar() && !_state.value.actualizando) {
                iniciarActualizacion()
            }
        }

        // Recuerda la última búsqueda y, si hay caché en disco, la pinta de
        // inmediato. Después el flujo de filtros pide la versión nueva.
        viewModelScope.launch {
            val local = prefs.getStockLocal()
            val marca = prefs.getStockMarca()
            val ruta = prefs.getStockRuta()
            val sku = prefs.getStockSku()
            val filtros = StockFilters(local = local, marca = marca, ruta = ruta, sku = sku)
            val cache = withContext(Dispatchers.IO) { stockCache.load() }
            val mostroCache = cache != null &&
                filtros.hayCriterio &&
                cache.coincide(filtros)
            if (mostroCache) {
                mostrarCache(cache, filtros)
            } else if (local.isNotBlank() || marca.isNotBlank() ||
                ruta.isNotBlank() || sku.isNotBlank()
            ) {
                _filters.update { it.copy(local = local, marca = marca, ruta = ruta, sku = sku) }
            }
        }
        viewModelScope.launch {
            _filters.debounce(600).collect { filtros ->
                prefs.setStockFilters(filtros.local, filtros.marca, filtros.ruta, filtros.sku)
            }
        }

        // Al abrir, la app intenta entrar sola: el usuario no debería tocar nada.
        viewModelScope.launch {
            alcanceResuelto.await()
            if (_state.value.result.items.isEmpty()) {
                _state.update { it.copy(status = StockStatus.Loading) }
            }
            val listo = runCatching { repo.ensureSession() }.getOrDefault(false)
            _state.update { it.copy(hasSession = listo) }
            if (listo) {
                cargarCatalogo()
            } else if (_state.value.result.items.isEmpty()) {
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
            alcanceResuelto.await()
            // El debounce evita una consulta por tecla al escribir el SKU y
            // collectLatest cancela la búsqueda anterior si el usuario sigue
            // escribiendo: con red no conviene encolar consultas viejas.
            _filters
                .debounce(250)
                .distinctUntilChanged()
                .collectLatest { filtros -> cargar(filtros) }
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
                        repo.invalidarCache()
                        cargarCatalogo()
                        // El snapshot cambió: hay que releer aunque la consulta
                        // anterior tenga las mismas filas en pantalla.
                        cargar(_filters.value, force = true)
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

    /** Fija en el repositorio el límite de rutas del alcance visible. */
    private fun aplicarAlcance(alcance: StockAlcance) {
        val rutas = if (alcance.limita) alcance.rutas else emptySet()
        runCatching { repo.fijarAlcance(rutas) }
        _state.update { it.copy(alcance = alcance) }
    }

    /**
     * Cambia entre "Mis rutas" y "RM completa". Reacomoda catálogo y búsqueda
     * al nuevo límite y, si el snapshot actual no cubre el alcance, dispara una
     * descarga acotada.
     */
    fun cambiarAlcance(scope: StockScope) {
        if (scope == _state.value.alcance.scope) return
        viewModelScope.launch {
            val alcance = runCatching { scopeStore.cambiar(scope) }
                .getOrElse { StockAlcance(scope) }
            aplicarAlcance(alcance)
            cargarCatalogo()
            cargar(_filters.value, force = true)
            val cubierto = runCatching { scopeStore.snapshotCubre(alcance) }
                .getOrDefault(true)
            if (!cubierto && client.puedeEntrar() && !_state.value.actualizando) {
                iniciarActualizacion()
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
        val alcance = _state.value.alcance
        val request = StockSyncRequest(
            rutas = if (alcance.limita) alcance.rutas else emptySet(),
            force = force,
        )
        val id = runCatching { scheduler.start(request) }.getOrNull()
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

    /** Pinta la última búsqueda guardada mientras se pide la versión nueva. */
    private fun mostrarCache(cache: CachedStock, base: StockFilters) {
        // La caché guarda también el estado/orden con el que se consultó; si no,
        // al volver se mostraría sólo Disponible aunque el usuario estuviera en
        // Negativos, o al revés.
        val filtros = base.copy(
            estado = cache.estado,
            cobertura = cache.cobertura,
            soloQuiebre = cache.soloQuiebre,
            soloNegativos = cache.soloNegativos,
            orden = cache.orden,
            modo = cache.modo,
        )
        val resultado = construirStockResult(
            base = cache.items,
            filtros = filtros,
            source = StockSource.CACHE,
            fetchedAt = cache.fetchedAt,
            total = cache.total,
            truncated = cache.truncated,
        ).copy(
            // Los conteos de las tarjetas salen de la búsqueda sin filtrar por
            // estado; el subconjunto cacheado no alcanza para recalcularlos.
            conteoQuiebre = cache.conteoQuiebre,
            conteoBajo = cache.conteoBajo,
            conteoDisponible = cache.conteoDisponible,
            conteoNegativos = cache.conteoNegativos,
            conteoCritico = cache.conteoCritico,
            conteoCoberturaBaja = cache.conteoCoberturaBaja,
            conteoSano = cache.conteoSano,
            conteoSobrestock = cache.conteoSobrestock,
        )
        filtrosMostrados = filtros
        ultimaCargaEn = cache.fetchedAt
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L
        _state.update {
            it.copy(
                result = resultado,
                status = if (resultado.items.isEmpty()) StockStatus.Empty else StockStatus.Ready,
                // Queda girando hasta que el flujo de filtros resuelve la
                // consulta que revalida la caché.
                refrescando = true,
                progresoCarga = 0f,
                progresoCompleto = false,
                fuente = StockSource.CACHE,
                consultadoEn = cache.fetchedAt,
                truncado = cache.truncated,
                aviso = null,
                fechaDatos = resultado.maxSnapshot,
                generadoEn = cache.fetchedAt,
            )
        }
        // Después de pintar: el flujo de filtros dispara la revalidación.
        _filters.update { filtros }
    }

    private suspend fun cargarCatalogo() {
        // catalog() recorre locales x items (miles de comparaciones): en Main
        // congelaba la app al cambiar a la pestaña Stock.
        runCatching { withContext(Dispatchers.Default) { repo.catalog() } }
            .onSuccess { catalogo ->
                // Si ya hay filas (caché o búsqueda en vivo), no pisan la fecha
                // del dato que el usuario está viendo con la del snapshot viejo.
                val hayFilas = _state.value.result.items.isNotEmpty()
                val fecha = if (hayFilas) null else {
                    runCatching { repo.ultimaActualizacion() }.getOrNull()
                }
                val generado = if (hayFilas) null else {
                    runCatching { repo.generadoEn() }.getOrNull()
                }
                _state.update {
                    it.copy(
                        catalog = catalogo,
                        isDemo = repo.isDemo,
                        fechaDatos = if (hayFilas) it.fechaDatos else fecha,
                        generadoEn = if (hayFilas) it.generadoEn else generado,
                    )
                }
            }
            .onFailure { error ->
                if (error is SessionExpiredException && _state.value.result.items.isEmpty()) {
                    _state.update { it.copy(status = StockStatus.SinSesion) }
                }
            }
    }

    private suspend fun cargar(filtros: StockFilters, force: Boolean = false) {
        val generacion = ++generacionConsulta
        if (repo.requiereSesion && !_state.value.hasSession) {
            _state.update { it.copy(status = StockStatus.SinSesion) }
            return
        }
        // Sin criterio de búsqueda la pantalla queda en blanco: el stock se
        // muestra recién cuando el usuario elige local, marca o producto.
        if (!filtros.hayCriterio) {
            ultimaConsulta = null
            filtrosMostrados = null
            consultaEnCurso = null
            _state.update {
                it.copy(
                    result = StockResult(),
                    status = StockStatus.Idle,
                    refrescando = false,
                    progresoCarga = null,
                    progresoCompleto = false,
                    fuente = StockSource.SNAPSHOT,
                    consultadoEn = null,
                    truncado = false,
                    aviso = null,
                )
            }
            return
        }
        // Evita pedir dos veces lo mismo: misma consulta en vuelo o ya resuelta
        // con filas en pantalla. El refresco de fondo usa force = true.
        if (!force) {
            if (consultaEnCurso == filtros) return
            if (filtros == ultimaConsulta && _state.value.result.items.isNotEmpty()) return
        }
        consultaEnCurso = filtros
        try {
        val previo = _state.value.result
        val conservar = mismaBase(filtrosMostrados, filtros) && previo.items.isNotEmpty()

        // Fase 1: pintar ya. Si la base es la misma, se conserva la lista; si
        // es nueva, se intenta el snapshot local antes de tocar la red.
        _state.update {
            it.copy(
                status = if (conservar) StockStatus.Ready else StockStatus.Loading,
                refrescando = true,
                progresoCarga = if (conservar) 0.25f else 0f,
                progresoCompleto = false,
            )
        }
        var local: StockResult? = null
        if (!conservar) {
            local = try {
                withContext(Dispatchers.Default) { repo.searchLocal(filtros) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (generacion != generacionConsulta) return
            if (local != null && local.items.isNotEmpty()) {
                filtrosMostrados = filtros
                _state.update {
                    it.copy(
                        result = local,
                        status = StockStatus.Ready,
                        refrescando = true,
                        progresoCarga = 0.25f,
                        fuente = local.source,
                        consultadoEn = local.fetchedAt,
                        truncado = local.truncated,
                        aviso = null,
                        fechaDatos = local.maxSnapshot ?: it.fechaDatos,
                    )
                }
            }
        }

        // Fase 2: la versión en vivo. Mientras corre, la barra superior y la
        // lista local siguen visibles.
        var errorEnVivo: String? = null
        val enVivo = try {
            withContext(Dispatchers.Default) {
                repo.searchLive(filtros, force) { cargados, total ->
                    if (generacion == generacionConsulta && total > 0) {
                        val fraccion = (cargados.toFloat() / total).coerceIn(0f, 1f)
                        _state.update {
                            it.copy(
                                // 25 % de la fase local + 70 % de la descarga.
                                progresoCarga = (0.25f + 0.70f * fraccion)
                                    .coerceIn(0.25f, 0.95f),
                                progresoCompleto = false,
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            errorEnVivo = "No se pudo actualizar; mostrando datos guardados"
            null
        }
        if (generacion != generacionConsulta) return

        val final = enVivo
            ?: local
            ?: previo.takeIf { conservar }
        if (final == null) {
            _state.update {
                it.copy(
                    status = if (errorEnVivo != null) StockStatus.Error else StockStatus.Empty,
                    refrescando = false,
                    progresoCarga = null,
                    progresoCompleto = false,
                )
            }
            return
        }

        val resultado = if (errorEnVivo != null && final.aviso == null) {
            final.copy(aviso = errorEnVivo)
        } else {
            final
        }
        // Sólo hay "100 %" si KPI One respondió de verdad. Si se cayó al
        // snapshot por red/error, se oculta la barra y manda el aviso.
        val actualizado = enVivo != null && enVivo.source == StockSource.LIVE
        ultimaConsulta = filtros
        filtrosMostrados = filtros
        ultimaCargaEn = System.currentTimeMillis()
        _state.update {
            it.copy(
                result = resultado,
                status = when {
                    resultado.items.isNotEmpty() -> StockStatus.Ready
                    errorEnVivo != null -> StockStatus.Error
                    else -> StockStatus.Empty
                },
                refrescando = false,
                progresoCarga = if (actualizado) 1f else null,
                progresoCompleto = actualizado,
                fuente = resultado.source,
                consultadoEn = resultado.fetchedAt,
                truncado = resultado.truncated,
                aviso = resultado.aviso,
                fechaDatos = resultado.maxSnapshot ?: it.fechaDatos,
                generadoEn = if (resultado.source == StockSource.LIVE) {
                    resultado.fetchedAt ?: it.generadoEn
                } else {
                    it.generadoEn
                },
            )
        }
        // Sólo se cachean búsquedas acotadas: una marca sola puede devolver
        // miles de filas y no vale la pena escribir el archivo.
        if (resultado.items.isNotEmpty() &&
            (filtros.local.isNotBlank() ||
                filtros.ruta.isNotBlank() ||
                filtros.sku.trim().length >= 3)
        ) {
            withContext(Dispatchers.IO) {
                runCatching { stockCache.save(filtros, resultado) }
            }
        }
        if (actualizado) ocultarProgreso(generacion)
        } finally {
            if (generacion == generacionConsulta && consultaEnCurso == filtros) {
                consultaEnCurso = null
            }
        }
    }

    /**
     * ¿La nueva búsqueda usa la misma base visible? El orden y el modo no
     * cambian las filas; el estado sí, así que se muestra el skeleton mientras
     * se aplica (evita dejar una lista de Disponible bajo la tarjeta Negativos).
     */
    private fun mismaBase(anterior: StockFilters?, nueva: StockFilters): Boolean =
        anterior != null &&
            anterior.local == nueva.local &&
            anterior.ruta == nueva.ruta &&
            anterior.marca == nueva.marca &&
            anterior.sku == nueva.sku &&
            anterior.estado == nueva.estado &&
            anterior.cobertura == nueva.cobertura &&
            anterior.soloQuiebre == nueva.soloQuiebre &&
            anterior.soloNegativos == nueva.soloNegativos

    fun onFilter(cambio: (StockFilters) -> StockFilters) {
        _filters.update(cambio)
    }

    fun reintentar() {
        viewModelScope.launch { cargar(_filters.value, force = true) }
    }

    /**
     * Al volver a la pestaña Stock, si pasó un minuto, pide la versión nueva
     * en segundo plano. La lista guardada sigue visible mientras refresca.
     */
    /** Deja el "100 %" visible un momento y vuelve a la píldora de frescura. */
    private fun ocultarProgreso(generacion: Int) {
        viewModelScope.launch {
            delay(1_100)
            if (generacion == generacionConsulta) {
                _state.update {
                    it.copy(progresoCarga = null, progresoCompleto = false)
                }
            }
        }
    }

    fun alVolver() {
        val filtros = _filters.value
        if (!filtros.hayCriterio || ultimaConsulta == null) return
        if (System.currentTimeMillis() - ultimaCargaEn < REFRESCO_AL_VOLVER_MS) return
        viewModelScope.launch { cargar(filtros, force = true) }
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
                repo.invalidarCache()
                cargarCatalogo()
                _state.update {
                    it.copy(
                        fechaDatos = runCatching { repo.ultimaActualizacion() }.getOrNull(),
                        generadoEn = runCatching { repo.generadoEn() }.getOrNull(),
                    )
                }
                cargar(_filters.value, force = true)
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

    private companion object {
        const val REFRESCO_AL_VOLVER_MS = 60_000L
    }
}
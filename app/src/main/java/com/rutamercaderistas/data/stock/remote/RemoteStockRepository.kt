package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.OsaRespuesta
import com.rutamercaderistas.data.stock.OsaRespuestaRemota
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.construirStockResult
import com.rutamercaderistas.data.stock.codigosDeRuta
import com.rutamercaderistas.data.stock.normalizarRuta
import com.rutamercaderistas.data.stock.normalizarStock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** La búsqueda no se puede resolver en vivo con los filtros actuales. */
class StockQueryException(message: String) : Exception(message)

/**
 * Búsqueda en vivo contra KPI One.
 *
 * La pantalla no consulta KPI One directamente: pasa por acá para compartir
 * login, reintentos y parseo con la descarga masiva. Las rutas se resuelven
 * contra Room (el rutero indexado), no contra el snapshot de stock.
 *
 * La consulta pagina hasta el total que informa KPI One (500 filas por
 * página como máximo) para no dejar filas afuera: los conteos de las tarjetas
 * se calculan sobre la base completa, no sobre las primeras páginas.
 *
 * La respuesta se cachea un minuto porque bajar una ruta entera son varias
 * páginas y cada tecla del buscador no debe golpear la API. Un refresco manual
 * pasa [force] = true y salta la caché.
 */
@Singleton
class RemoteStockRepository @Inject constructor(
    private val client: KpiOneClient,
    private val session: KpiSessionStore,
    private val routeDao: RouteEntryDao,
) : StockRepository {

    override val isDemo: Boolean = false

    override val requiereSesion: Boolean = true

    // ── Catálogo ───────────────────────────────────────────────────

    override suspend fun catalog(): StockCatalog = withContext(Dispatchers.IO) {
        val entradas = entradas()
        val kpi = runCatching { client.catalog() }.getOrNull()
        val rutasPorCodigo = entradas.groupBy { it.codigo.trim().uppercase() }
            .mapValues { (_, lista) ->
                lista.mapNotNull { it.rutero.trim().takeIf(String::isNotBlank) }
                    .distinct()
                    .sorted()
            }
        val locales = rutasPorCodigo.map { (codigo, rutas) ->
            StockLocal(
                code = codigo,
                name = kpi?.names?.get(codigo)
                    ?: entradas.firstOrNull { it.codigo.trim().equals(codigo, ignoreCase = true) }?.local
                    ?: codigo,
                rutas = rutas,
            )
        }.sortedBy { it.code }
        StockCatalog(
            locales = locales,
            marcas = kpi?.brands.orEmpty(),
            rutas = entradas.mapNotNull { it.rutero.trim().takeIf(String::isNotBlank) }
                .distinct()
                .sorted(),
        )
    }

    // ── Búsqueda ───────────────────────────────────────────────────

    override suspend fun search(filters: StockFilters): StockResult =
        search(filters, force = false)

    override suspend fun search(filters: StockFilters, force: Boolean): StockResult =
        search(filters, force, onProgress = { _, _ -> })

    suspend fun search(
        filters: StockFilters,
        force: Boolean,
        onProgress: (loaded: Int, total: Int) -> Unit,
    ): StockResult = withContext(Dispatchers.IO) {
            val clave = cacheKey(filters)
            if (!force) {
                leerCache(clave)?.let { cache ->
                    // La caché guarda las filas crudas de la API: la marca se
                    // filtra acá y no en la descarga, así cambiarla no obliga
                    // a paginar de nuevo la ruta entera.
                    return@withContext construirResultado(
                        items = cache.items,
                        codigos = cache.codigos,
                        filtros = filters,
                        source = StockSource.LIVE,
                        fetchedAt = Instant.ofEpochMilli(cache.at).toString(),
                        total = cache.total,
                        truncated = cache.truncated,
                        osaConsultada = cache.osaConsultada,
                    )
                }
            }

            val entradas = entradas()
            val todosLosCodigos = resolverCodigos(entradas, filters)
            val codigos = todosLosCodigos.take(MAX_LOCALES)
            val truncadoPorLocales = todosLosCodigos.size > codigos.size
            if ((filters.local.isNotBlank() || filters.ruta.isNotBlank()) && codigos.isEmpty()) {
                throw StockQueryException("Sin locales para la búsqueda")
            }
            if (codigos.isEmpty() && normalizarStock(filters.sku).length < 3) {
                throw StockQueryException("La búsqueda en vivo necesita local, ruta o SKU")
            }

            val items = mutableListOf<StockItem>()
            var total = 0
            var offset = 0
            var paginas = 0
            var cortadaPorTope = false
            while (true) {
                // collectLatest del ViewModel cancela la búsqueda anterior al
                // cambiar de filtro; sin esto la paginación seguiría bajando
                // páginas que ya nadie va a mirar.
                ensureActive()
                val pagina = client.fetchInventory(
                    storeCodes = codigos,
                    // La marca se filtra localmente para que el texto parcial
                    // ("caso") siga funcionando igual que en el snapshot.
                    brand = null,
                    query = filters.sku.trim(),
                    offset = offset,
                    limit = KpiOneClient.LIMIT,
                )
                // El total puede crecer si el inventario cambia durante la
                // descarga: quedarse con el de la primera página cortaría
                // filas nuevas sin avisar.
                total = maxOf(total, pagina.total)
                items += pagina.items
                paginas++
                if (total > 0) onProgress(items.size.coerceAtMost(total), total)
                if (pagina.items.isEmpty()) break
                // Se avanza por filas recibidas y no por el límite pedido:
                // KPI One recorta "limit" a 500, así el offset sigue alineado.
                offset += pagina.items.size
                if (total > 0 && offset >= total) break
                if (paginas >= MAX_PAGINAS) {
                    // Quedan filas sin bajar: tope de seguridad.
                    cortadaPorTope = true
                    break
                }
            }
            if (total <= 0) total = items.size

            val truncada = truncadoPorLocales || cortadaPorTope || total > items.size
            val ahora = System.currentTimeMillis()
            // Se guardan las filas crudas: los filtros de local, marca y SKU se
            // aplican al leer (ver [construirResultado]).
            guardarCache(
                clave,
                CacheEntry(
                    items = items,
                    codigos = codigos,
                    total = total,
                    truncated = truncada,
                    at = ahora,
                ),
            )

            construirResultado(
                items = items,
                codigos = codigos,
                filtros = filters,
                source = StockSource.LIVE,
                fetchedAt = Instant.ofEpochMilli(ahora).toString(),
                total = total,
                truncated = truncada,
            )
        }

    // ── Cruce OSA ──────────────────────────────────────────────────

    /**
     * Completa los negativos con la OSA de la última visita.
     *
     * Se hace después de mostrar el stock (segunda fase): la red de KPI One
     * tarda ~1-2 s por visita y no debe frenar la búsqueda. La respuesta se
     * cachea por local+marca, así cambiar de filtro u ordenar no vuelve a
     * pedirla. Si algún par falla o se agota el tiempo, se devuelven los
     * cruces que sí llegaron y NO se marca "sin OSA" para el resto.
     */
    override suspend fun completarOsa(
        filtros: StockFilters,
        resultado: StockResult,
    ): StockResult? {
        if (resultado.source != StockSource.LIVE) return null
        // La entrada cruda de la búsqueda: permite reconstruir la lista
        // completa (sin el filtro actual) y mantener OSA al cambiar filtros.
        val clave = cacheKey(filtros)
        val entrada = leerCache(clave) ?: return null
        val negativos = entrada.items.filter {
            it.stockUnits < 0.0 && it.storeCode.isNotBlank() && it.brand.isNotBlank()
        }
        if (negativos.isEmpty()) return null
        // Ya cruzado y completo: no volver a pedir OSA por el mismo corte.
        if (entrada.osaConsultada && negativos.none { it.osa == null }) return null

        val pares = paresOsa(negativos)
        if (pares.isEmpty()) return null
        val (porPar, completo) = resolverOsa(pares)

        // Se enriquecen todas las filas crudas: la próxima reconstrucción
        // (otro estado/orden) ya sale con la OSA puesta.
        val enriquecidos = entrada.items.map { item ->
            if (item.stockUnits >= 0.0 || item.osa != null) {
                item
            } else {
                item.copy(osa = osaDe(item, porPar))
            }
        }
        val consultada = completo &&
            enriquecidos.none { it.stockUnits < 0.0 && it.osa == null }
        synchronized(cacheLock) {
            val actual = cache[clave]
            if (actual != null && actual.at == entrada.at) {
                cache[clave] = actual.copy(
                    items = enriquecidos,
                    osaConsultada = consultada,
                )
            }
        }
        return construirResultado(
            items = enriquecidos,
            codigos = entrada.codigos,
            filtros = filtros,
            source = StockSource.LIVE,
            fetchedAt = Instant.ofEpochMilli(entrada.at).toString(),
            total = entrada.total,
            truncated = entrada.truncated,
            osaConsultada = consultada,
        )
    }

    /** Pares local+marca con negativos, de los más repetidos a los menos. */
    private fun paresOsa(negativos: List<StockItem>): List<Pair<String, String>> {
        val conteo = LinkedHashMap<Pair<String, String>, Int>()
        for (item in negativos) {
            val par = item.storeCode.trim().uppercase() to item.brand.trim()
            conteo[par] = (conteo[par] ?: 0) + 1
        }
        return conteo.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }
                .thenBy { it.key.first + "|" + it.key.second })
            .map { it.key }
            .take(MAX_PARES_OSA)
    }

    /**
     * Trae las respuestas de cada par, primero desde la caché corta y después
     * de KPI One (con concurrencia y presupuesto acotados).
     */
    private suspend fun resolverOsa(
        pares: List<Pair<String, String>>,
    ): Pair<Map<Pair<String, String>, List<OsaRespuestaRemota>>, Boolean> {
        val salida = ConcurrentHashMap<Pair<String, String>, List<OsaRespuestaRemota>>()
        val pendientes = mutableListOf<Pair<String, String>>()
        val ahora = System.currentTimeMillis()
        synchronized(osaCacheLock) {
            for (par in pares) {
                val cache = osaCache[par]
                if (cache != null && ahora - cache.vistaEn <= OSA_CACHE_TTL_MS) {
                    salida[par] = cache.respuestas
                } else {
                    pendientes += par
                }
            }
        }
        if (pendientes.isEmpty()) return salida to true

        val fallo = AtomicBoolean(false)
        val desde = LocalDate.now().minusDays(OSA_VENTANA_DIAS)
        val hasta = LocalDate.now()
        val completo = withTimeoutOrNull(OSA_PRESUPUESTO_MS) {
            coroutineScope {
                val permiso = Semaphore(OSA_CONCURRENCIA)
                pendientes.map { par ->
                    async(Dispatchers.IO) {
                        permiso.withPermit {
                            try {
                                val respuestas = client.fetchOsaReciente(
                                    storeCode = par.first,
                                    brand = par.second,
                                    desde = desde,
                                    hasta = hasta,
                                )
                                salida[par] = respuestas
                                guardarOsaCache(par, respuestas)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Un par caído no invalida los demás.
                                fallo.set(true)
                            }
                        }
                    }
                }.awaitAll()
                true
            }
        } != null
        val todoResuelto = salida.keys.containsAll(pares)
        return salida to (completo && todoResuelto && !fallo.get())
    }

    private fun guardarOsaCache(
        par: Pair<String, String>,
        respuestas: List<OsaRespuestaRemota>,
    ) = synchronized(osaCacheLock) {
        if (osaCache.size >= MAX_OSA_CACHE && par !in osaCache) {
            osaCache.entries.firstOrNull()?.let { osaCache.remove(it.key) }
        }
        osaCache[par] = OsaParCache(
            respuestas = respuestas,
            vistaEn = System.currentTimeMillis(),
        )
    }

    private fun osaDe(
        item: StockItem,
        porPar: Map<Pair<String, String>, List<OsaRespuestaRemota>>,
    ): OsaRespuesta? {
        val lista = porPar[item.storeCode.trim().uppercase() to item.brand.trim()]
            ?: return null
        val id = item.productId
        val encontrada = (if (id != null) {
            lista.firstOrNull { it.productId == id }
        } else {
            null
        }) ?: lista.firstOrNull {
            normalizarStock(it.sku) == normalizarStock(item.sku)
        } ?: return null
        return encontrada.aRespuesta()
    }

    override fun invalidarCache() {
        synchronized(cacheLock) { cache.clear() }
        synchronized(osaCacheLock) { osaCache.clear() }
        routeIndex = null
    }

    // ── Sesión ─────────────────────────────────────────────────────

    override suspend fun hasSession(): Boolean = client.hasSession()

    override suspend fun ensureSession(): Boolean = client.ensureSession()

    override suspend fun conectar(email: String, password: String): Boolean {
        val ok = client.login(email, password)
        if (ok) {
            session.guardarCredenciales(email.trim(), password)
            invalidarCache()
        }
        return ok
    }

    override suspend fun desconectar() {
        client.logout()
        session.borrarTodo()
        client.clearCatalogCache()
        invalidarCache()
    }

    // ── Internos ───────────────────────────────────────────────────

    /**
     * La marca no entra en la clave: no se manda a KPI One (se filtra en
     * memoria), así que cambiar sólo la marca reutiliza la descarga.
     */
    private fun cacheKey(filters: StockFilters): String =
        normalizarStock(filters.local.substringBefore("·")) + "|" +
            normalizarRuta(filters.ruta) + "|" +
            normalizarStock(filters.sku)

    /**
     * Aplica local, marca y SKU sobre las filas crudas y arma el [StockResult].
     * Los conteos de las tarjetas salen de esta base ya completa (o de lo que
     * se alcanzó a bajar si [truncated] es true).
     */
    private fun construirResultado(
        items: List<StockItem>,
        codigos: List<String>,
        filtros: StockFilters,
        source: StockSource,
        fetchedAt: String,
        total: Int,
        truncated: Boolean,
        osaConsultada: Boolean = false,
    ): StockResult {
        val permitidos = codigos.toSet()
        val marca = normalizarStock(filtros.marca)
        val sku = normalizarStock(filtros.sku)
        val base = items.filter { item ->
            (permitidos.isEmpty() || item.storeCode in permitidos) &&
                (marca.isEmpty() || normalizarStock(item.brand).contains(marca)) &&
                (sku.isEmpty() ||
                    normalizarStock(item.sku).contains(sku) ||
                    normalizarStock(item.product).contains(sku))
        }
        return construirStockResult(
            base = base,
            filtros = filtros,
            source = source,
            fetchedAt = fetchedAt,
            total = total,
            truncated = truncated,
            osaConsultada = osaConsultada,
        )
    }

    private fun leerCache(clave: String): CacheEntry? = synchronized(cacheLock) {
        val entrada = cache[clave] ?: return@synchronized null
        if (System.currentTimeMillis() - entrada.at > CACHE_TTL_MS) {
            cache.remove(clave)
            null
        } else {
            entrada
        }
    }

    private fun guardarCache(clave: String, entrada: CacheEntry) = synchronized(cacheLock) {
        if (cache.size >= MAX_CACHE) {
            cache.entries.firstOrNull()?.let { cache.remove(it.key) }
        }
        cache[clave] = entrada
    }

    private suspend fun entradas(): List<RouteEntryEntity> {
        val ahora = System.currentTimeMillis()
        val actual = routeIndex
        if (actual != null && ahora - actual.at < RUTAS_TTL_MS) return actual.entries
        val nuevas = routeDao.getAllEntries()
        routeIndex = RouteIndex(nuevas, ahora)
        return nuevas
    }

    private fun resolverCodigos(
        entradas: List<RouteEntryEntity>,
        filters: StockFilters,
    ): List<String> {
        val local = filters.local.trim()
        if (local.isNotBlank()) {
            val texto = normalizarStock(local.substringBefore("·"))
            val completo = normalizarStock(local)
            val porCodigo = entradas.filter {
                val codigo = normalizarStock(it.codigo)
                codigo == texto || (texto.length >= 2 && codigo.startsWith(texto))
            }
            val elegidos = porCodigo.ifEmpty {
                entradas.filter { normalizarStock(it.local).contains(completo) }
            }
            return elegidos.map { it.codigo.trim().uppercase() }
                .distinct()
        }

        return codigosDeRuta(entradas, filters.ruta)
    }

    private data class CacheEntry(
        /** Filas crudas de KPI One: los filtros se aplican al leer. */
        val items: List<StockItem>,
        /** Locales pedidos a la API; acotan las filas al reconstruir. */
        val codigos: List<String>,
        val total: Int,
        val truncated: Boolean,
        val at: Long,
        /** El cruce OSA ya terminó para esta búsqueda. */
        val osaConsultada: Boolean = false,
    )

    private data class RouteIndex(
        val entries: List<RouteEntryEntity>,
        val at: Long,
    )

    /** Respuestas OSA por local+marca; la última visita tarda en cambiar. */
    private data class OsaParCache(
        val respuestas: List<OsaRespuestaRemota>,
        val vistaEn: Long,
    )

    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheLock = Any()

    private val osaCache = LinkedHashMap<Pair<String, String>, OsaParCache>()
    private val osaCacheLock = Any()

    @Volatile
    private var routeIndex: RouteIndex? = null

    private companion object {
        /**
         * Tope de locales por consulta. La ruta más grande del rutero real
         * (PITUTO) tiene 124 locales: con 60 se perdían más de 2.000 filas.
         * 150 deja margen sin que la URL crezca de más.
         */
        const val MAX_LOCALES = 150
        /**
         * Tope de filas por búsqueda en vivo. Una ruta de 40 locales tipo
         * Jumbo (~250 SKU cada uno) ronda las 10.000 filas; si se supera, la
         * cabecera avisa "Resultados parciales". Antes eran 3 páginas: 1.500.
         */
        const val MAX_FILAS = 10_000
        /** KPI One recorta "limit" a 500 aunque se le pida más. */
        const val MAX_PAGINAS = MAX_FILAS / KpiOneClient.LIMIT
        const val MAX_CACHE = 12
        /** Bajar una ruta entera son varias páginas: la caché evita repetirlo. */
        const val CACHE_TTL_MS = 60_000L
        const val RUTAS_TTL_MS = 60_000L

        /** Tope de pares local+marca por cruce; el resto queda sin cruzar. */
        const val MAX_PARES_OSA = 40
        /** Ventana para buscar la última visita del par local+marca. */
        const val OSA_VENTANA_DIAS = 60L
        const val OSA_CONCURRENCIA = 6
        /** Presupuesto total del cruce: nunca frena la pantalla más que esto. */
        const val OSA_PRESUPUESTO_MS = 12_000L
        const val OSA_CACHE_TTL_MS = 10 * 60_000L
        const val MAX_OSA_CACHE = 64
    }
}

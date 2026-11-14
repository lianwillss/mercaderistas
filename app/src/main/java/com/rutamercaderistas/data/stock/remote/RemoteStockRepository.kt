package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockSource
import com.rutamercaderistas.data.stock.construirStockResult
import com.rutamercaderistas.data.stock.normalizarRuta
import com.rutamercaderistas.data.stock.normalizarStock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
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

    /**
     * Rutas del alcance actual (normalizadas). Vacío = sin límite. Al cambiar,
     * se descarta el índice filtrado y la caché de consultas: una respuesta
     * armada con otro alcance no sirve.
     */
    @Volatile
    private var alcanceRutas: Set<String> = emptySet()

    override fun fijarAlcance(rutas: Set<String>) {
        val nuevas = rutas
            .map { normalizarRuta(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (nuevas == alcanceRutas) return
        alcanceRutas = nuevas
        invalidarCache()
    }

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
                    )
                }
            }

            val entradas = entradas()
            // Sin local ni ruta, una búsqueda por SKU en vivo debe acotarse a
            // los locales del alcance; si no, KPI One devolvería la RM entera.
            val todosLosCodigos = resolverCodigos(entradas, filters).ifEmpty {
                if (alcanceRutas.isEmpty()) {
                    emptyList()
                } else {
                    entradas.map { it.codigo.trim().uppercase() }.distinct()
                }
            }
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

    override fun invalidarCache() {
        synchronized(cacheLock) { cache.clear() }
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
        alcanceKey() + "|" +
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
        )
    }

    private fun alcanceKey(): String = alcanceRutas.sorted().joinToString(",")

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
            .filter { entry ->
                alcanceRutas.isEmpty() || normalizarRuta(entry.rutero) in alcanceRutas
            }
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

        val ruta = normalizarRuta(filters.ruta)
        if (ruta.isBlank()) return emptyList()
        return entradas.filter {
            val nombre = normalizarRuta(it.rutero)
            nombre == ruta || (ruta.length >= 3 && nombre.startsWith(ruta))
        }.map { it.codigo.trim().uppercase() }
            .distinct()
    }

    private data class CacheEntry(
        /** Filas crudas de KPI One: los filtros se aplican al leer. */
        val items: List<StockItem>,
        /** Locales pedidos a la API; acotan las filas al reconstruir. */
        val codigos: List<String>,
        val total: Int,
        val truncated: Boolean,
        val at: Long,
    )

    private data class RouteIndex(
        val entries: List<RouteEntryEntity>,
        val at: Long,
    )

    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheLock = Any()

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
    }
}

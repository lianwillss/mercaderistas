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
 * La respuesta se cachea unos segundos porque cada tecla del buscador no debe
 * golpear la API. Un refresco manual pasa [force] = true y salta la caché.
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
                    return@withContext construirStockResult(
                        base = cache.items,
                        filtros = filters,
                        source = StockSource.LIVE,
                        fetchedAt = Instant.ofEpochMilli(cache.at).toString(),
                        total = cache.total,
                        truncated = cache.truncated,
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
            while (paginas < MAX_PAGINAS) {
                val pagina = client.fetchInventory(
                    storeCodes = codigos,
                    // La marca se filtra localmente para que el texto parcial
                    // ("caso") siga funcionando igual que en el snapshot.
                    brand = null,
                    query = filters.sku.trim(),
                    offset = offset,
                    limit = KpiOneClient.LIMIT,
                )
                if (paginas == 0) total = pagina.total
                items += pagina.items
                if (total > 0) onProgress(items.size.coerceAtMost(total), total)
                if (pagina.items.isEmpty()) break
                offset += KpiOneClient.LIMIT
                paginas++
                if (total > 0 && offset >= total) break
            }
            if (total <= 0) total = items.size

            val marca = normalizarStock(filters.marca)
            val sku = normalizarStock(filters.sku)
            val base = items.filter { item ->
                (codigos.isEmpty() || item.storeCode in codigos) &&
                    (marca.isEmpty() || normalizarStock(item.brand).contains(marca)) &&
                    (sku.isEmpty() ||
                        normalizarStock(item.sku).contains(sku) ||
                        normalizarStock(item.product).contains(sku))
            }
            val truncated = truncadoPorLocales || total > items.size
            val ahora = System.currentTimeMillis()
            guardarCache(clave, CacheEntry(base, total, truncated, ahora))

            construirStockResult(
                base = base,
                filtros = filters,
                source = StockSource.LIVE,
                fetchedAt = Instant.ofEpochMilli(ahora).toString(),
                total = total,
                truncated = truncated,
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

    private fun cacheKey(filters: StockFilters): String =
        normalizarStock(filters.local.substringBefore("·")) + "|" +
            normalizarRuta(filters.ruta) + "|" +
            normalizarStock(filters.marca) + "|" +
            normalizarStock(filters.sku)

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

        val ruta = normalizarRuta(filters.ruta)
        if (ruta.isBlank()) return emptyList()
        return entradas.filter {
            val nombre = normalizarRuta(it.rutero)
            nombre == ruta || (ruta.length >= 3 && nombre.startsWith(ruta))
        }.map { it.codigo.trim().uppercase() }
            .distinct()
    }

    private data class CacheEntry(
        val items: List<StockItem>,
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
        const val MAX_LOCALES = 60
        const val MAX_PAGINAS = 3
        const val MAX_CACHE = 12
        const val CACHE_TTL_MS = 30_000L
        const val RUTAS_TTL_MS = 60_000L
    }
}

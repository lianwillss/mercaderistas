package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.stock.remote.RemoteStockRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Elige en cada búsqueda entre KPI One en vivo y el snapshot local.
 *
 * - Con local, ruta o SKU y conexión: consulta en vivo y, si algo falla,
 *   devuelve el snapshot con un aviso. La pantalla nunca queda en blanco por
 *   un problema de red.
 * - Sin criterio suficiente (por ejemplo, sólo una marca) o sin conexión:
 *   usa el snapshot, que es rápido y ya está en memoria.
 *
 * El catálogo y las rutas salen del snapshot porque KPI One no trae el rutero;
 * las rutas de la búsqueda en vivo se resuelven en [RemoteStockRepository]
 * contra Room.
 */
@Singleton
class StockRepositoryRouter @Inject constructor(
    private val local: DemoStockRepository,
    private val remote: RemoteStockRepository,
    private val network: NetworkMonitor,
    private val cache: StockCache,
) : StockRepository {

    override val isDemo: Boolean get() = local.isDemo

    override val tieneDatos: Boolean get() = local.tieneDatos

    // El modo híbrido nunca deja la pantalla en "SinSesion": si KPI One no
    // responde, el snapshot sigue siendo una fuente válida.
    override val requiereSesion: Boolean = false

    override suspend fun hasSession(): Boolean = remote.hasSession()

    override suspend fun ensureSession(): Boolean {
        val localListo = local.tieneDatos
        if (!network.online()) return localListo
        val remotoListo = runCatching { remote.ensureSession() }.getOrDefault(false)
        return remotoListo || localListo
    }

    override suspend fun conectar(email: String, password: String): Boolean =
        remote.conectar(email, password)

    override suspend fun desconectar() {
        remote.desconectar()
        cache.clear()
    }

    override suspend fun ultimaActualizacion(): String? = local.ultimaActualizacion()

    override suspend fun generadoEn(): String? = local.generadoEn()

    override suspend fun catalog(): StockCatalog = withContext(Dispatchers.Default) {
        val localCatalogo = local.catalog()
        if (localCatalogo.isReady) return@withContext localCatalogo
        runCatching {
            withContext(Dispatchers.IO) { remote.catalog() }
        }.getOrDefault(localCatalogo)
    }

    override suspend fun search(filters: StockFilters): StockResult =
        search(filters, force = false)

    override suspend fun search(filters: StockFilters, force: Boolean): StockResult =
        searchLive(filters, force) ?: localSearch(filters)

    override suspend fun searchLocal(filters: StockFilters): StockResult =
        localSearch(filters)

    override suspend fun searchLive(
        filters: StockFilters,
        force: Boolean,
        onProgress: (loaded: Int, total: Int) -> Unit,
    ): StockResult? {
        if (!puedeEnVivo(filters)) return null

        if (!network.online()) {
            return localSearch(filters).copy(
                source = StockSource.SNAPSHOT,
                aviso = "Sin conexión: datos guardados",
            )
        }

        return try {
            remote.search(filters, force, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            localSearch(filters).copy(
                source = StockSource.SNAPSHOT,
                aviso = "No se pudo consultar KPI One: datos guardados",
            )
        }
    }

    override fun invalidarCache() = remote.invalidarCache()

    private suspend fun localSearch(filters: StockFilters): StockResult =
        withContext(Dispatchers.Default) { local.search(filters) }

    /** Una marca sola puede ser todo el catálogo; en vivo sólo con local/ruta/SKU. */
    private fun puedeEnVivo(filters: StockFilters): Boolean =
        filters.local.isNotBlank() ||
            filters.ruta.isNotBlank() ||
            filters.sku.trim().length >= 3
}

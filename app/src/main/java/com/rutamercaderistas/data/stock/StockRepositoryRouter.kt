package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.stock.remote.KpiHttpException
import com.rutamercaderistas.data.stock.remote.RemoteStockRepository
import com.rutamercaderistas.data.stock.remote.SessionExpiredException
import com.rutamercaderistas.data.stock.remote.StockQueryException
import com.rutamercaderistas.data.stock.remote.StockQueryTooLargeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
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
        } catch (_: StockQueryException) {
            // Los filtros no resuelven ningún local en el rutero: no es un
            // problema de red. Se devuelve null para que la pantalla use el
            // snapshot sin pintar un banner de error que confunde.
            null
        } catch (e: StockQueryTooLargeException) {
            Timber.d("Stock en vivo: %d locales, se usa el snapshot", e.locales)
            snapshotConAviso(
                filters,
                "Consulta grande (" + e.locales + " locales): KPI One no la " +
                    "entrega bien en vivo. Se muestran datos guardados; acota " +
                    "por local para ver el stock en vivo.",
            )
        } catch (e: SessionExpiredException) {
            Timber.w(e, "Stock en vivo: sesión de KPI One vencida")
            snapshotConAviso(
                filters,
                "Sesión de KPI One vencida. Vuelve a conectar en Ajustes; " +
                    "mostrando datos guardados.",
            )
        } catch (e: KpiHttpException) {
            Timber.w(e, "Stock en vivo: KPI One respondió HTTP %d", e.statusCode)
            if (e.statusCode == 503) {
                snapshotConAviso(
                    filters,
                    "KPI One está temporalmente ocupado (HTTP 503). " +
                        "Mostrando datos guardados; reintenta en unos segundos.",
                )
            } else {
                snapshotConAviso(
                    filters,
                    "KPI One respondió HTTP " + e.statusCode + ": datos guardados.",
                )
            }
        } catch (e: IOException) {
            Timber.w(e, "Stock en vivo: no se pudo conectar con KPI One")
            snapshotConAviso(
                filters,
                "No se pudo conectar con KPI One. Mostrando datos guardados.",
            )
        } catch (e: Exception) {
            Timber.w(e, "Stock en vivo: error inesperado")
            snapshotConAviso(filters, "No se pudo consultar KPI One: datos guardados")
        }
    }

    override suspend fun completarOsa(
        filtros: StockFilters,
        resultado: StockResult,
    ): StockResult? {
        if (!network.online()) return null
        return try {
            remote.completarOsa(filtros, resultado)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override fun invalidarCache() = remote.invalidarCache()

    private suspend fun localSearch(filters: StockFilters): StockResult =
        withContext(Dispatchers.Default) { local.search(filters) }

    /** Snapshot con un aviso que explica por qué no se pudo consultar en vivo. */
    private suspend fun snapshotConAviso(filters: StockFilters, aviso: String): StockResult =
        localSearch(filters).copy(source = StockSource.SNAPSHOT, aviso = aviso)

    /**
     * Una marca sola puede ser todo el catálogo; en vivo sólo con local, ruta
     * o SKU. El local y la ruta piden 2 caracteres: con uno solo la resolución
     * por subcadena arrastra cientos de locales y consulta media región.
     */
    private fun puedeEnVivo(filters: StockFilters): Boolean =
        filters.local.trim().length >= 2 ||
            filters.ruta.trim().length >= 2 ||
            filters.sku.trim().length >= 3
}

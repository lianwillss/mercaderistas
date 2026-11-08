package com.rutamercaderistas.data.stock.remote

import com.rutamercaderistas.data.stock.CoberturaTipo
import com.rutamercaderistas.data.stock.EstadoTipo
import com.rutamercaderistas.data.stock.StockCatalog
import com.rutamercaderistas.data.stock.StockFilters
import com.rutamercaderistas.data.stock.StockItem
import com.rutamercaderistas.data.stock.StockLocal
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockResult
import com.rutamercaderistas.data.stock.StockSort
import com.rutamercaderistas.data.stock.coberturaDe
import com.rutamercaderistas.data.stock.estadoDe
import com.rutamercaderistas.data.stock.normalizarStock
import com.rutamercaderistas.data.stock.sinDuplicados
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Búsqueda en vivo contra KPI One. Comparte [KpiOneClient] con la descarga
 * masiva para que login, reintentos y parseo sean idénticos.
 *
 * Hoy el modo activo es el archivo local; este repositorio queda listo para
 * cuando se quiera consultar sin snapshot.
 */
@Singleton
class RemoteStockRepository @Inject constructor(
    private val client: KpiOneClient,
    private val session: KpiSessionStore,
) : StockRepository {

    override val isDemo: Boolean = false

    override val requiereSesion: Boolean = true

    override suspend fun hasSession(): Boolean = client.hasSession()

    override suspend fun ensureSession(): Boolean = client.ensureSession()

    override suspend fun conectar(email: String, password: String): Boolean {
        val ok = client.login(email, password)
        if (ok) session.guardarCredenciales(email.trim(), password)
        return ok
    }

    override suspend fun desconectar() {
        client.logout()
        session.borrarTodo()
        client.clearCatalogCache()
    }

    override suspend fun catalog(): StockCatalog = withContext(Dispatchers.IO) {
        val catalogo = client.catalog()
        StockCatalog(
            locales = catalogo.codes.map { codigo ->
                StockLocal(codigo, catalogo.names[codigo] ?: codigo)
            },
            marcas = catalogo.brands,
            marcasPorRuta = emptyMap(),
        )
    }

    override suspend fun search(filters: StockFilters): StockResult = withContext(Dispatchers.IO) {
        val catalogo = catalog()
        val codigos = resolverLocales(filters.local, catalogo)
        val marca = resolverMarca(filters.marca, catalogo)
        val texto = filters.sku.trim()
        if (codigos.isEmpty() && marca == null && texto.isBlank()) {
            return@withContext StockResult()
        }

        val items = mutableListOf<StockItem>()
        var offset = 0
        var total = Int.MAX_VALUE
        var paginas = 0
        while (offset < total && paginas < MAX_PAGINAS) {
            val pagina = client.fetchInventory(
                storeCodes = codigos,
                brand = marca,
                query = texto,
                offset = offset,
                limit = KpiOneClient.LIMIT,
            )
            items += pagina.items
            total = pagina.total
            if (pagina.items.isEmpty()) break
            offset += KpiOneClient.LIMIT
            paginas++
        }

        val base = items.sinDuplicados()
        val unicos = base.distinctBy { it.sku }
        val conteoQuiebre = unicos.count { estadoDe(it) == EstadoTipo.Quiebre }
        val conteoBajo = unicos.count { estadoDe(it) == EstadoTipo.Bajo }
        val conteoDisponible = unicos.count { estadoDe(it) == EstadoTipo.Disponible }
        val conteoNegativos = unicos.count { it.stockUnits < 0.0 }
        val conteoCritico = unicos.count { coberturaDe(it) == CoberturaTipo.Critico }
        val conteoCoberturaBaja = unicos.count { coberturaDe(it) == CoberturaTipo.Bajo }
        val conteoSano = unicos.count { coberturaDe(it) == CoberturaTipo.Sano }
        val conteoSobrestock = unicos.count { coberturaDe(it) == CoberturaTipo.Sobrestock }

        val filtrados = base
            .filter { !filters.soloQuiebre || estadoDe(it) == EstadoTipo.Quiebre }
            .filter { filters.estado == null || estadoDe(it) == filters.estado }
            .filter { filters.cobertura == null || coberturaDe(it) == filters.cobertura }
            .filter { !filters.soloNegativos || it.stockUnits < 0.0 }
            .let { lista ->
                when (filters.orden) {
                    StockSort.GRAVEDAD -> lista.sortedWith(
                        compareBy(
                            { if (it.stockUnits < 0.0) 0 else 1 },
                            { if (estadoDe(it) == EstadoTipo.Quiebre) 0 else 1 },
                            { it.daysOfInventory ?: Int.MAX_VALUE },
                            { it.stockUnits },
                        ),
                    )
                    StockSort.PRODUCTO_AZ -> lista.sortedWith(
                        compareBy({ it.product.lowercase() }, { it.storeCode }),
                    )
                    StockSort.STOCK_ASC -> lista.sortedBy { it.stockUnits }
                    StockSort.STOCK_DESC -> lista.sortedByDescending { it.stockUnits }
                    StockSort.MENOR_COBERTURA -> lista.sortedBy {
                        it.coberturaDias ?: Double.MAX_VALUE
                    }
                }
            }
        StockResult(
            items = filtrados,
            conteoQuiebre = conteoQuiebre,
            conteoBajo = conteoBajo,
            conteoDisponible = conteoDisponible,
            conteoNegativos = conteoNegativos,
            conteoCritico = conteoCritico,
            conteoCoberturaBaja = conteoCoberturaBaja,
            conteoSano = conteoSano,
            conteoSobrestock = conteoSobrestock,
        )
    }

    private fun resolverLocales(texto: String, catalogo: StockCatalog): List<String> {
        if (texto.isBlank()) return emptyList()
        val buscado = normalizarStock(texto.substringBefore("·"))
        val porCodigo = catalogo.locales.filter {
            normalizarStock(it.code) == buscado ||
                (buscado.length >= 3 && normalizarStock(it.code).startsWith(buscado))
        }
        if (porCodigo.isNotEmpty()) return porCodigo.take(MAX_LOCALES).map { it.code }
        return catalogo.locales
            .filter { normalizarStock(it.name).contains(buscado) }
            .take(MAX_LOCALES)
            .map { it.code }
    }

    private fun resolverMarca(texto: String, catalogo: StockCatalog): String? {
        if (texto.isBlank()) return null
        val buscado = normalizarStock(texto)
        return catalogo.marcas.firstOrNull { normalizarStock(it) == buscado }
            ?: catalogo.marcas.firstOrNull { normalizarStock(it).contains(buscado) }
    }

    private companion object {
        const val MAX_PAGINAS = 8
        const val MAX_LOCALES = 8
    }
}

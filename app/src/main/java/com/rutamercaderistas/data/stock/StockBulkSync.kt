package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.local.RouteEntryEntity
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Qué locales bajar. Vacío en rutas/locales = toda la RM del rutero. */
data class StockSyncRequest(
    val rutas: Set<String> = emptySet(),
    val locales: Set<String> = emptySet(),
    val force: Boolean = false,
)

enum class StockSyncPhase { CONNECTING, CATALOG, DOWNLOADING, VALIDATING, SAVING, DONE }

data class StockSyncProgress(
    val phase: StockSyncPhase,
    val done: Int = 0,
    val total: Int = 0,
    val rows: Int = 0,
    val current: String = "",
    val failed: Int = 0,
    val startedAt: Long = 0L,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    val descripcion: String
        get() = when (phase) {
            StockSyncPhase.CONNECTING -> "Conectando con KPI One…"
            StockSyncPhase.CATALOG -> "Leyendo catálogo…"
            StockSyncPhase.DOWNLOADING ->
                if (total > 0) "Descargando $done/$total · $rows filas" else "Descargando stock…"
            StockSyncPhase.VALIDATING -> "Validando $rows filas…"
            StockSyncPhase.SAVING -> "Guardando snapshot…"
            StockSyncPhase.DONE -> "Stock actualizado: $rows filas"
        }
}

sealed interface StockSyncOutcome {
    data class Success(val data: DemoStockData, val meta: StockMeta) : StockSyncOutcome
    data class Blocked(
        val message: String,
        val rows: Int,
        val previousRows: Int,
        val locales: Int,
        val previousLocales: Int,
    ) : StockSyncOutcome
    data class Failure(val message: String, val cause: Throwable? = null) : StockSyncOutcome
}

/**
 * Genera stock.json desde KPI One sin depender de la Mac ni de Drive.
 *
 * - Toma los códigos de local del rutero ya indexado en Room y sus rutas.
 * - Baja el inventario en lotes de [BATCH_SIZE] locales (la API acepta
 *   `storeCodes` separados por coma), con reintentos en [KpiOneClient].
 * - Aplica el mismo control de calidad del script: si filas o locales caen
 *   más de 10 % contra el snapshot anterior, no pisa el archivo bueno.
 * - Escribe con move atómico y deja un .bak para volver atrás.
 */
@Singleton
class StockBulkSync @Inject constructor(
    private val client: KpiOneClient,
    private val routeDao: RouteEntryDao,
    private val storage: StockStorage,
    private val rmCodes: RmCodeProvider,
) {

    suspend fun run(
        request: StockSyncRequest = StockSyncRequest(),
        onProgress: suspend (StockSyncProgress) -> Unit = {},
    ): StockSyncOutcome = withContext(Dispatchers.IO) {
        val inicio = System.currentTimeMillis()
        try {
            onProgress(StockSyncProgress(StockSyncPhase.CONNECTING, startedAt = inicio))
            if (!client.ensureSession()) {
                return@withContext StockSyncOutcome.Failure(
                    "No se pudo entrar a KPI One. Revisa el correo y la clave en Ajustes.",
                )
            }

            onProgress(StockSyncProgress(StockSyncPhase.CATALOG, startedAt = inicio))
            val catalogo = client.catalog(force = true)
            val catalogoSet = catalogo.codes.toSet()
            val rm = rmCodes.codes()
            val alcance = alcanceDe(request, rm)
            val mapa = construirMapa(routeDao.getAllEntries(), request, rm)
            Timber.i(
                "StockBulkSync: %d códigos en el rutero, %d en RM, %d en el catálogo",
                mapa.size,
                rm.size,
                catalogoSet.size,
            )
            if (mapa.isEmpty()) {
                val detalle = if (request.rutas.isNotEmpty()) {
                    "Ninguna de tus rutas (" + request.rutas.sorted().joinToString(", ") +
                        ") tiene locales en el rutero."
                } else {
                    "El rutero no tiene locales para actualizar."
                }
                return@withContext StockSyncOutcome.Failure(
                    detalle + " Sincroniza el rutero primero.",
                )
            }

            val pedidos = mapa.keys
                .filter { it in catalogoSet }
                .sorted()
            val desconocidos = mapa.keys.filter { it !in catalogoSet }.sorted()
            Timber.i(
                "StockBulkSync: %d pedidos, %d desconocidos",
                pedidos.size,
                desconocidos.size,
            )
            if (pedidos.isEmpty()) {
                return@withContext StockSyncOutcome.Failure(
                    if (request.rutas.isNotEmpty()) {
                        "Ningún local de tus rutas coincide con KPI One."
                    } else {
                        "Ningún local del rutero coincide con KPI One."
                    },
                )
            }

            val fallidos = desconocidos.toMutableList()
            val items = mutableListOf<StockItem>()
            var hechos = 0
            onProgress(
                StockSyncProgress(
                    phase = StockSyncPhase.DOWNLOADING,
                    done = 0,
                    total = pedidos.size,
                    rows = 0,
                    failed = fallidos.size,
                    startedAt = inicio,
                ),
            )

            val lotes = pedidos.chunked(BATCH_SIZE)
            for ((indice, lote) in lotes.withIndex()) {
                currentCoroutineContext().ensureActive()
                try {
                    var offset = 0
                    var total = Int.MAX_VALUE
                    var paginas = 0
                    while (offset < total && paginas < MAX_PAGINAS) {
                        val pagina = client.fetchInventory(
                            storeCodes = lote,
                            offset = offset,
                            limit = KpiOneClient.LIMIT,
                        )
                        items += pagina.items
                        total = pagina.total
                        if (pagina.items.isEmpty()) break
                        offset += KpiOneClient.LIMIT
                        paginas++
                        currentCoroutineContext().ensureActive()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "StockBulkSync: falló el lote %s", lote.joinToString(","))
                    fallidos += lote
                }

                hechos += lote.size
                onProgress(
                    StockSyncProgress(
                        phase = StockSyncPhase.DOWNLOADING,
                        done = hechos,
                        total = pedidos.size,
                        rows = items.size,
                        current = lote.lastOrNull().orEmpty(),
                        failed = fallidos.size,
                        startedAt = inicio,
                    ),
                )
                if (indice < lotes.lastIndex) delay(DELAY_MS)
            }

            onProgress(
                StockSyncProgress(
                    phase = StockSyncPhase.VALIDATING,
                    done = pedidos.size,
                    total = pedidos.size,
                    rows = items.size,
                    failed = fallidos.size,
                    startedAt = inicio,
                ),
            )

            val pedidosSet = pedidos.toSet()
            val filas = items.filter { it.storeCode in pedidosSet }.sinDuplicados()
            if (filas.isEmpty()) {
                return@withContext StockSyncOutcome.Failure(
                    "KPI One no devolvió filas de stock. No se tocó el archivo anterior.",
                )
            }

            val locales = pedidos.map { codigo ->
                StockLocal(
                    code = codigo,
                    name = catalogo.names[codigo] ?: codigo,
                    rutas = mapa[codigo].orEmpty().sorted(),
                )
            }
            val datos = DemoStockData(items = filas, locales = locales, marcas = catalogo.brands)

            val previo = storage.read(META_FILE)?.let { StockFileCodec.decodeMeta(it) }
            val cambioAlcance = previo != null && previo.scope != alcance
            if (!request.force && previo != null && !cambioAlcance) {
                val caeFilas = previo.rows > 0 && filas.size < previo.rows * 0.9
                val caeLocales = previo.locales > 0 && locales.size < previo.locales * 0.9
                if (caeFilas || caeLocales) {
                    return@withContext StockSyncOutcome.Blocked(
                        message = "El snapshot nuevo parece incompleto: " +
                            filas.size + " filas vs " + previo.rows + " antes, " +
                            locales.size + " locales vs " + previo.locales + " antes.",
                        rows = filas.size,
                        previousRows = previo.rows,
                        locales = locales.size,
                        previousLocales = previo.locales,
                    )
                }
            }

            // Si falló más de 10 % de los locales, es más seguro conservar el
            // snapshot anterior que publicar uno incompleto.
            if (!request.force && fallidos.size > maxOf(3, pedidos.size / 10)) {
                return@withContext StockSyncOutcome.Blocked(
                    message = "Fallaron " + fallidos.size + " de " + pedidos.size +
                        " locales. No se reemplazó el stock anterior.",
                    rows = filas.size,
                    previousRows = previo?.rows ?: 0,
                    locales = locales.size,
                    previousLocales = previo?.locales ?: 0,
                )
            }

            onProgress(
                StockSyncProgress(
                    phase = StockSyncPhase.SAVING,
                    done = pedidos.size,
                    total = pedidos.size,
                    rows = filas.size,
                    failed = fallidos.size,
                    startedAt = inicio,
                ),
            )

            val terminado = System.currentTimeMillis()
            val meta = StockMeta(
                generatedAt = terminado,
                rows = filas.size,
                locales = locales.size,
                failed = fallidos.sorted(),
                maxSnapshot = filas.maxOfOrNull { it.snapshotDate },
                source = "kpi-one",
                durationMs = terminado - inicio,
                scope = alcance,
                rutas = request.rutas.map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted(),
            )
            storage.backup(STOCK_FILE)
            storage.backup(META_FILE)
            val texto = StockFileCodec.encode(
                data = datos,
                generatedAt = Instant.ofEpochMilli(terminado).toString(),
                source = "kpi-one",
            )
            if (!storage.writeAtomic(STOCK_FILE, texto)) {
                return@withContext StockSyncOutcome.Failure(
                    "No se pudo guardar el archivo de stock. Se conserva el anterior.",
                )
            }
            if (!storage.writeAtomic(META_FILE, StockFileCodec.encodeMeta(meta))) {
                Timber.w("StockBulkSync: no se pudo guardar stock-meta.json")
            }

            onProgress(
                StockSyncProgress(
                    phase = StockSyncPhase.DONE,
                    done = pedidos.size,
                    total = pedidos.size,
                    rows = filas.size,
                    failed = fallidos.size,
                    startedAt = inicio,
                ),
            )
            StockSyncOutcome.Success(datos, meta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "StockBulkSync falló")
            StockSyncOutcome.Failure(e.message ?: "No se pudo actualizar el stock.", e)
        }
    }

    /**
     * Clave del alcance que se guarda en la meta. El QC sólo compara contra el
     * snapshot anterior cuando la clave es idéntica: bajar "Mis rutas" después
     * de la RM no es una caída de datos.
     */
    private fun alcanceDe(request: StockSyncRequest, rm: Set<String>): String = when {
        request.rutas.isNotEmpty() -> claveAlcanceRutas(request.rutas)
        request.locales.isNotEmpty() -> "locales:" + request.locales
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString(",")
        rm.isEmpty() -> StockAlcance.SCOPE_ALL
        else -> StockAlcance.SCOPE_RM
    }

    private fun construirMapa(
        entries: List<RouteEntryEntity>,
        request: StockSyncRequest,
        rm: Set<String>,
    ): Map<String, Set<String>> {
        val rutasBuscadas = request.rutas.map { it.trim() }.filter { it.isNotEmpty() }
        val localesBuscados = request.locales.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val filtrarPorRegion = rutasBuscadas.isEmpty() && localesBuscados.isEmpty()

        return entries.asSequence()
            .filter { entry ->
                val codigo = entry.codigo.trim().uppercase()
                val porRegion = esRm(entry.region)
                // El rutero real trae REGION vacía: la lista RM es el respaldo.
                val porLista = codigo in rm
                val sinDatosDeRegion = rm.isEmpty() && entry.region.isBlank()
                codigo.isNotEmpty() &&
                    (localesBuscados.isEmpty() || codigo in localesBuscados) &&
                    (rutasBuscadas.isEmpty() ||
                        rutasBuscadas.any { it.equals(entry.rutero.trim(), ignoreCase = true) }) &&
                    (!filtrarPorRegion || porRegion || porLista || sinDatosDeRegion)
            }
            .groupBy { it.codigo.trim().uppercase() }
            .mapValues { (_, lista) ->
                lista.mapNotNull { it.rutero.trim().takeIf(String::isNotBlank) }.toSet()
            }
    }

    private fun esRm(region: String): Boolean {
        val texto = region.uppercase().trim()
        return texto == "RM" ||
            texto.startsWith("RM ") ||
            texto.contains("METROPOLITANA")
    }

    companion object {
        const val STOCK_FILE = "stock.json"
        const val META_FILE = "stock-meta.json"
        private const val BATCH_SIZE = 4
        private const val DELAY_MS = 120L
        private const val MAX_PAGINAS = 20
    }
}

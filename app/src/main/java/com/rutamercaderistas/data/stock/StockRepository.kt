package com.rutamercaderistas.data.stock

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente de stock de la app.
 *
 * Contrato deliberadamente angosto: la pantalla no sabe si los datos vienen de
 * KPI One en vivo o del snapshot local. [StockRepositoryRouter] decide en cada
 * búsqueda y deja el snapshot como respaldo.
 */
interface StockRepository {
    /** true mientras la pantalla use la muestra local en vez del proxy. */
    val isDemo: Boolean get() = false

    /** ¿Hay filas cargadas? Sirve para no forzar "SinSesion" si el snapshot existe. */
    val tieneDatos: Boolean get() = false

    /** El archivo local no necesita cookie; la consulta en vivo sí. */
    val requiereSesion: Boolean get() = false

    /** ¿Hay sesión de KPI One guardada? El demo siempre responde que sí. */
    suspend fun hasSession(): Boolean = true

    /** Deja la sesión lista sin intervención del usuario. */
    suspend fun ensureSession(): Boolean = true

    /** Prueba un acceso escrito en Ajustes; lo guarda si funciona. */
    suspend fun conectar(email: String, password: String): Boolean = false

    /** Olvida credenciales y sesión. */
    suspend fun desconectar() = Unit

    /** Fecha del dato más nuevo del archivo cargado (ISO), si se conoce. */
    suspend fun ultimaActualizacion(): String? = null

    /** Hora en que se generó el snapshot (ISO-8601), si el archivo la trae. */
    suspend fun generadoEn(): String? = null

    suspend fun catalog(): StockCatalog

    suspend fun search(filters: StockFilters): StockResult

    /**
     * Variante con [force] para saltar la caché corta del modo en vivo.
     * Las fuentes sin caché pueden ignorarlo.
     */
    suspend fun search(filters: StockFilters, force: Boolean): StockResult =
        search(filters)

    /**
     * Resultado local/snapshot para pintar de inmediato mientras se pide la
     * versión en vivo. Las fuentes sin snapshot pueden devolver lo mismo que
     * [search].
     */
    suspend fun searchLocal(filters: StockFilters): StockResult = search(filters)

    /**
     * Consulta en vivo. Devuelve null cuando la fuente no tiene modo remoto
     * (por ejemplo, sólo una marca: se resuelve con el snapshot).
     */
    suspend fun searchLive(
        filters: StockFilters,
        force: Boolean,
        onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    ): StockResult? = null

    /** Vacía la caché de consultas en vivo, si la fuente tiene una. */
    fun invalidarCache() = Unit

    /**
     * Acota catálogo y búsquedas a estas rutas. Vacío = sin filtro (RM o todo
     * el snapshot disponible). Lo fija el ViewModel cuando el alcance es
     * "Mis rutas".
     */
    fun fijarAlcance(rutas: Set<String>) = Unit
}

/**
 * Datos de la muestra local (9 locales exportados de KPI One).
 * Se elimina cuando el proxy esté conectado.
 */
@Singleton
class DemoStockRepository @Inject constructor(
    data: DemoStockData,
    origenInicial: StockOrigin,
) : StockRepository {

    constructor(data: DemoStockData) : this(data, StockOrigin.ASSET)

    @Volatile
    private var origin: StockOrigin = origenInicial

    override val isDemo: Boolean get() = origin == StockOrigin.ASSET

    override val tieneDatos: Boolean get() = data.items.isNotEmpty()

    val origen: StockOrigin get() = origin

    @Volatile
    private var data: DemoStockData = data

    /**
     * Rutas normalizadas del alcance actual. Vacío = no limitar (RM o el
     * snapshot completo). Lo fija el ViewModel al abrir o al cambiar "Mis
     * rutas" / "RM completa".
     */
    @Volatile
    private var alcanceRutas: Set<String> = emptySet()

    override fun fijarAlcance(rutas: Set<String>) {
        alcanceRutas = rutas
            .map { normalizarRuta(it) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Códigos de local que caen dentro del alcance. null = sin filtro.
     * Se cruza por nombre de ruta normalizado porque el snapshot guarda las
     * rutas tal como venían en el rutero.
     */
    private fun codigosDelAlcance(): Set<String>? {
        val rutas = alcanceRutas
        if (rutas.isEmpty()) return null
        return data.locales
            .filter { local -> local.rutas.any { normalizarRuta(it) in rutas } }
            .map { it.code }
            .toSet()
    }

    // Se descartan las marcas dadas de baja acá, en el único punto por donde
    // pasan catálogo y búsqueda: así también aplica a los snapshots nuevos que
    // lleguen por URL o KPI One, no sólo al JSON empaquetado.
    private val items: List<StockItem>
        get() {
            val codigos = codigosDelAlcance()
            return data.items.filterNot { item ->
                marcaExcluida(item.brand) ||
                    (codigos != null && item.storeCode !in codigos)
            }
        }

    /** Reemplaza la muestra en memoria tras una actualización por URL o KPI One. */
    fun actualizarDatos(nuevos: DemoStockData, nuevoOrigen: StockOrigin = StockOrigin.KPI_ONE) {
        data = nuevos
        origin = nuevoOrigen
        fechaCache = fechaMasNueva(nuevos)
        // El snapshot recién aplicado cuenta como generado ahora si el
        // archivo no traía su propio instante.
        generadoCache = nuevos.generatedAt ?: Instant.now().toString()
    }

    @Volatile
    private var fechaCache: String? = fechaMasNueva(data)

    @Volatile
    private var generadoCache: String? = data.generatedAt

    override suspend fun ultimaActualizacion(): String? = fechaCache

    override suspend fun generadoEn(): String? = generadoCache

    private fun fechaMasNueva(datos: DemoStockData): String? =
        datos.items.maxOfOrNull { it.snapshotDate }

    override suspend fun catalog(): StockCatalog {
        val codigosAlcance = codigosDelAlcance()
        val locales = data.locales.ifEmpty {
            items.map { StockLocal(it.storeCode, it.storeName) }
                .distinctBy { it.code }
                .sortedBy { it.code }
        }.let { lista ->
            if (codigosAlcance == null) lista else lista.filter { it.code in codigosAlcance }
        }
        // Con alcance, la marca se ofrece sólo si tiene stock en esos locales:
        // el catálogo general incluye marcas que no llegan a todas las rutas.
        val marcas = if (codigosAlcance == null) {
            data.marcas.filterNot { marcaExcluida(it) }
                .ifEmpty { items.map { it.brand }.distinct().sorted() }
        } else {
            items.map { it.brand }.distinct().sorted()
        }
        val rutas = locales.flatMap { it.rutas }
            .filter { alcanceRutas.isEmpty() || normalizarRuta(it) in alcanceRutas }
            .distinct()
            .sorted()
        // Marcas que tienen stock en los locales de cada ruta: al elegir una ruta
        // el selector de marca se limita a las marcas realmente presentes.
        // Items agrupados por local UNA vez: antes cada ruta recorría los 13k
        // items completos (296 locales x 13.081 = millones de comparaciones en
        // cada apertura de la pestaña).
        val itemsPorLocal = items.groupBy { it.storeCode }
        val marcasPorRuta = locales.flatMap { local ->
            local.rutas.map { ruta -> normalizarRuta(ruta) to local.code }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, codigos) ->
            codigos.asSequence()
                .flatMap { codigo -> itemsPorLocal[codigo].orEmpty().asSequence() }
                .map { it.brand }.distinct().sorted().toList()
        }
        // Conteo de locales por (ruta, marca) para mostrar en el combo.
        val localesPorRutaMarca = locales.flatMap { local ->
            val marcasLocal = itemsPorLocal[local.code].orEmpty().map { it.brand }.distinct()
            local.rutas.flatMap { ruta ->
                marcasLocal.map { marca -> Triple(normalizarRuta(ruta), marca, local.code) }
            }
        }.groupBy({ it.first to it.second }, { it.third })
            .mapValues { it.value.toSet().size }
            .entries.groupBy({ it.key.first }, { it.key.second to it.value })
            .mapValues { (_, v) -> v.toMap() }
        return StockCatalog(
            locales = locales.sortedBy { it.code },
            marcas = marcas,
            rutas = rutas,
            marcasPorRuta = marcasPorRuta,
            localesPorRutaMarca = localesPorRutaMarca,
        )
    }

    override suspend fun search(filters: StockFilters): StockResult {
        val texto = normalizarStock(filters.sku)
        val marca = normalizarStock(filters.marca)
        // Si el usuario eligió un local, el campo muestra "J521 · JUMBO ...".
        // Basta con lo que va antes del separador para identificar el código.
        val local = normalizarStock(filters.local)
        val localCodigo = normalizarStock(filters.local.substringBefore("·"))
        // La ruta viene del rutero: se resuelve a un conjunto de locales.
        val ruta = normalizarRuta(filters.ruta)
        val codigosRuta: Set<String>? = if (ruta.isBlank()) {
            null
        } else {
            data.locales.filter { l -> l.rutas.any { normalizarRuta(it).startsWith(ruta) } }
                .map { it.code }.toSet()
        }
        val base = items.filter { item ->
            (codigosRuta == null || item.storeCode in codigosRuta) &&
            (local.isEmpty() ||
                normalizarStock(item.storeCode).contains(localCodigo) ||
                normalizarStock(item.storeName).contains(local)) &&
                (marca.isEmpty() || normalizarStock(item.brand).contains(marca)) &&
                (texto.isEmpty() ||
                    normalizarStock(item.sku).contains(texto) ||
                    normalizarStock(item.product).contains(texto))
        }
        return construirStockResult(base, filters)
    }
}

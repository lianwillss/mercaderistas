package com.rutamercaderistas.data.stock

/**
 * Normaliza para buscar: sin tildes, sin mayúsculas, sin espacios sobrantes.
 * "Ñuñoa" y "nunoa" deben encontrar lo mismo.
 *
 * El patrón va compilado UNA vez: antes se construía un [Regex] por llamada y
 * esta función se ejecuta miles de veces (un item de stock por vez), lo que
 * dejaba el hilo principal bloqueado >5 s (ANR al abrir Stock).
 */
private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normalizarStock(texto: String): String =
    java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .uppercase()
        .trim()

/** Filtros que el usuario manipula en la pantalla de stock. */
data class StockFilters(
    val local: String = "",
    val ruta: String = "",
    val marca: String = "",
    val sku: String = "",
    val soloQuiebre: Boolean = false,
    val soloNegativos: Boolean = false,
    /** Cruce del negativo con la respuesta OSA de la última visita. */
    val validacion: ValidacionNegativo? = null,
    /** Filtra por estado (quiebre, bajo, disponible); null = todos. */
    val estado: EstadoTipo? = null,
    /** Filtra por tramo de cobertura de venta; null = todos. */
    val cobertura: CoberturaTipo? = null,
    val orden: StockSort = StockSort.PRODUCTO_AZ,
    val modo: StockMode = StockMode.POR_LOCAL,
)

/** Estado de un producto según el stock y los días de inventario de KPI One. */
enum class EstadoTipo(val texto: String) {
    /** Stock bajo cero: no es un quiebre, es un negativo. */
    Negativo("Negativo"),
    Quiebre("Quiebre"),
    Bajo("Bajo"),
    Disponible("Disponible"),
}

/**
 * Cobertura de venta: cuántos días aguanta el stock al ritmo de venta actual.
 * Es una dimensión distinta de [EstadoTipo]: un producto puede estar "disponible"
 * y aun así tener sólo 3 días de cobertura.
 */
enum class CoberturaTipo(val texto: String) {
    /** Menos de 7 días: se acaba esta semana. */
    Critico("Se agota"),
    /** Entre 7 y 14 días. */
    Bajo("Justo"),
    /** Entre 15 y 30 días. */
    Sano("Holgado"),
    /** Más de 30 días: plata dormida. */
    Sobrestock("Exceso"),
    /** Vendió 0 en los últimos 28 días. */
    SinVenta("Sin venta"),
    /** La API no trajo venta para este SKU. */
    SinDato("Sin dato"),
}

/**
 * Calcula la cobertura con la venta que manda KPI One: 28 días manda,
 * 7 días como respaldo. Devuelve null cuando no hay con qué dividir.
 */
fun coberturaDe(item: StockItem): CoberturaTipo = when {
    // El negativo es un dato inválido: no se puede calcular cobertura.
    item.stockUnits < 0.0 -> CoberturaTipo.SinDato
    item.saleLast4w == 0 -> CoberturaTipo.SinVenta
    item.ventaDiaria == null -> CoberturaTipo.SinDato
    else -> when (val dias = item.stockUnits / item.ventaDiaria!!) {
        in 0.0..<7.0 -> CoberturaTipo.Critico
        in 7.0..14.0 -> CoberturaTipo.Bajo
        in 14.0..30.0 -> CoberturaTipo.Sano
        else -> CoberturaTipo.Sobrestock
    }
}

fun estadoDe(item: StockItem): EstadoTipo {
    val dias = item.daysOfInventory
    return when {
        // El negativo manda: un SKU en -13 no es un quiebre, es un negativo.
        item.stockUnits < 0.0 -> EstadoTipo.Negativo
        item.quiebre || item.stockUnits == 0.0 -> EstadoTipo.Quiebre
        dias != null && dias <= 3 -> EstadoTipo.Bajo
        dias == null && item.stockUnits <= 5.0 -> EstadoTipo.Bajo
        else -> EstadoTipo.Disponible
    }
}

/**
 * Respuesta OSA ("¿El producto está presente en góndola?") de la última
 * visita registrada para un local y producto.
 *
 * La OSA no es stock: es lo que el mercaderista vio en góndola. Cruzarla con
 * el negativo del portal sirve para detectar datos desactualizados: si el
 * portal dice negativo pero la OSA dice que el producto está exhibido, el
 * stock probablemente no se ajustó.
 */
data class OsaRespuesta(
    /** true = estaba en góndola ("sí"); false = no estaba ("no"). */
    val estaEnGondola: Boolean,
    /** Fecha de la respuesta (ISO yyyy-MM-dd). */
    val fecha: String = "",
    /** Fecha de la visita que contiene la respuesta (ISO yyyy-MM-dd). */
    val visitaFecha: String = "",
    /** Motivo/gestion escrito por el mercaderista, si lo hubo. */
    val motivo: String? = null,
    /** Stock contado en la visita, si el flujo lo pidió. */
    val stockContado: Double? = null,
    /** Stock B2B que mostraba KPI One en el momento de la visita. */
    val stockB2bVisita: Double? = null,
)

/**
 * Respuesta OSA tal como llega de KPI One. Se mantiene fuera de [StockItem]
 * para que el cliente HTTP no dependa de la validación de UI.
 */
data class OsaRespuestaRemota(
    val storeCode: String,
    val productId: Int?,
    val sku: String,
    val respuesta: Boolean,
    /** respondedAt de la respuesta (ISO, con hora). */
    val fecha: String,
    /** Fecha de la visita donde se respondió (yyyy-MM-dd). */
    val visitaFecha: String,
    val motivo: String? = null,
    val stockContado: Double? = null,
    val stockB2bVisita: Double? = null,
) {
    fun aRespuesta() = OsaRespuesta(
        estaEnGondola = respuesta,
        fecha = fecha.substringBefore("T").ifBlank { fecha },
        visitaFecha = visitaFecha,
        motivo = motivo,
        stockContado = stockContado,
        stockB2bVisita = stockB2bVisita,
    )
}

/** Resultado de cruzar un negativo con la OSA de la última visita. */
enum class ValidacionNegativo(val texto: String) {
    /** OSA "no": el negativo coincide con lo que se vio en góndola. */
    CONFIRMADO("Confirmado"),

    /** OSA "sí": el producto está exhibido y el stock del portal no cuadra. */
    CONTRADICE("Por verificar"),

    /** No hay respuesta OSA para este local/producto/fecha. */
    SIN_DATO("Sin OSA"),
}

/**
 * Sólo los negativos se cruzan: el resto de los estados no necesita OSA.
 * Sin respuesta o con stock no negativo queda [ValidacionNegativo.SIN_DATO].
 */
fun validacionNegativo(item: StockItem): ValidacionNegativo = when {
    item.stockUnits >= 0.0 -> ValidacionNegativo.SIN_DATO
    item.osa == null -> ValidacionNegativo.SIN_DATO
    item.osa.estaEnGondola -> ValidacionNegativo.CONTRADICE
    else -> ValidacionNegativo.CONFIRMADO
}

/** Ruta comparable: "EMU-3", "emu 3" y "emu3" quedan iguales. */
fun normalizarRuta(texto: String): String =
    normalizarStock(texto).filter { it.isLetterOrDigit() }

/**
 * Marcas dadas de baja: no deben aparecer en ninguna pantalla (selector,
 * búsqueda, conteos, compartir). Se comparan sin espacios ni signos porque la
 * misma marca llega como "BERRY SUR" en el catálogo EAN y "BERRYSUR" en stock.
 */
private val MARCAS_EXCLUIDAS = setOf(
    "BERRYSUR",
    // Baja pedida por el cliente: BREDEN MASTER no la ven los mercaderistas en sus zonas.
    // Se excluye al cargar stock para aligerar la carga y no mostrarla en selectores.
    "BREDENMASTER",
)

fun marcaExcluida(marca: String): Boolean =
    normalizarStock(marca).filter { it.isLetterOrDigit() } in MARCAS_EXCLUIDAS

/**
 * Local y ruta son caminos alternativos: escribir un local o una marca suelta
 * la ruta. La ruta, en cambio, **acota** en vez de reiniciar: conserva la marca
 * y el producto, para que "aceite en EMU-3" y "EMU-3 con aceite" den lo mismo.
 * Si la marca conservada no existe en la ruta nueva, se limpia una sola vez al
 * recomponer (ver [marcaFueraDeRuta]).
 */
fun StockFilters.conLocal(valor: String): StockFilters {
    val buscar = valor.isNotBlank()
    return copy(
        local = valor,
        ruta = "",
        // Al buscar por local, la tarjeta por defecto es Disponible.
        estado = if (buscar) EstadoTipo.Disponible else estado,
        soloQuiebre = if (buscar) false else soloQuiebre,
        soloNegativos = if (buscar) false else soloNegativos,
        validacion = if (buscar) null else validacion,
    )
}

fun StockFilters.conMarca(valor: String): StockFilters = copy(marca = valor)

fun StockFilters.conRuta(valor: String): StockFilters {
    val buscar = valor.isNotBlank()
    return copy(
        ruta = valor,
        // Sólo el local se descarta: es el camino alternativo. Marca y
        // producto se conservan porque la ruta los acota, no los reemplaza.
        local = "",
        // Al buscar por ruta, la tarjeta por defecto es Disponible.
        estado = if (buscar) EstadoTipo.Disponible else estado,
        soloQuiebre = if (buscar) false else soloQuiebre,
        soloNegativos = if (buscar) false else soloNegativos,
        validacion = if (buscar) null else validacion,
    )
}

/** Sin local, marca o producto no se muestra stock: la pantalla queda en blanco. */
val StockFilters.hayCriterio: Boolean
    get() = local.isNotBlank() || ruta.isNotBlank() || marca.isNotBlank() || sku.isNotBlank()

enum class StockSort(val label: String) {
    GRAVEDAD("Gravedad"),
    MENOR_COBERTURA("Menor cobertura"),
    PRODUCTO_AZ("Producto A-Z"),
    STOCK_ASC("Stock: menor a mayor"),
    STOCK_DESC("Stock: mayor a menor"),
}

enum class StockMode { POR_LOCAL, POR_MARCA }

/** Una fila de stock tal como la devuelve KPI One (vía proxy). */
data class StockItem(
    val storeCode: String,
    val storeName: String,
    val brand: String,
    val product: String,
    val sku: String,
    val stockUnits: Double,
    val transitUnits: Double?,
    val snapshotDate: String,
    val quiebre: Boolean,
    val daysOfInventory: Int? = null,
    /** Unidades vendidas en los últimos 7 días según KPI One; null = sin dato. */
    val saleLastWeek: Int? = null,
    /** Unidades vendidas en los últimos 28 días según KPI One; null = sin dato. */
    val saleLast4w: Int? = null,
    /** ID interno de KPI One para el local; permite cruzar OSA sin ambigüedad. */
    val storeId: Int? = null,
    /** ID interno de KPI One para el producto; permite cruzar OSA sin ambigüedad. */
    val productId: Int? = null,
    /** Respuesta OSA de la última visita, sólo para negativos. */
    val osa: OsaRespuesta? = null,
) {
    /**
     * Venta diaria promedio. La ventana de 28 días manda porque suaviza promociones;
     * la de 7 días sólo se usa cuando la de 28 no vino.
     */
    val ventaDiaria: Double?
        get() = when {
            saleLast4w != null && saleLast4w > 0 -> saleLast4w / 28.0
            saleLastWeek != null && saleLastWeek > 0 -> saleLastWeek / 7.0
            else -> null
        }

    /** Días que aguanta el stock al ritmo actual; null si no hay venta con qué calcular. */
    val coberturaDias: Double?
        get() = ventaDiaria?.takeIf { it > 0.0 && stockUnits >= 0.0 }?.let { stockUnits / it }

    /** "74" o "6.973,39" — el stock de KPI One es decimal. */
    val stockLabel: String
        get() = if (stockUnits % 1.0 == 0.0) {
            stockUnits.toLong().toString()
        } else {
            String.format(java.util.Locale("es", "CL"), "%,.2f", stockUnits)
        }

    val transitLabel: String
        get() = transitUnits?.let { t ->
            if (t % 1.0 == 0.0) t.toLong().toString()
            else String.format(java.util.Locale("es", "CL"), "%,.2f", t)
        } ?: "—"
}

/**
 * KPI One devuelve a veces dos filas para el mismo local y SKU (descripciones
 * distintas del mismo producto). Se conserva la más completa: sin duplicados
 * las claves de la lista son únicas y el conteo no se infla.
 */
fun List<StockItem>.sinDuplicados(): List<StockItem> {
    val porClave = LinkedHashMap<String, StockItem>()
    for (item in this) {
        val clave = item.storeCode + "|" + item.sku
        val actual = porClave[clave]
        porClave[clave] = if (actual == null) item else masCompleta(actual, item)
    }
    return porClave.values.toList()
}

private fun masCompleta(a: StockItem, b: StockItem): StockItem = when {
    // La OSA es el dato más valioso de esta fila: si una descripción la trae
    // y la otra no, se conserva la que la tiene.
    a.osa == null && b.osa != null -> b
    a.osa != null && b.osa == null -> a
    a.daysOfInventory == null && b.daysOfInventory != null -> b
    a.daysOfInventory != null && b.daysOfInventory == null -> a
    b.product.length > a.product.length -> b
    else -> a
}

/** De dónde salieron las filas de esta consulta. */
enum class StockSource {
    /** Consulta hecha a KPI One en el momento de buscar. */
    LIVE,

    /** Última búsqueda guardada en disco; se está revalidando. */
    CACHE,

    /** Snapshot local (asset, archivo generado o respaldo de Drive). */
    SNAPSHOT,
}

/** Resultado agregado de una consulta. */
data class StockResult(
    val items: List<StockItem> = emptyList(),
    // Conteos sobre la búsqueda SIN filtrar por estado ni negativos: así las
    // tarjetas siguen mostrando su número aunque una esté seleccionada.
    val conteoQuiebre: Int = 0,
    val conteoBajo: Int = 0,
    val conteoDisponible: Int = 0,
    val conteoNegativos: Int = 0,
    val conteoCritico: Int = 0,
    val conteoCoberturaBaja: Int = 0,
    val conteoSano: Int = 0,
    val conteoSobrestock: Int = 0,
    /** Negativos que la OSA confirma fuera de góndola. */
    val conteoConfirmados: Int = 0,
    /** Negativos con OSA "sí": el stock probablemente está desactualizado. */
    val conteoContradicen: Int = 0,
    /** Negativos sin respuesta OSA para el local/producto. */
    val conteoSinOsa: Int = 0,
    /**
     * true cuando ya se intentó cruzar TODOS los negativos con OSA. Mientras
     * sea false no se pinta "Sin OSA": la respuesta puede estar en camino.
     */
    val osaConsultada: Boolean = false,
    /** Origen de las filas: en vivo o snapshot local. */
    val source: StockSource = StockSource.SNAPSHOT,
    /** Instante ISO en que se consultó KPI One; null si vino del snapshot. */
    val fetchedAt: String? = null,
    /**
     * Filas que reportó KPI One para la consulta. Con la descarga completa
     * coincide con lo bajado (incluye duplicados que luego se deduplican).
     */
    val total: Int = 0,
    /**
     * true cuando la descarga en vivo no alcanzó el total de KPI One: se cortó
     * por el tope de filas/locales o la API dejó de paginar antes de tiempo.
     * Los conteos de las tarjetas son parciales en ese caso.
     */
    val truncated: Boolean = false,
    /** Aviso para la cabecera cuando se usó el respaldo local. */
    val aviso: String? = null,
) {
    val quiebres: Int get() = items.count { estadoDe(it) == EstadoTipo.Quiebre }
    val negativos: Int get() = items.count { it.stockUnits < 0.0 }
    /** Negativos que OSA marca como "sí está" y conviene revisar. */
    val porVerificar: Int
        get() = items.count { validacionNegativo(it) == ValidacionNegativo.CONTRADICE }
    val uniqueProducts: Int get() = items.map { it.sku }.distinct().size
    val uniqueLocales: Int get() = items.map { it.storeCode }.distinct().size
    val uniqueBrands: Int get() = items.map { it.brand }.distinct().size
    val maxSnapshot: String? get() = items.maxOfOrNull { it.snapshotDate }
}

/** Catálogo para poblar los desplegables. */
data class StockCatalog(
    val locales: List<StockLocal> = emptyList(),
    val marcas: List<String> = emptyList(),
    val rutas: List<String> = emptyList(),
    /** Marcas presentes en los locales de cada ruta (clave = ruta normalizada). */
    val marcasPorRuta: Map<String, List<String>> = emptyMap(),
    /** Cantidad de locales de cada ruta que tienen cada marca: ruta -> marca -> #locales. */
    val localesPorRutaMarca: Map<String, Map<String, Int>> = emptyMap(),
) {
    val isReady: Boolean get() = locales.isNotEmpty() || marcas.isNotEmpty()
}

/**
 * Marcas que se ofrecen para una ruta: sólo las que tienen stock en sus locales.
 * Si la fuente no trae el desglose por ruta (catálogo remoto), se ofrece la
 * lista completa en vez de dejar el selector vacío.
 */
fun StockCatalog.marcasPara(ruta: String): List<String> {
    if (ruta.isBlank()) return marcas
    return marcasPorRuta[normalizarRuta(ruta)]?.takeIf { it.isNotEmpty() } ?: marcas
}

/**
 * ¿La marca escrita dejó de existir en la ruta elegida? Sólo se considera
 * inválida una marca **completa** del catálogo que no está en la ruta.
 *
 * El texto a medio escribir ("caso", "cas") es una búsqueda en curso: borrarlo
 * en cada tecla impedía escribir una marca con la ruta ya elegida.
 */
fun StockCatalog.marcaFueraDeRuta(ruta: String, marca: String): Boolean {
    if (ruta.isBlank() || marca.isBlank()) return false
    val buscada = normalizarStock(marca)
    if (marcasPara(ruta).any { normalizarStock(it) == buscada }) return false
    return marcas.any { normalizarStock(it) == buscada }
}

data class StockLocal(
    val code: String,
    val name: String,
    val rutas: List<String> = emptyList(),
) {
    /** "L207 · Líder Plaza Vespucio"; si el nombre ya trae el código, no se repite. */
    val label: String
        get() = if (name.startsWith(code, ignoreCase = true)) name else "$code · $name"
}

enum class StockStatus { Loading, Ready, Empty, Error, Idle, SinSesion }

/** De dónde salieron las filas que está mostrando la pantalla. */
enum class StockOrigin { ASSET, DRIVE, KPI_ONE }

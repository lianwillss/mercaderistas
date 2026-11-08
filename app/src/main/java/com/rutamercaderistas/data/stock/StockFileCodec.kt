package com.rutamercaderistas.data.stock

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Formato del archivo de stock (compacto, igual al que genera tools/stock-sync.mjs). */
@Serializable
data class StockFileDto(
    /** Versión del formato; los lectores viejos ignoran campos nuevos. */
    val v: Int = 3,
    val generatedAt: String? = null,
    val source: String? = null,
    val locales: List<StockLocalDto> = emptyList(),
    val marcas: List<String> = emptyList(),
    val items: List<StockRowDto> = emptyList(),
)

@Serializable
data class StockLocalDto(
    @SerialName("c") val code: String = "",
    @SerialName("n") val name: String = "",
    @SerialName("r") val rutas: List<String> = emptyList(),
)

@Serializable
data class StockRowDto(
    @SerialName("s") val storeCode: String = "",
    @SerialName("n") val storeName: String = "",
    @SerialName("b") val brand: String = "",
    @SerialName("p") val product: String = "",
    @SerialName("k") val sku: String = "",
    @SerialName("u") val stock: Double? = null,
    @SerialName("t") val transit: Double? = null,
    @SerialName("d") val date: String = "",
    @SerialName("q") val quiebre: Int? = null,
    // KPI One manda enteros y decimales mezclados en este campo.
    @SerialName("i") val days: Double? = null,
    /** Unidades vendidas en 7 días (KPI One). Ausente en archivos v2 y anteriores. */
    @SerialName("vw") val saleWeek: Int? = null,
    /** Unidades vendidas en 28 días (KPI One). Ausente en archivos v2 y anteriores. */
    @SerialName("v28") val sale4w: Int? = null,
) {
    fun toItem() = StockItem(
        storeCode = storeCode,
        storeName = storeName,
        brand = brand,
        product = product,
        sku = sku,
        stockUnits = stock ?: 0.0,
        transitUnits = transit,
        snapshotDate = date,
        quiebre = quiebre == 1,
        daysOfInventory = days?.toInt(),
        saleLastWeek = saleWeek,
        saleLast4w = sale4w,
    )
}

/** Metadatos de la última descarga; se guardan aparte para el control de calidad. */
@Serializable
data class StockMeta(
    val generatedAt: Long = 0L,
    val rows: Int = 0,
    val locales: Int = 0,
    val failed: List<String> = emptyList(),
    val maxSnapshot: String? = null,
    val source: String = "kpi-one",
    val durationMs: Long = 0L,
    /** "rm" o "all"; si cambia, el QC no compara contra el alcance anterior. */
    val scope: String = "all",
)

object StockFileCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(
        data: DemoStockData,
        generatedAt: String? = null,
        source: String? = null,
    ): String = json.encodeToString(
        StockFileDto(
            generatedAt = generatedAt,
            source = source,
            locales = data.locales.map { StockLocalDto(it.code, it.name, it.rutas) },
            marcas = data.marcas,
            items = data.items.map { item ->
                StockRowDto(
                    storeCode = item.storeCode,
                    storeName = item.storeName,
                    brand = item.brand,
                    product = item.product,
                    sku = item.sku,
                    stock = item.stockUnits,
                    transit = item.transitUnits,
                    date = item.snapshotDate,
                    quiebre = if (item.quiebre) 1 else 0,
                    days = item.daysOfInventory?.toDouble(),
                    saleWeek = item.saleLastWeek,
                    sale4w = item.saleLast4w,
                )
            },
        ),
    )

    fun decode(text: String): DemoStockData {
        val archivo = json.decodeFromString<StockFileDto>(text)
        return DemoStockData(
            items = archivo.items.map { it.toItem() },
            locales = archivo.locales.map { StockLocal(it.code, it.name, it.rutas) },
            marcas = archivo.marcas,
            generatedAt = archivo.generatedAt,
        )
    }

    fun encodeMeta(meta: StockMeta): String = json.encodeToString(meta)

    fun decodeMeta(text: String): StockMeta? =
        runCatching { json.decodeFromString<StockMeta>(text) }.getOrNull()
}

package com.rutamercaderistas.data.stock

import android.content.Context

/**
 * Catálogo completo de KPI One más las filas de stock cargadas desde assets
 * o desde el último snapshot generado por la app.
 */
data class DemoStockData(
    val items: List<StockItem> = emptyList(),
    val locales: List<StockLocal> = emptyList(),
    val marcas: List<String> = emptyList(),
    /** ISO-8601 de cuándo se generó el archivo; null en el asset de la APK. */
    val generatedAt: String? = null,
)

object StockDemoData {

    private const val ASSET = "stock/stock_demo.json"

    fun load(context: Context): DemoStockData = runCatching {
        context.assets.open(ASSET).bufferedReader().use { it.readText() }.let(::parse)
    }.getOrDefault(DemoStockData())

    fun parse(text: String): DemoStockData = StockFileCodec.decode(text)
}

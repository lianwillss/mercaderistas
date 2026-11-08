package com.rutamercaderistas

object Constants {
    const val PROMOTIONS_CSV_URL = "https://drive.usercontent.google.com/download?id=10fqnue63wMiraRjabc0euN5czXi5A563&export=download"
    const val PROMOTION_REFRESH_INTERVAL_HOURS = 12L

    // Respaldo: stock.json ya procesado (catálogo + filas). La vía principal
    // es StockBulkSync dentro de la app; esto se usa si KPI One falla.
    // Ejemplo Drive: https://drive.usercontent.google.com/download?id=<ID>&export=download
    const val STOCK_JSON_URL =
        "https://drive.usercontent.google.com/download?id=1_LRvsIM_gzsaACGfmdNaZkNNEET1dyXz&export=download"

    const val DRIVE_EXPORT_URL = "https://docs.google.com/spreadsheets/d/18PejQq99XzNyTDf0fFku0hHwMs2sG1X5/export?format=xlsx"

    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 60_000
    const val MAX_RETRIES = 3
    const val RETRY_BACKOFF_MS = 1_000L

    // Intervalo mínimo entre auto-chequeos del rutero al abrir la app.
    const val AUTO_SYNC_MIN_INTERVAL_MS = 15 * 60 * 1000L

    const val UPDATE_SUPPRESS_DAYS_MS = 86_400_000L
}

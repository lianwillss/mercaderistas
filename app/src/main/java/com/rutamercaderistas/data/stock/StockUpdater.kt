package com.rutamercaderistas.data.stock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Respaldo: baja un stock.json ya procesado (por ejemplo el que publica
 * Drive) y lo aplica. La vía principal es [StockBulkSync], que genera el
 * archivo dentro de la app.
 */
@Singleton
class StockUpdater @Inject constructor(
    private val repositorio: DemoStockRepository,
    private val storage: StockStorage,
) {
    private val cliente = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun actualizar(url: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.isNotBlank()) { "Falta la URL del archivo de stock" }
            val request = Request.Builder().url(url).get().build()
            val texto = cliente.newCall(request).execute().use { respuesta ->
                if (!respuesta.isSuccessful) error("El servidor respondió " + respuesta.code)
                respuesta.body?.string().orEmpty()
            }
            require(texto.isNotBlank()) { "El archivo vino vacío" }
            val datos = StockDemoData.parse(texto)
            require(datos.items.isNotEmpty()) { "El archivo no trae filas de stock" }
            require(datos.locales.isNotEmpty()) { "El archivo no trae locales" }
            storage.backup(StockBulkSync.STOCK_FILE)
            require(storage.writeAtomic(StockBulkSync.STOCK_FILE, texto)) {
                "No se pudo guardar el archivo de stock"
            }
            val meta = StockMeta(
                generatedAt = System.currentTimeMillis(),
                rows = datos.items.size,
                locales = datos.locales.size,
                maxSnapshot = datos.items.maxOfOrNull { it.snapshotDate },
                source = "drive",
            )
            storage.writeAtomic(StockBulkSync.META_FILE, StockFileCodec.encodeMeta(meta))
            repositorio.actualizarDatos(datos, StockOrigin.DRIVE)
            datos.items.size
        }
    }
}

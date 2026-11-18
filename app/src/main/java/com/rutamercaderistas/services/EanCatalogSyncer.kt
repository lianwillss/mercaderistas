package com.rutamercaderistas.services

import com.rutamercaderistas.data.local.EanProductDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deja el catálogo EAN al día sin depender de abrir la pantalla Cód. EAN.
 *
 * El catálogo crece con nuevos assets ean*.xlsx y con cada subida de
 * EAN_DATA_VERSION; este sincronizador aplica la misma detección que
 * EanSearchViewModel (sin datos, versión vieja, hash de assets distinto o
 * filas sin normalizar) y reimporta solo cuando hace falta.
 *
 * Lo usa el detalle de stock antes de resolver el código de barras de un SKU:
 * así un código EAN recién agregado queda enlazado automáticamente.
 */
@Singleton
class EanCatalogSyncer @Inject constructor(
    private val parser: EanExcelParser,
    private val dao: EanProductDao,
) {

    // Evita que dos aperturas seguidas del detalle disparen dos importaciones.
    // La escritura real ya está serializada dentro del parser.
    private val mutex = Mutex()

    /**
     * Garantiza que el catálogo esté importado y al día.
     * Devuelve la cantidad de productos del catálogo.
     */
    suspend fun ensure(): Result<Int> = mutex.withLock {
        val count = runCatching { dao.count() }.getOrDefault(0)
        val hasData = count > 0
        val currentHash = runCatching { parser.computeAssetsHash() }.getOrDefault("")
        val storedHash = runCatching { parser.getEanAssetsHash() }.getOrDefault("")
        val storedVersion = runCatching { parser.getEanDataVersion() }.getOrDefault(0)
        val sinNormalizar = runCatching { dao.hasUnnormalized() }.getOrDefault(0)

        val needsImport = !hasData ||
            storedVersion < EAN_DATA_VERSION ||
            (currentHash.isNotBlank() && storedHash != currentHash) ||
            sinNormalizar > 0

        if (!needsImport) return@withLock Result.success(count)

        parser.loadFromAssets().onSuccess {
            parser.setEanDataVersion(EAN_DATA_VERSION)
            if (currentHash.isNotBlank()) parser.setEanAssetsHash(currentHash)
        }
    }
}

package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.RouteEntryDao
import timber.log.Timber
import com.rutamercaderistas.data.local.toDomain
import com.rutamercaderistas.data.local.toEntities
import com.rutamercaderistas.models.EntradaRuta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.measureTimeMillis

/**
 * Arquitecto: RuteroManager
 * Encargado de la orquestación de datos: Indexación, Lazy Loading.
 * Cachea rutas en Room en vez de archivos JSON.
 */
class RuteroManager(
    private val context: Context,
    private val routeEntryDao: RouteEntryDao,
) {

    companion object {
        const val EXCEL_FILE_NAME = "master_rutero.xlsx"
    }

    val ruterosFlow: Flow<List<String>> = routeEntryDao.observeRuteros()

    private val parser = ExcelParser()
    private val syncMutex = Mutex()

    suspend fun <T> withSyncLock(block: suspend () -> T): T {
        syncMutex.lock()
        return try {
            block()
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Guarda el archivo Excel maestro asegurando que se reemplace el anterior.
     */
    suspend fun saveMasterExcel(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, EXCEL_FILE_NAME)
        val temp = File(context.filesDir, "$EXCEL_FILE_NAME.tmp")
        try {
            temp.writeBytes(bytes)
            if (temp.length() <= 0 || !isValidMasterExcel(temp)) {
                Timber.e("NEW_FILE_REJECTED: Excel vacío o con estructura inválida")
                return@withContext false
            }

            replaceFile(temp, target)
            Timber.d("NEW_FILE_SAVED: Archivo maestro guardado (%d bytes)", target.length())
            true
        } catch (e: Exception) {
            Timber.e(e, "Error guardando Excel maestro")
            false
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Indexación: Parsea el Excel y guarda todas las rutas en Room.
     */
    suspend fun createIndex(listener: ExcelParser.ProgressListener? = null): Boolean = withContext(Dispatchers.IO) {
        var success = false
        val time = measureTimeMillis {
            try {
                val file = File(context.filesDir, EXCEL_FILE_NAME)
                if (!file.exists()) {
                    Timber.e("INDEX_FAILED: No existe el archivo maestro para indexar")
                    return@measureTimeMillis
                }

                listener?.onProgress("Analizando Excel...", 5)
                val result = parser.parseAll(file, listener)

                if (result.isSuccess) {
                    val (ruteros, byRoute) = result.getOrThrow()
                    if (!isValidMasterData(ruteros, byRoute)) {
                        Timber.e("INDEX_FAILED: Excel sin rutas o columnas de datos válidas")
                        return@measureTimeMillis
                    }

                    val allEntities = byRoute.flatMap { (_, entries) ->
                        entries.toEntities()
                    }
                    routeEntryDao.deleteAllAndInsert(allEntities)

                    Timber.d("INDEX_CREATED: %d rutas, %d registros en Room", ruteros.size, allEntities.size)
                    success = true
                } else {
                    Timber.e("INDEX_FAILED: %s", result.exceptionOrNull()?.message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error creando índice")
            }
        }
        Timber.d("LOAD_TIME_MS (Indexación): %d ms", time)
        success
    }

    private suspend fun isValidMasterExcel(file: File): Boolean {
        val (ruteros, byRoute) = parser.parseAll(file).getOrNull() ?: return false
        return isValidMasterData(ruteros, byRoute)
    }

    private fun isValidMasterData(
        ruteros: List<String>,
        byRoute: Map<String, List<EntradaRuta>>,
    ): Boolean = isValidMasterImport(ruteros, byRoute.values.flatten())

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    /**
     * Carga la lista de ruteros desde Room.
     */
    suspend fun loadIndex(): List<String> = withContext(Dispatchers.IO) {
        routeEntryDao.getDistinctRuteros()
    }

    /**
     * Carga diferida: Obtiene los datos de una ruta desde Room.
     */
    suspend fun loadRoute(ruteroName: String, listener: ExcelParser.ProgressListener? = null): List<EntradaRuta> = withContext(Dispatchers.IO) {
        val fromDb = routeEntryDao.getEntriesForRoute(ruteroName)
        if (fromDb.isNotEmpty()) {
            Timber.d("ROOM_HIT: Ruta %s (%d registros)", ruteroName, fromDb.size)
            return@withContext fromDb.toDomain()
        }

        Timber.d("ROOM_MISS: Cargando ruta %s desde Excel", ruteroName)
        val file = File(context.filesDir, EXCEL_FILE_NAME)
        if (!file.exists()) return@withContext emptyList()

        val result = parser.parseSpecificRoute(file, ruteroName, listener)
        if (result.isSuccess) {
            val data = result.getOrThrow()
            // Cache to Room for next time
            routeEntryDao.insertAll(data.toEntities())
            Timber.d("ROUTE_LOADED: %s (%d registros)", ruteroName, data.size)
            return@withContext data
        }

        emptyList()
    }

    /**
     * Carga todas las entradas de la planilla desde Room (para detectar cambios).
     */
    suspend fun loadAllEntries(): List<EntradaRuta> = withContext(Dispatchers.IO) {
        routeEntryDao.getAllEntries().toDomain()
    }

}

internal fun isValidMasterImport(
    ruteros: List<String>,
    entries: List<EntradaRuta>,
): Boolean = ruteros.isNotEmpty() && entries.isNotEmpty() &&
    entries.any { it.local.isNotBlank() } &&
    entries.any { it.codigo.isNotBlank() } &&
    entries.any { it.cliente.isNotBlank() }

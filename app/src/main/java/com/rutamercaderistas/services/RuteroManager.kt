package com.rutamercaderistas.services

import android.content.Context
import com.rutamercaderistas.data.local.RouteEntryDao
import timber.log.Timber
import com.rutamercaderistas.data.local.toDomain
import com.rutamercaderistas.data.local.toEntities
import com.rutamercaderistas.models.EntradaRuta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
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

    /**
     * Guarda el archivo Excel maestro asegurando que se reemplace el anterior.
     */
    suspend fun saveMasterExcel(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, EXCEL_FILE_NAME)
            if (file.exists()) {
                file.delete()
                Timber.d("OLD_FILE_REMOVED: Archivo anterior eliminado")
            }

            file.writeBytes(bytes)

            if (file.exists() && file.length() > 0) {
                Timber.d("NEW_FILE_SAVED: Archivo maestro guardado (%d bytes)", file.length())
                true
            } else {
                Timber.e("ONEDRIVE_DOWNLOAD_FAILED: Archivo guardado está vacío o no existe")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error guardando Excel maestro")
            false
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

    suspend fun invalidateAllCaches() {
        routeEntryDao.deleteAll()
        Timber.d("CACHE_CLEARED: Toda la data en Room ha sido eliminada")
    }
}

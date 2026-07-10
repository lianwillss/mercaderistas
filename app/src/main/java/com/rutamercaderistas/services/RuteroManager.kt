package com.rutamercaderistas.services

import android.content.Context
import android.util.Log
import com.rutamercaderistas.data.local.RouteEntryDao
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

    private val TAG = "RuteroManager"
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
                Log.d(TAG, "OLD_FILE_REMOVED: Archivo anterior eliminado")
            }

            file.writeBytes(bytes)

            if (file.exists() && file.length() > 0) {
                Log.d(TAG, "NEW_FILE_SAVED: Archivo maestro guardado (${file.length()} bytes)")
                true
            } else {
                Log.e(TAG, "ONEDRIVE_DOWNLOAD_FAILED: Archivo guardado está vacío o no existe")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando Excel maestro", e)
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
                    Log.e(TAG, "INDEX_FAILED: No existe el archivo maestro para indexar")
                    return@measureTimeMillis
                }

                listener?.onProgress("Analizando Excel...", 5)
                val result = parser.parseAll(file, listener)

                if (result.isSuccess) {
                    val (ruteros, byRoute) = result.getOrThrow()

                    // Save all entries to Room
                    val allEntities = byRoute.flatMap { (_, entries) ->
                        entries.toEntities()
                    }
                    routeEntryDao.deleteAll()
                    routeEntryDao.insertAll(allEntities)

                    Log.d(TAG, "INDEX_CREATED: ${ruteros.size} rutas, ${allEntities.size} registros en Room")
                    success = true
                } else {
                    Log.e(TAG, "INDEX_FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creando índice", e)
            }
        }
        Log.d(TAG, "LOAD_TIME_MS (Indexación): $time ms")
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
            Log.d(TAG, "ROOM_HIT: Ruta $ruteroName (${fromDb.size} registros)")
            return@withContext fromDb.toDomain()
        }

        Log.d(TAG, "ROOM_MISS: Cargando ruta $ruteroName desde Excel")
        val file = File(context.filesDir, EXCEL_FILE_NAME)
        if (!file.exists()) return@withContext emptyList()

        val result = parser.parseSpecificRoute(file, ruteroName, listener)
        if (result.isSuccess) {
            val data = result.getOrThrow()
            // Cache to Room for next time
            routeEntryDao.insertAll(data.toEntities())
            Log.d(TAG, "ROUTE_LOADED: $ruteroName (${data.size} registros)")
            return@withContext data
        }

        emptyList()
    }

    /**
     * Elimina toda la caché de rutas en Room.
     */
    suspend fun invalidateAllCaches() {
        routeEntryDao.deleteAll()
        Log.d(TAG, "CACHE_CLEARED: Toda la data en Room ha sido eliminada")
    }
}

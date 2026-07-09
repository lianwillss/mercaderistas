package com.rutamercaderistas.services

import android.content.Context
import android.util.Log
import com.rutamercaderistas.models.EntradaRuta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Arquitecto: RuteroManager
 * Encargado de la orquestación de datos: Indexación, Lazy Loading y Caché por Ruta.
 * Optimizado para invalidación total de caché durante actualizaciones.
 */
class RuteroManager(private val context: Context) {

    private val TAG = "RuteroManager"
    companion object {
        const val EXCEL_FILE_NAME = "master_rutero.xlsx"
    }
    private val INDEX_FILE_NAME = "rutero_index.json"
    
    private val _ruterosFlow = MutableStateFlow<List<String>>(emptyList())
    val ruterosFlow: StateFlow<List<String>> = _ruterosFlow.asStateFlow()

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
     * Indexación rápida: Recorre el Excel para extraer los nombres de las rutas.
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
                    saveIndex(ruteros)

                    // Pre-cache every route so loadRoute never needs to re-parse
                    byRoute.forEach { (name, entries) ->
                        saveToRouteCache(name, entries)
                    }

                    _ruterosFlow.value = ruteros
                    Log.d(TAG, "INDEX_CREATED: ${ruteros.size} rutas, ${byRoute.values.sumOf { it.size }} registros")
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

    private fun saveIndex(ruteros: List<String>) {
        val json = JSONObject()
        json.put("ruteros", org.json.JSONArray(ruteros))
        json.put("updated_at", System.currentTimeMillis())
        File(context.filesDir, INDEX_FILE_NAME).writeText(json.toString())
    }

    fun loadIndex(): List<String> {
        val file = File(context.filesDir, INDEX_FILE_NAME)
        if (!file.exists()) return emptyList()
        
        return try {
            val json = JSONObject(file.readText())
            val array = json.getJSONArray("ruteros")
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            _ruterosFlow.value = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * CARGA DIFERIDA: Solo carga los datos de una ruta específica.
     */
    suspend fun loadRoute(ruteroName: String, listener: ExcelParser.ProgressListener? = null): List<EntradaRuta> = withContext(Dispatchers.IO) {
        // 1. Intentar cargar desde Caché de Ruta
        val cached = loadFromRouteCache(ruteroName)
        if (cached != null) {
            Log.d(TAG, "CACHE_HIT: Ruta $ruteroName cargada desde caché")
            return@withContext cached
        }

        Log.d(TAG, "CACHE_MISS: Cargando ruta $ruteroName desde Excel")
        val file = File(context.filesDir, EXCEL_FILE_NAME)
        if (!file.exists()) return@withContext emptyList()

        val result = parser.parseSpecificRoute(file, ruteroName, listener)
        if (result.isSuccess) {
            val data = result.getOrThrow()
            saveToRouteCache(ruteroName, data)
            Log.d(TAG, "ROUTE_LOADED: $ruteroName (${data.size} registros)")
            return@withContext data
        }
        
        emptyList()
    }

    private fun cacheFileName(ruteroName: String): String =
        "cache_route_${ruteroName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.json"

    private fun saveToRouteCache(ruteroName: String, data: List<EntradaRuta>) {
        try {
            val cacheFile = File(context.cacheDir, cacheFileName(ruteroName))
            val json = JSONObject()
            val array = org.json.JSONArray()
            data.forEach { entry ->
                val obj = JSONObject().apply {
                    put("rutero", entry.rutero)
                    put("codigo", entry.codigo)
                    put("local", entry.local)
                    put("direccion", entry.direccion)
                    put("cliente", entry.cliente)
                    put("reponedor", entry.reponedor)
                    put("cadena", entry.cadena)
                    put("formato", entry.formato)
                    put("region", entry.region)
                    put("comuna", entry.comuna)
                    put("supervisor", entry.supervisor)
                    put("gestores", entry.gestores)
                    put("modalidad", entry.modalidad)
                    put("equipo", entry.equipo)
                    put("lunes", entry.lunes)
                    put("martes", entry.martes)
                    put("miercoles", entry.miercoles)
                    put("jueves", entry.jueves)
                    put("viernes", entry.viernes)
                    put("sabado", entry.sabado)
                    put("domingo", entry.domingo)
                }
                array.put(obj)
            }
            json.put("data", array)
            cacheFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando caché de ruta", e)
        }
    }

    private fun loadFromRouteCache(ruteroName: String): List<EntradaRuta>? {
        val cacheFile = File(context.cacheDir, cacheFileName(ruteroName))
        if (!cacheFile.exists()) return null
        
        val masterFile = File(context.filesDir, EXCEL_FILE_NAME)
        if (masterFile.exists() && cacheFile.lastModified() < masterFile.lastModified()) {
            Log.d(TAG, "CACHE_INVALIDATED: Caché de $ruteroName expirado")
            cacheFile.delete()
            return null
        }

        return try {
            val json = JSONObject(cacheFile.readText())
            val array = json.getJSONArray("data")
            val list = mutableListOf<EntradaRuta>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(EntradaRuta(
                    reponedor = obj.optString("reponedor", ""),
                    rutero = obj.getString("rutero"),
                    codigo = obj.getString("codigo"),
                    local = obj.getString("local"),
                    direccion = obj.getString("direccion"),
                    cliente = obj.getString("cliente"),
                    cadena = obj.optString("cadena", ""),
                    formato = obj.optString("formato", ""),
                    region = obj.optString("region", ""),
                    comuna = obj.optString("comuna", ""),
                    supervisor = obj.optString("supervisor", ""),
                    gestores = obj.optString("gestores", ""),
                    modalidad = obj.optString("modalidad", ""),
                    equipo = obj.optString("equipo", ""),
                    lunes = obj.getBoolean("lunes"),
                    martes = obj.getBoolean("martes"),
                    miercoles = obj.getBoolean("miercoles"),
                    jueves = obj.getBoolean("jueves"),
                    viernes = obj.getBoolean("viernes"),
                    sabado = obj.getBoolean("sabado"),
                    domingo = obj.optBoolean("domingo", false)
                ))
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Elimina absolutamente toda la caché local de rutas.
     */
    fun invalidateAllCaches() {
        // Eliminar archivos de caché de rutas
        context.cacheDir.listFiles { _, name -> name.startsWith("cache_route_") }?.forEach { it.delete() }
        
        // Eliminar índice
        val indexFile = File(context.filesDir, INDEX_FILE_NAME)
        if (indexFile.exists()) indexFile.delete()
        
        // Limpiar flujo de ruteros
        _ruterosFlow.value = emptyList()
        
        Log.d(TAG, "CACHE_CLEARED: Toda la caché y el índice han sido eliminados")
    }
}

package com.rutamercaderistas.data.stock

import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.data.preferences.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resuelve y persiste el alcance de Stock.
 *
 * La persona sale del rutero activo (el que el usuario ya eligió en la pestaña
 * Ruta). Si todavía no hay ninguno, se usa el primero indexado. El alcance se
 * guarda en DataStore para que la próxima apertura abra en el mismo lugar.
 */
@Singleton
class StockScopeStore @Inject constructor(
    private val routeDao: RouteEntryDao,
    private val prefs: PreferencesRepository,
    private val storage: StockStorage,
) {

    suspend fun cargar(): StockAlcance = withContext(Dispatchers.IO) {
        resolver(StockScope.leer(prefs.getStockScope()))
    }

    suspend fun cambiar(scope: StockScope): StockAlcance = withContext(Dispatchers.IO) {
        prefs.setStockScope(scope.name)
        resolver(scope)
    }

    /**
     * ¿El snapshot en disco ya cubre este alcance? El asset de la APK no trae
     * `stock-meta.json`, pero es la RM completa.
     */
    fun snapshotCubre(alcance: StockAlcance): Boolean {
        if (alcance.scope == StockScope.RM_COMPLETA) {
            val meta = leerMeta() ?: return true
            return meta.scope == StockAlcance.SCOPE_RM ||
                meta.scope == StockAlcance.SCOPE_ALL
        }
        if (alcance.rutas.isEmpty()) return true
        val meta = leerMeta()
        // Si no hay meta (asset de la APK), el snapshot es RM completa y NO
        // cubre "Mis rutas": hay que bajar solo las rutas de la persona.
        if (meta == null) return false
        // La RM ya contiene todas las rutas: sirve para "Mis rutas" sin bajar
        // de nuevo. Lo contrario no: un snapshot acotado no cubre la RM.
        return meta.scope == alcance.claveSnapshot ||
            meta.scope == StockAlcance.SCOPE_RM ||
            meta.scope == StockAlcance.SCOPE_ALL
    }

    private suspend fun resolver(scope: StockScope): StockAlcance {
        if (scope == StockScope.RM_COMPLETA) return StockAlcance(scope)
        val ruteros = routeDao.getDistinctRuteros()
        val rutero = prefs.getSelectedRoute()
            ?.takeIf { elegido -> ruteros.any { it.equals(elegido, ignoreCase = true) } }
            ?: ruteros.firstOrNull()
            ?: return StockAlcance(scope)
        val rutas = rutasDeLaPersona(routeDao.getAllEntries(), rutero)
        return StockAlcance(scope = scope, rutas = rutas)
    }

    private fun leerMeta(): StockMeta? =
        storage.read(StockBulkSync.META_FILE)
            ?.let { StockFileCodec.decodeMeta(it) }
}

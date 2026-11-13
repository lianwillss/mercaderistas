package com.rutamercaderistas.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SyncHistoryEntry(
    val timestamp: Long,
    val summary: String,
)

data class PendingUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    /** Tag completo ("v12.20.1"): versionCode colisiona en patch, el tag no. */
    val versionTag: String = "",
    /** Bytes publicados en el release (0 = pendiente viejo sin verificación). */
    val apkSize: Long = 0L,
    /** SHA-256 publicado por el CI ("" = pendiente viejo sin verificación). */
    val apkSha256: String = "",
)

val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mercaderistas_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_SELECTED_ROUTE = stringPreferencesKey("selected_rutero")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync_time")
        private val KEY_UPDATE_SUPPRESSED_UNTIL = longPreferencesKey("update_suppressed_until")
        private val KEY_DRIVE_URL = stringPreferencesKey("drive_url")
        private val KEY_TRANSPORT_MODE = stringPreferencesKey("transport_mode")
        private val KEY_LAST_VERSION_CODE = intPreferencesKey("last_version_code")
        internal val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_STOCK_LOCAL = stringPreferencesKey("stock_local")
        private val KEY_STOCK_MARCA = stringPreferencesKey("stock_marca")
        private val KEY_STOCK_RUTA = stringPreferencesKey("stock_ruta")
        private val KEY_STOCK_SKU = stringPreferencesKey("stock_sku")
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("ean_search_history")
        val KEY_LOCALES_SEARCH_HISTORY = stringPreferencesKey("locales_search_history")
        val KEY_ONBOARDING_DONE = stringPreferencesKey("onboarding_done")
        private val KEY_LAST_SYNC_ETAG = stringPreferencesKey("last_sync_etag")
        private val KEY_LAST_SYNC_HASH = stringPreferencesKey("last_sync_hash")
        private val KEY_LAST_SYNC_CHECK = longPreferencesKey("last_sync_check")
        private val KEY_LAST_PROMO_REFRESH = longPreferencesKey("last_promo_refresh")
        private val KEY_PENDING_UPDATE_NAME = stringPreferencesKey("pending_update_name")
        private val KEY_PENDING_UPDATE_CODE = intPreferencesKey("pending_update_code")
        private val KEY_PENDING_UPDATE_URL = stringPreferencesKey("pending_update_url")
        private val KEY_PENDING_UPDATE_TAG = stringPreferencesKey("pending_update_tag")
        private val KEY_PENDING_UPDATE_SIZE = longPreferencesKey("pending_update_size")
        private val KEY_PENDING_UPDATE_SHA = stringPreferencesKey("pending_update_sha")
        /**
         * versionCode para el que ya se intentó instalar sola al salir de la
         * app. Evita que un arranque en frío vuelva a lanzar la instalación.
         */
        private val KEY_UPDATE_INSTALL_ATTEMPTED_CODE =
            intPreferencesKey("update_install_attempted_code")
        /** Versión nueva detectada al arrancar: se muestra una vez y se borra. */
        private val KEY_PENDING_UPGRADE_NOTICE = stringPreferencesKey("pending_upgrade_notice")
        /** Última pestaña principal elegida, para restaurarla tras actualizar. */
        private val KEY_LAST_MAIN_TAB = stringPreferencesKey("last_main_tab")
        /** Sesión de PackageInstaller commiteada, para no duplicarla al reabrir. */
        private val KEY_INSTALL_SESSION_ID = intPreferencesKey("install_session_id")
        /**
         * Tag del release que trajo un APK no instalable (versión anunciada
         * distinta o no más nueva). No se vuelve a ofrecer ese tag.
         */
        private val KEY_REJECTED_UPDATE_TAG = stringPreferencesKey("rejected_update_tag")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        private val KEY_SYNC_HISTORY = stringPreferencesKey("sync_history")
        private const val SEARCH_HISTORY_MAX = 8
        private const val LOCALES_SEARCH_HISTORY_MAX = 8
        private const val SYNC_HISTORY_MAX = 20
    }

    suspend fun getSelectedRoute(): String? =
        context.prefsDataStore.data.first()[KEY_SELECTED_ROUTE]

    suspend fun setSelectedRoute(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_SELECTED_ROUTE)
            else prefs[KEY_SELECTED_ROUTE] = value
        }
    }

    suspend fun getStockLocal(): String =
        context.prefsDataStore.data.first()[KEY_STOCK_LOCAL] ?: ""

    suspend fun getStockMarca(): String =
        context.prefsDataStore.data.first()[KEY_STOCK_MARCA] ?: ""

    suspend fun getStockRuta(): String =
        context.prefsDataStore.data.first()[KEY_STOCK_RUTA] ?: ""

    suspend fun getStockSku(): String =
        context.prefsDataStore.data.first()[KEY_STOCK_SKU] ?: ""

    suspend fun setStockFilters(
        local: String,
        marca: String,
        ruta: String = "",
        sku: String = "",
    ) {
        context.prefsDataStore.edit { prefs ->
            if (local.isBlank()) prefs.remove(KEY_STOCK_LOCAL) else prefs[KEY_STOCK_LOCAL] = local
            if (marca.isBlank()) prefs.remove(KEY_STOCK_MARCA) else prefs[KEY_STOCK_MARCA] = marca
            if (ruta.isBlank()) prefs.remove(KEY_STOCK_RUTA) else prefs[KEY_STOCK_RUTA] = ruta
            if (sku.isBlank()) prefs.remove(KEY_STOCK_SKU) else prefs[KEY_STOCK_SKU] = sku
        }
    }

    suspend fun getLastSyncTime(): Long =
        context.prefsDataStore.data.first()[KEY_LAST_SYNC] ?: 0L

    suspend fun setLastSyncTime(value: Long) {
        context.prefsDataStore.edit { it[KEY_LAST_SYNC] = value }
    }

    suspend fun getUpdateSuppressedUntil(): Long =
        context.prefsDataStore.data.first()[KEY_UPDATE_SUPPRESSED_UNTIL] ?: 0L

    suspend fun setUpdateSuppressedUntil(value: Long) {
        context.prefsDataStore.edit { it[KEY_UPDATE_SUPPRESSED_UNTIL] = value }
    }

    suspend fun getDriveUrl(): String? =
        context.prefsDataStore.data.first()[KEY_DRIVE_URL]

    suspend fun setDriveUrl(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_DRIVE_URL)
            else prefs[KEY_DRIVE_URL] = value
        }
    }

    suspend fun getTransportMode(): String? =
        context.prefsDataStore.data.first()[KEY_TRANSPORT_MODE]

    fun getTransportModeFlow(): Flow<String?> =
        context.prefsDataStore.data.map { it[KEY_TRANSPORT_MODE] }

    suspend fun setTransportMode(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_TRANSPORT_MODE)
            else prefs[KEY_TRANSPORT_MODE] = value
        }
    }

    suspend fun getLastVersionCode(): Int =
        context.prefsDataStore.data.first()[KEY_LAST_VERSION_CODE] ?: 0

    suspend fun setLastVersionCode(value: Int) {
        context.prefsDataStore.edit { it[KEY_LAST_VERSION_CODE] = value }
    }

    fun getFontScaleFlow(): Flow<Float> =
        context.prefsDataStore.data.map { it[KEY_FONT_SCALE] ?: 1f }

    suspend fun setFontScale(value: Float) {
        context.prefsDataStore.edit { it[KEY_FONT_SCALE] = value.coerceIn(0.8f, 1.8f) }
    }

    fun getSearchHistoryFlow(): Flow<List<String>> =
        context.prefsDataStore.data.map { prefs ->
            prefs[KEY_SEARCH_HISTORY]?.let { raw ->
                runCatching { JSONArray(raw) }
                    .getOrElse { JSONArray() }
                    .let { arr -> List(arr.length()) { i -> arr.getString(i) } }
            } ?: emptyList()
        }

    suspend fun addSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        context.prefsDataStore.edit { prefs ->
            val existing = prefs[KEY_SEARCH_HISTORY]
                ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
            val list = (0 until existing.length()).map { existing.getString(it) }.toMutableList()
            list.remove(q)
            list.add(0, q)
            val trimmed = list.take(SEARCH_HISTORY_MAX)
            val next = JSONArray()
            trimmed.forEach { next.put(it) }
            prefs[KEY_SEARCH_HISTORY] = next.toString()
        }
    }

    suspend fun clearSearchHistory() {
        context.prefsDataStore.edit { it.remove(KEY_SEARCH_HISTORY) }
    }

    fun getLocalesSearchHistoryFlow(): Flow<List<String>> =
        context.prefsDataStore.data.map { prefs ->
            prefs[KEY_LOCALES_SEARCH_HISTORY]?.let { raw ->
                runCatching { JSONArray(raw) }
                    .getOrElse { JSONArray() }
                    .let { arr -> List(arr.length()) { i -> arr.getString(i) } }
            } ?: emptyList()
        }

    suspend fun addLocalesSearchQuery(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        context.prefsDataStore.edit { prefs ->
            val existing = prefs[KEY_LOCALES_SEARCH_HISTORY]
                ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
            val list = (0 until existing.length()).map { existing.getString(it) }.toMutableList()
            list.remove(q)
            list.add(0, q)
            val trimmed = list.take(LOCALES_SEARCH_HISTORY_MAX)
            val next = JSONArray()
            trimmed.forEach { next.put(it) }
            prefs[KEY_LOCALES_SEARCH_HISTORY] = next.toString()
        }
    }

    suspend fun clearLocalesSearchHistory() {
        context.prefsDataStore.edit { it.remove(KEY_LOCALES_SEARCH_HISTORY) }
    }

    suspend fun isOnboardingDone(): Boolean =
        context.prefsDataStore.data.first()[KEY_ONBOARDING_DONE] != null

    suspend fun setOnboardingDone() {
        context.prefsDataStore.edit { it[KEY_ONBOARDING_DONE] = "true" }
    }

    suspend fun getLastSyncETag(): String? =
        context.prefsDataStore.data.first()[KEY_LAST_SYNC_ETAG]

    suspend fun setLastSyncETag(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_LAST_SYNC_ETAG)
            else prefs[KEY_LAST_SYNC_ETAG] = value
        }
    }

    suspend fun getLastSyncHash(): String? =
        context.prefsDataStore.data.first()[KEY_LAST_SYNC_HASH]

    suspend fun setLastSyncHash(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_LAST_SYNC_HASH)
            else prefs[KEY_LAST_SYNC_HASH] = value
        }
    }

    /** Última vez que se revisó el Excel (manual, auto o worker), haya cambios o no. */
    suspend fun getLastSyncCheck(): Long =
        context.prefsDataStore.data.first()[KEY_LAST_SYNC_CHECK] ?: 0L

    suspend fun setLastSyncCheck(value: Long) {
        context.prefsDataStore.edit { it[KEY_LAST_SYNC_CHECK] = value }
    }

    fun getLastSyncCheckFlow(): Flow<Long> =
        context.prefsDataStore.data.map { it[KEY_LAST_SYNC_CHECK] ?: 0L }

    suspend fun getLastPromoRefresh(): Long =
        context.prefsDataStore.data.first()[KEY_LAST_PROMO_REFRESH] ?: 0L

    suspend fun setLastPromoRefresh(value: Long) {
        context.prefsDataStore.edit { it[KEY_LAST_PROMO_REFRESH] = value }
    }

    fun getSyncHistoryFlow(): Flow<List<SyncHistoryEntry>> =
        context.prefsDataStore.data.map { prefs ->
            prefs[KEY_SYNC_HISTORY]?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    List(arr.length()) { i ->
                        val obj = arr.getJSONObject(i)
                        SyncHistoryEntry(
                            timestamp = obj.optLong("ts"),
                            summary = obj.optString("summary"),
                        )
                    }
                }.getOrElse { emptyList() }
            } ?: emptyList()
        }

    suspend fun addSyncHistoryEntry(entry: SyncHistoryEntry) {
        context.prefsDataStore.edit { prefs ->
            val existing = prefs[KEY_SYNC_HISTORY]
                ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
            val next = JSONArray()
            next.put(JSONObject().put("ts", entry.timestamp).put("summary", entry.summary))
            for (i in 0 until minOf(existing.length(), SYNC_HISTORY_MAX - 1)) {
                next.put(existing.get(i))
            }
            prefs[KEY_SYNC_HISTORY] = next.toString()
        }
    }

    suspend fun clearSyncHistory() {
        context.prefsDataStore.edit { it.remove(KEY_SYNC_HISTORY) }
    }

    suspend fun setPendingUpdate(entry: PendingUpdate) {
        context.prefsDataStore.edit { prefs ->
            prefs[KEY_PENDING_UPDATE_NAME] = entry.versionName
            prefs[KEY_PENDING_UPDATE_CODE] = entry.versionCode
            prefs[KEY_PENDING_UPDATE_URL] = entry.apkUrl
            if (entry.versionTag.isNotBlank()) prefs[KEY_PENDING_UPDATE_TAG] = entry.versionTag
            else prefs.remove(KEY_PENDING_UPDATE_TAG)
            if (entry.apkSize > 0L) prefs[KEY_PENDING_UPDATE_SIZE] = entry.apkSize
            else prefs.remove(KEY_PENDING_UPDATE_SIZE)
            if (entry.apkSha256.isNotBlank()) prefs[KEY_PENDING_UPDATE_SHA] = entry.apkSha256
            else prefs.remove(KEY_PENDING_UPDATE_SHA)
        }
    }

    suspend fun getPendingUpdate(): PendingUpdate? {
        val prefs = context.prefsDataStore.data.first()
        val code = prefs[KEY_PENDING_UPDATE_CODE] ?: return null
        if (code <= 0) return null
        return PendingUpdate(
            versionName = prefs[KEY_PENDING_UPDATE_NAME].orEmpty(),
            versionCode = code,
            apkUrl = prefs[KEY_PENDING_UPDATE_URL].orEmpty(),
            versionTag = prefs[KEY_PENDING_UPDATE_TAG].orEmpty(),
            apkSize = prefs[KEY_PENDING_UPDATE_SIZE] ?: 0L,
            apkSha256 = prefs[KEY_PENDING_UPDATE_SHA].orEmpty(),
        )
    }

    suspend fun clearPendingUpdate() {
        context.prefsDataStore.edit { prefs ->
            prefs.remove(KEY_PENDING_UPDATE_NAME)
            prefs.remove(KEY_PENDING_UPDATE_CODE)
            prefs.remove(KEY_PENDING_UPDATE_URL)
            prefs.remove(KEY_PENDING_UPDATE_TAG)
            prefs.remove(KEY_PENDING_UPDATE_SIZE)
            prefs.remove(KEY_PENDING_UPDATE_SHA)
            prefs.remove(KEY_UPDATE_INSTALL_ATTEMPTED_CODE)
            prefs.remove(KEY_INSTALL_SESSION_ID)
        }
    }

    suspend fun getUpdateInstallAttemptedCode(): Int =
        context.prefsDataStore.data.first()[KEY_UPDATE_INSTALL_ATTEMPTED_CODE] ?: 0

    suspend fun setUpdateInstallAttemptedCode(versionCode: Int) {
        context.prefsDataStore.edit { it[KEY_UPDATE_INSTALL_ATTEMPTED_CODE] = versionCode }
    }

    suspend fun clearUpdateInstallAttemptedCode() {
        context.prefsDataStore.edit { it.remove(KEY_UPDATE_INSTALL_ATTEMPTED_CODE) }
    }

    suspend fun getInstallSessionId(): Int =
        context.prefsDataStore.data.first()[KEY_INSTALL_SESSION_ID] ?: 0

    suspend fun setInstallSessionId(value: Int) {
        context.prefsDataStore.edit { it[KEY_INSTALL_SESSION_ID] = value }
    }

    suspend fun clearInstallSessionId() {
        context.prefsDataStore.edit { it.remove(KEY_INSTALL_SESSION_ID) }
    }

    val pendingUpgradeNotice: Flow<String?> =
        context.prefsDataStore.data.map { it[KEY_PENDING_UPGRADE_NOTICE] }

    suspend fun setPendingUpgradeNotice(versionName: String) {
        context.prefsDataStore.edit { it[KEY_PENDING_UPGRADE_NOTICE] = versionName }
    }

    suspend fun clearPendingUpgradeNotice() {
        context.prefsDataStore.edit { it.remove(KEY_PENDING_UPGRADE_NOTICE) }
    }

    val lastMainTab: Flow<String?> =
        context.prefsDataStore.data.map { it[KEY_LAST_MAIN_TAB] }

    suspend fun setLastMainTab(value: String) {
        context.prefsDataStore.edit { it[KEY_LAST_MAIN_TAB] = value }
    }

    /** Tag publicado cuyo APK no se puede instalar sobre esta versión. */
    suspend fun getRejectedUpdateTag(): String =
        context.prefsDataStore.data.first()[KEY_REJECTED_UPDATE_TAG].orEmpty()

    suspend fun setRejectedUpdateTag(tag: String) {
        context.prefsDataStore.edit { prefs ->
            if (tag.isBlank()) prefs.remove(KEY_REJECTED_UPDATE_TAG)
            else prefs[KEY_REJECTED_UPDATE_TAG] = tag
        }
    }

    /** Última consulta exitosa a GitHub; se usa para no repetir al volver. */
    suspend fun getLastUpdateCheck(): Long =
        context.prefsDataStore.data.first()[KEY_LAST_UPDATE_CHECK] ?: 0L

    suspend fun setLastUpdateCheck(value: Long) {
        context.prefsDataStore.edit { it[KEY_LAST_UPDATE_CHECK] = value }
    }
}

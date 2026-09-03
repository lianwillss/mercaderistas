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
import javax.inject.Inject
import javax.inject.Singleton

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
        private val KEY_SEARCH_HISTORY = stringPreferencesKey("ean_search_history")
        val KEY_LOCALES_SEARCH_HISTORY = stringPreferencesKey("locales_search_history")
        val KEY_ONBOARDING_DONE = stringPreferencesKey("onboarding_done")
        private val KEY_LAST_SYNC_ETAG = stringPreferencesKey("last_sync_etag")
        private val KEY_LAST_SYNC_HASH = stringPreferencesKey("last_sync_hash")
        private const val SEARCH_HISTORY_MAX = 8
        private const val LOCALES_SEARCH_HISTORY_MAX = 8
    }

    suspend fun getSelectedRoute(): String? =
        context.prefsDataStore.data.first()[KEY_SELECTED_ROUTE]

    suspend fun setSelectedRoute(value: String?) {
        context.prefsDataStore.edit { prefs ->
            if (value == null) prefs.remove(KEY_SELECTED_ROUTE)
            else prefs[KEY_SELECTED_ROUTE] = value
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
}

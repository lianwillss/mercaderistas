package com.rutamercaderistas.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
}

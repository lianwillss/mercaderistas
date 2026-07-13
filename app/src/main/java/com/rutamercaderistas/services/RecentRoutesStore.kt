package com.rutamercaderistas.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.recentRoutesDataStore: DataStore<Preferences> by preferencesDataStore(name = "recent_routes")

class RecentRoutesStore(private val context: Context) {

    companion object {
        private val RECENT_ROUTES_KEY = stringPreferencesKey("recent_routes_json")
        private const val MAX_RECENTES = 5
    }

    val recentRoutesFlow: Flow<List<String>> = context.recentRoutesDataStore.data.map { prefs ->
        val json = prefs[RECENT_ROUTES_KEY] ?: return@map emptyList()
        try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun addRoute(rutero: String) {
        context.recentRoutesDataStore.edit { prefs ->
            val current = prefs[RECENT_ROUTES_KEY]?.let { json ->
                try {
                    val arr = org.json.JSONArray(json)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) { null }
            } ?: emptyList()
            val updated = current.toMutableList().apply {
                remove(rutero)
                add(0, rutero)
            }.take(MAX_RECENTES)
            prefs[RECENT_ROUTES_KEY] = org.json.JSONArray(updated).toString()
        }
    }

    suspend fun getRoutes(): List<String> = recentRoutesFlow.first()
}

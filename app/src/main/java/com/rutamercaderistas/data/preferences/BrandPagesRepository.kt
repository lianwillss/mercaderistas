package com.rutamercaderistas.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

val Context.brandPagesDataStore: DataStore<Preferences> by preferencesDataStore(name = "brand_pages")

@Singleton
class BrandPagesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun getAll(): Map<String, Int> {
        val prefs = context.brandPagesDataStore.data.first()
        val result = mutableMapOf<String, Int>()
        for ((key, value) in prefs.asMap()) {
            if (value is Int) {
                result[key.name] = value
            }
        }
        return result
    }

    suspend fun set(normalizedName: String, page: Int) {
        context.brandPagesDataStore.edit { prefs ->
            prefs[intPreferencesKey(normalizedName)] = page
        }
    }
}

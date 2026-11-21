package com.rutamercaderistas.data.stock

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rutamercaderistas.data.preferences.prefsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockSearchHistory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val busquedas: Flow<StockBusquedas> =
        context.prefsDataStore.data.map { prefs ->
            prefs[KEY_BUSQUEDAS]
                ?.let { raw -> runCatching { json.decodeFromString<StockBusquedas>(raw) }.getOrNull() }
                ?: StockBusquedas()
        }

    suspend fun registrar(tipo: TipoBusquedaStock, valor: String) {
        if (valor.isBlank()) return
        val ahora = System.currentTimeMillis()
        context.prefsDataStore.edit { prefs ->
            val actual = prefs[KEY_BUSQUEDAS]
                ?.let { raw ->
                    runCatching { json.decodeFromString<StockBusquedas>(raw) }.getOrNull()
                }
                ?: StockBusquedas()
            prefs[KEY_BUSQUEDAS] = json.encodeToString(
                registrarSeleccion(actual, tipo, valor, ahora),
            )
        }
    }

    private companion object {
        val KEY_BUSQUEDAS = stringPreferencesKey("stock_busquedas_v1")
    }
}

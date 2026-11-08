package com.rutamercaderistas.data.stock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Códigos de local de la Región Metropolitana. */
fun interface RmCodeProvider {
    fun codes(): Set<String>
}

/**
 * Lista curada de 314 códigos RM. Se usa cuando el rutero no trae la columna
 * REGION (hoy viene vacía) para no descargar los 790 locales del país.
 */
@Singleton
class AssetRmCodeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : RmCodeProvider {

    private val cache: Set<String> by lazy {
        runCatching {
            context.assets.open(ASSET).bufferedReader().useLines { lineas ->
                lineas.map { it.trim().uppercase() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }

    override fun codes(): Set<String> = cache

    private companion object {
        const val ASSET = "stock/locales_rm.txt"
    }
}

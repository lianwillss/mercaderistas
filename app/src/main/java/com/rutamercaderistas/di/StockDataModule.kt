package com.rutamercaderistas.di

import android.content.Context
import com.rutamercaderistas.data.stock.DemoStockData
import com.rutamercaderistas.data.stock.StockDemoData
import com.rutamercaderistas.data.stock.StockFileCodec
import com.rutamercaderistas.data.stock.StockOrigin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** La pantalla se nutre del snapshot local (asset o archivo generado). */
@Module
@InstallIn(SingletonComponent::class)
object StockDataModule {

    @Provides
    @Singleton
    fun provideStockOrigin(@ApplicationContext context: Context): StockOrigin {
        if (!File(context.filesDir, "stock/stock.json").exists()) return StockOrigin.ASSET
        val meta = File(context.filesDir, "stock/stock-meta.json")
        val source = if (meta.exists()) {
            runCatching { StockFileCodec.decodeMeta(meta.readText())?.source }.getOrNull()
        } else {
            null
        }
        return if (source == "drive") StockOrigin.DRIVE else StockOrigin.KPI_ONE
    }

    @Provides
    @Singleton
    fun provideDemoStockData(@ApplicationContext context: Context): DemoStockData {
        // Si ya se generó o descargó un snapshot, ese archivo manda sobre el de la APK.
        val actualizado = File(context.filesDir, "stock/stock.json")
        if (actualizado.exists()) {
            runCatching { StockDemoData.parse(actualizado.readText()) }
                .getOrNull()
                ?.takeIf { it.items.isNotEmpty() }
                ?.let { return it }
        }
        return StockDemoData.load(context)
    }
}

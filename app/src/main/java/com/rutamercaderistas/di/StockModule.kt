package com.rutamercaderistas.di

import com.rutamercaderistas.data.stock.AssetRmCodeProvider
import com.rutamercaderistas.data.stock.DemoStockRepository
import com.rutamercaderistas.data.stock.RmCodeProvider
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import com.rutamercaderistas.data.stock.remote.SecureCookieStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * La pantalla usa el archivo local (asset o snapshot generado). El cliente
 * remoto y la descarga masiva comparten [KpiSessionStore].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StockModule {

    @Binds
    @Singleton
    abstract fun bindStockRepository(impl: DemoStockRepository): StockRepository

    @Binds
    @Singleton
    abstract fun bindKpiSessionStore(impl: SecureCookieStore): KpiSessionStore

    @Binds
    @Singleton
    abstract fun bindRmCodeProvider(impl: AssetRmCodeProvider): RmCodeProvider
}

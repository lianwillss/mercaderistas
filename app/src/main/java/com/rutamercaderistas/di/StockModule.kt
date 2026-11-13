package com.rutamercaderistas.di

import com.rutamercaderistas.data.stock.AndroidNetworkMonitor
import com.rutamercaderistas.data.stock.AssetRmCodeProvider
import com.rutamercaderistas.data.stock.NetworkMonitor
import com.rutamercaderistas.data.stock.RmCodeProvider
import com.rutamercaderistas.data.stock.StockRepository
import com.rutamercaderistas.data.stock.StockRepositoryRouter
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import com.rutamercaderistas.data.stock.remote.SecureCookieStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * La pantalla usa un híbrido: KPI One en vivo cuando hay criterio y conexión,
 * y el snapshot local como respaldo. El cliente remoto y la descarga masiva
 * comparten [KpiSessionStore].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StockModule {

    @Binds
    @Singleton
    abstract fun bindStockRepository(impl: StockRepositoryRouter): StockRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindKpiSessionStore(impl: SecureCookieStore): KpiSessionStore

    @Binds
    @Singleton
    abstract fun bindRmCodeProvider(impl: AssetRmCodeProvider): RmCodeProvider
}

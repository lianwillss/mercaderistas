package com.rutamercaderistas.di

import android.content.Context
import com.rutamercaderistas.data.stock.FileStockStorage
import com.rutamercaderistas.data.stock.StockStorage
import com.rutamercaderistas.data.stock.remote.KpiOneClient
import com.rutamercaderistas.data.stock.remote.KpiSessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StockNetworkModule {

    @Provides
    @Singleton
    fun provideKpiHttpClient(session: KpiSessionStore): OkHttpClient =
        KpiOneClient.defaultHttpClient(session)

    @Provides
    @Singleton
    fun provideKpiOneClient(
        session: KpiSessionStore,
        http: OkHttpClient,
    ): KpiOneClient = KpiOneClient(session = session, http = http)

    @Provides
    @Singleton
    fun provideStockStorage(@ApplicationContext context: Context): StockStorage =
        FileStockStorage(File(context.filesDir, "stock"))
}

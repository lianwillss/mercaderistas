package com.rutamercaderistas.di

import android.content.Context
import com.rutamercaderistas.data.local.AppDatabase
import com.rutamercaderistas.data.local.RouteEntryDao
import com.rutamercaderistas.services.RecentRoutesStore
import com.rutamercaderistas.services.RuteroManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.create(context)
    }

    @Provides
    @Singleton
    fun provideRouteEntryDao(db: AppDatabase): RouteEntryDao {
        return db.routeEntryDao()
    }

    @Provides
    @Singleton
    fun provideRuteroManager(
        @ApplicationContext context: Context,
        routeEntryDao: RouteEntryDao,
    ): RuteroManager {
        return RuteroManager(context, routeEntryDao)
    }

    @Provides
    @Singleton
    fun provideRecentRoutesStore(@ApplicationContext context: Context): RecentRoutesStore {
        return RecentRoutesStore(context)
    }
}

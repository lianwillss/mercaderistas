package com.rutamercaderistas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Database(
    entities = [RouteEntryEntity::class, PromotionEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeEntryDao(): RouteEntryDao
    abstract fun promotionDao(): PromotionDao

    companion object {
        private const val DB_NAME = "mercaderistas.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration() // TODO: add Migration objects before production release
                .build()
    }
}

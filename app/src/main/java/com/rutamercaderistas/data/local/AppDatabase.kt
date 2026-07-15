package com.rutamercaderistas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        private val MIGRATION_1_3 = Migration(1, 3) { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `promotions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `brand` TEXT NOT NULL,
                `chain` TEXT NOT NULL,
                `productName` TEXT NOT NULL,
                `price` TEXT NOT NULL,
                `startDate` TEXT NOT NULL DEFAULT '',
                `endDate` TEXT NOT NULL DEFAULT '',
                `lastUpdated` INTEGER NOT NULL DEFAULT 0
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promotions_brand` ON `promotions` (`brand`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_promotions_chain` ON `promotions` (`chain`)")
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}

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
    entities = [RouteEntryEntity::class, PromotionEntity::class, EanProductEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeEntryDao(): RouteEntryDao
    abstract fun promotionDao(): PromotionDao
    abstract fun eanProductDao(): EanProductDao

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

        private val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `ean_products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `codCencosud` TEXT NOT NULL DEFAULT '',
                `codProveedor` TEXT NOT NULL DEFAULT '',
                `eanPrincipal` TEXT NOT NULL DEFAULT '',
                `descripcionProducto` TEXT NOT NULL DEFAULT '',
                `marca` TEXT NOT NULL DEFAULT '',
                `unBase` TEXT NOT NULL DEFAULT '',
                `unPedido` TEXT NOT NULL DEFAULT '',
                `conversion` TEXT NOT NULL DEFAULT '',
                `estado` TEXT NOT NULL DEFAULT '',
                `catN1Cencosud` TEXT NOT NULL DEFAULT '',
                `catN2Cencosud` TEXT NOT NULL DEFAULT '',
                `catN3Cencosud` TEXT NOT NULL DEFAULT '',
                `catN4Cencosud` TEXT NOT NULL DEFAULT '',
                `catN1Proveedor` TEXT NOT NULL DEFAULT '',
                `catN2Proveedor` TEXT NOT NULL DEFAULT '',
                `catN3Proveedor` TEXT NOT NULL DEFAULT '',
                `catN4Proveedor` TEXT NOT NULL DEFAULT '',
                `codigoBarra` TEXT NOT NULL DEFAULT ''
            )""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ean_products_eanPrincipal` ON `ean_products` (`eanPrincipal`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ean_products_cod_cencosud` ON `ean_products` (`codCencosud`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ean_products_cod_proveedor` ON `ean_products` (`codProveedor`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ean_products_codigo_barra` ON `ean_products` (`codigoBarra`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ean_products_marca` ON `ean_products` (`marca`)")
        }

        private val MIGRATION_4_5 = Migration(4, 5) { db ->
            // Se recrea la tabla con el esquema exacto que Room espera.
            // SQLite no permite quitar DEFAULT ni renombrar índices con ALTER,
            // y los datos se reimportan desde assets al abrir la pantalla EAN.
            db.execSQL("DROP TABLE IF EXISTS `ean_products`")
            db.execSQL(
                """CREATE TABLE `ean_products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `codCencosud` TEXT NOT NULL,
                `codProveedor` TEXT NOT NULL,
                `eanPrincipal` TEXT NOT NULL,
                `descripcionProducto` TEXT NOT NULL,
                `descripcion_norm` TEXT NOT NULL,
                `marca` TEXT NOT NULL,
                `marca_norm` TEXT NOT NULL,
                `unBase` TEXT NOT NULL,
                `unPedido` TEXT NOT NULL,
                `conversion` TEXT NOT NULL,
                `estado` TEXT NOT NULL,
                `catN1Cencosud` TEXT NOT NULL,
                `catN2Cencosud` TEXT NOT NULL,
                `catN3Cencosud` TEXT NOT NULL,
                `catN4Cencosud` TEXT NOT NULL,
                `catN1Proveedor` TEXT NOT NULL,
                `catN2Proveedor` TEXT NOT NULL,
                `catN3Proveedor` TEXT NOT NULL,
                `catN4Proveedor` TEXT NOT NULL,
                `codigoBarra` TEXT NOT NULL
            )"""
            )
            db.execSQL("CREATE INDEX `index_ean_products_eanPrincipal` ON `ean_products` (`eanPrincipal`)")
            db.execSQL("CREATE INDEX `index_ean_products_codCencosud` ON `ean_products` (`codCencosud`)")
            db.execSQL("CREATE INDEX `index_ean_products_codProveedor` ON `ean_products` (`codProveedor`)")
            db.execSQL("CREATE INDEX `index_ean_products_codigoBarra` ON `ean_products` (`codigoBarra`)")
            db.execSQL("CREATE INDEX `index_ean_products_marca` ON `ean_products` (`marca`)")
            db.execSQL("CREATE INDEX `index_ean_products_descripcion_norm` ON `ean_products` (`descripcion_norm`)")
            db.execSQL("CREATE INDEX `index_ean_products_marca_norm` ON `ean_products` (`marca_norm`)")
        }

        private val MIGRATION_5_6 = Migration(5, 6) { db ->
            // Columnas compactas (sin espacios) para búsqueda tolerante a
            // separadores: "bymaria" debe encontrar "by maria".
            db.execSQL("ALTER TABLE ean_products ADD COLUMN descripcion_norm_nospace TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE ean_products ADD COLUMN marca_norm_nospace TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX `index_ean_products_descripcion_norm_nospace` ON `ean_products` (`descripcion_norm_nospace`)")
            db.execSQL("CREATE INDEX `index_ean_products_marca_norm_nospace` ON `ean_products` (`marca_norm_nospace`)")
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
    }
}

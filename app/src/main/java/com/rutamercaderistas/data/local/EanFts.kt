package com.rutamercaderistas.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Índice full-text FTS5 del catálogo EAN como tabla *sidecar*: NO forma parte
 * del esquema Room (@Database sigue en versión 6), así que no hay migración
 * ni riesgo para instalaciones existentes. Se crea con IF NOT EXISTS y las
 * consultas van por @RawQuery con fallback a LIKE si la tabla no existe.
 *
 * Solo indexa las columnas normalizadas (sin diacríticos ni mayúsculas); los
 * códigos (EAN/SKU con ceros a la izquierda y fallback Levenshtein) se siguen
 * buscando por LIKE en [EanProductDao.searchCandidates].
 */
const val EAN_FTS_TABLE = "ean_product_fts"

private const val CREATE_FTS_TABLE = """CREATE VIRTUAL TABLE IF NOT EXISTS `ean_product_fts` USING fts5(
  `descripcion_norm`, `marca_norm`, `descripcion_norm_nospace`, `marca_norm_nospace`,
  content=`ean_products`, content_rowid=`id`
)"""

private const val TRIGGER_FTS_INSERT = """CREATE TRIGGER IF NOT EXISTS `ean_product_fts_insert`
AFTER INSERT ON `ean_products` BEGIN
  INSERT INTO `ean_product_fts`(rowid, `descripcion_norm`, `marca_norm`, `descripcion_norm_nospace`, `marca_norm_nospace`)
  VALUES (new.`id`, new.`descripcion_norm`, new.`marca_norm`, new.`descripcion_norm_nospace`, new.`marca_norm_nospace`);
END"""

private const val TRIGGER_FTS_DELETE = """CREATE TRIGGER IF NOT EXISTS `ean_product_fts_delete`
AFTER DELETE ON `ean_products` BEGIN
  INSERT INTO `ean_product_fts`(`ean_product_fts`, rowid, `descripcion_norm`, `marca_norm`, `descripcion_norm_nospace`, `marca_norm_nospace`)
  VALUES ('delete', old.`id`, old.`descripcion_norm`, old.`marca_norm`, old.`descripcion_norm_nospace`, old.`marca_norm_nospace`);
END"""

private const val TRIGGER_FTS_UPDATE = """CREATE TRIGGER IF NOT EXISTS `ean_product_fts_update`
AFTER UPDATE ON `ean_products` BEGIN
  INSERT INTO `ean_product_fts`(`ean_product_fts`, rowid, `descripcion_norm`, `marca_norm`, `descripcion_norm_nospace`, `marca_norm_nospace`)
  VALUES ('delete', old.`id`, old.`descripcion_norm`, old.`marca_norm`, old.`descripcion_norm_nospace`, old.`marca_norm_nospace`);
  INSERT INTO `ean_product_fts`(rowid, `descripcion_norm`, `marca_norm`, `descripcion_norm_nospace`, `marca_norm_nospace`)
  VALUES (new.`id`, new.`descripcion_norm`, new.`marca_norm`, new.`descripcion_norm_nospace`, new.`marca_norm_nospace`);
END"""

const val EAN_FTS_MATCH_QUERY = """SELECT p.* FROM ean_products p
JOIN ean_product_fts f ON p.id = f.rowid
WHERE ean_product_fts MATCH ?
ORDER BY bm25(ean_product_fts)"""

/**
 * Construye la expresión MATCH FTS5: AND de prefijos entrecomillados.
 * Los tokens ya vienen de compactNorm ([a-z0-9]), así que interpolarlos
 * entre comillas dobles es seguro. Pura y testeable en JVM.
 */
fun buildFtsMatch(tokens: List<String>): String? {
    val clean = tokens.map { it.trim() }.filter { it.isNotBlank() }
    if (clean.isEmpty()) return null
    return clean.joinToString(" AND ") { "\"$it\"*" }
}

@Singleton
class EanFtsManager @Inject constructor(
    private val db: AppDatabase,
) {
    /** Crea tabla + triggers si faltan y reconstruye el índice. Nunca lanza. */
    suspend fun ensureAndRebuild() = withContext(Dispatchers.IO) {
        try {
            val w = db.openHelper.writableDatabase
            w.execSQL(CREATE_FTS_TABLE)
            w.execSQL(TRIGGER_FTS_INSERT)
            w.execSQL(TRIGGER_FTS_DELETE)
            w.execSQL(TRIGGER_FTS_UPDATE)
            w.execSQL("INSERT INTO `ean_product_fts`(`ean_product_fts`) VALUES('rebuild')")
            Timber.d("EAN FTS5 índice reconstruido")
        } catch (e: Exception) {
            Timber.w(e, "EAN FTS5 no disponible — se sigue con LIKE")
        }
    }
}

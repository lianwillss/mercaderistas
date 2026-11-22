package com.rutamercaderistas.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface EanProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<EanProductEntity>)

    @Query("DELETE FROM ean_products")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM ean_products")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM ean_products WHERE eanPrincipal IS NOT NULL AND eanPrincipal != '' AND length(eanPrincipal) >= 8")
    suspend fun countValidEan(): Int

    @Query("SELECT COUNT(*) FROM ean_products WHERE descripcion_norm IS NULL OR descripcion_norm = ''")
    suspend fun hasUnnormalized(): Int

    // Resultados con ranking, filtros y orden estable (ver buildRankedSearch).
    // Room invalida el PagingSource al cambiar ean_products (observedEntities).
    @RawQuery(observedEntities = [EanProductEntity::class])
    fun rankedSearch(query: SupportSQLiteQuery): PagingSource<Int, EanProductEntity>

    // Catálogo sin texto de búsqueda, ordenado por marca/descripción (ver
    // buildBrowseSearch).
    @RawQuery(observedEntities = [EanProductEntity::class])
    fun browseSearch(query: SupportSQLiteQuery): PagingSource<Int, EanProductEntity>

    // Facetas de los chips (ver buildBrandFacets / buildCajaFacets).
    @RawQuery(observedEntities = [EanProductEntity::class])
    suspend fun brandFacets(query: SupportSQLiteQuery): List<BrandCountResult>

    @RawQuery(observedEntities = [EanProductEntity::class])
    suspend fun cajaFacets(query: SupportSQLiteQuery): List<CajaCountResult>

    // Total exacto de resultados con los mismos filtros (ver buildCountSearch).
    @RawQuery(observedEntities = [EanProductEntity::class])
    suspend fun countForQuery(query: SupportSQLiteQuery): Int

    /**
     * Código de barras del catálogo EAN para un SKU (Cód. Cencosud).
     *
     * Prefiere el EAN principal y cae al "Código de Barra" cuando el principal
     * viene vacío. Acepta coincidencia exacta y, como respaldo, igualdad
     * ignorando ceros iniciales. Devuelve null si el producto no está catalogado.
     */
    @Query(
        """
        SELECT COALESCE(NULLIF(eanPrincipal, ''), NULLIF(codigoBarra, ''))
        FROM ean_products
        WHERE codCencosud = :sku
           OR (:sku != '' AND codCencosud != '' AND ltrim(codCencosud, '0') = ltrim(:sku, '0'))
        ORDER BY (codCencosud = :sku) DESC,
                 length(COALESCE(NULLIF(eanPrincipal, ''), NULLIF(codigoBarra, ''))) DESC
        LIMIT 1
        """,
    )
    fun eanPorSkuFlow(sku: String): Flow<String?>

    /**
     * SKU (Cód. Cencosud) del producto que tiene este código de barras.
     *
     * Es el cruce inverso de [eanPorSkuFlow]: se usa al buscar o escanear un
     * EAN en Stock. Acepta el código en `eanPrincipal` o `codigoBarra` y, como
     * respaldo, igualdad ignorando ceros iniciales (el mismo criterio de
     * `eanPorSkuFlow`). Devuelve null si el EAN no está catalogado.
     */
    @Query(
        """
        SELECT codCencosud
        FROM ean_products
        WHERE codCencosud != ''
          AND (
            eanPrincipal = :ean
            OR codigoBarra = :ean
            OR (eanPrincipal != '' AND ltrim(eanPrincipal, '0') = ltrim(:ean, '0'))
            OR (codigoBarra != '' AND ltrim(codigoBarra, '0') = ltrim(:ean, '0'))
          )
        ORDER BY (eanPrincipal = :ean) DESC,
                 length(eanPrincipal) DESC
        LIMIT 1
        """,
    )
    suspend fun skuPorEan(ean: String): String?
}

// Resultados de las facetas de filtro.
data class BrandCountResult(
    val marca: String,
    val count: Int,
)

data class CajaCountResult(
    val conversion: String,
    val count: Int,
)

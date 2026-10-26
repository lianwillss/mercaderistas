package com.rutamercaderistas.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

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

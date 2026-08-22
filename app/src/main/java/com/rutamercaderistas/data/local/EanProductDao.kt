package com.rutamercaderistas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
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

    // Búsqueda por EAN principal
    @Query("SELECT * FROM ean_products WHERE eanPrincipal LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByEan(query: String): Flow<List<EanProductEntity>>

    // Búsqueda por Cód. Cencosud (SKU)
    @Query("SELECT * FROM ean_products WHERE codCencosud LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByCodCencosud(query: String): Flow<List<EanProductEntity>>

    // Búsqueda por Cód. Proveedor
    @Query("SELECT * FROM ean_products WHERE codProveedor LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByCodProveedor(query: String): Flow<List<EanProductEntity>>

    // Búsqueda por Código de Barra
    @Query("SELECT * FROM ean_products WHERE codigoBarra LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByCodigoBarra(query: String): Flow<List<EanProductEntity>>

    // Búsqueda general (códigos + nombre/marca normalizados, sin tildes)
    @Query("""
        SELECT * FROM ean_products
        WHERE eanPrincipal LIKE '%' || :query || '%'
           OR codCencosud LIKE '%' || :query || '%'
           OR codProveedor LIKE '%' || :query || '%'
           OR codigoBarra LIKE '%' || :query || '%'
           OR descripcion_norm LIKE '%' || :query || '%'
           OR marca_norm LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN eanPrincipal = :query THEN 0
                WHEN codCencosud = :query THEN 1
                WHEN codProveedor = :query THEN 2
                WHEN codigoBarra = :query THEN 3
                WHEN descripcion_norm = :query THEN 4
                WHEN descripcion_norm LIKE :query || '%' THEN 5
                WHEN descripcion_norm LIKE '%' || :query || '%' THEN 6
                ELSE 7
            END,
            descripcionProducto
        LIMIT 50
    """)
    fun searchAll(query: String): Flow<List<EanProductEntity>>

    // Búsqueda por marca (normalizada)
    @Query("SELECT * FROM ean_products WHERE marca_norm LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByMarca(query: String): Flow<List<EanProductEntity>>

    // Obtener todos (para debug/export)
    @Query("SELECT * FROM ean_products ORDER BY descripcionProducto")
    fun getAll(): Flow<List<EanProductEntity>>
}
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

    // Devuelve candidatos que contienen un único token (compacto) en cualquier
    // columna. El ViewModel combina los candidatos de varios tokens y filtra por
    // AND, permitiendo coincidencias en cualquier orden ("nat pistacho" →
    // "NAT ROMERO PISTACHO"). LIMIT alto para no descartar antes del filtro.
    @Query("""
        SELECT * FROM ean_products
        WHERE eanPrincipal LIKE '%' || :token || '%'
           OR codCencosud LIKE '%' || :token || '%'
           OR codProveedor LIKE '%' || :token || '%'
           OR codigoBarra LIKE '%' || :token || '%'
           OR descripcion_norm LIKE '%' || :token || '%'
           OR marca_norm LIKE '%' || :token || '%'
           OR descripcion_norm_nospace LIKE '%' || :token || '%'
           OR marca_norm_nospace LIKE '%' || :token || '%'
        LIMIT 300
    """)
    fun searchCandidates(token: String): Flow<List<EanProductEntity>>

    // Búsqueda por marca (normalizada)
    @Query("SELECT * FROM ean_products WHERE marca_norm LIKE '%' || :query || '%' ORDER BY descripcionProducto LIMIT 50")
    fun searchByMarca(query: String): Flow<List<EanProductEntity>>

    // Obtener todos
    @Query("SELECT * FROM ean_products ORDER BY descripcionProducto")
    fun getAll(): Flow<List<EanProductEntity>>
}
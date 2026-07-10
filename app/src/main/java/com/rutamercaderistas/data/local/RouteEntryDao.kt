package com.rutamercaderistas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteEntryDao {

    @Query("SELECT DISTINCT rutero FROM route_entries ORDER BY rutero")
    fun observeRuteros(): Flow<List<String>>

    @Query("SELECT DISTINCT rutero FROM route_entries ORDER BY rutero")
    suspend fun getDistinctRuteros(): List<String>

    @Query("SELECT * FROM route_entries WHERE rutero = :ruteroName")
    suspend fun getEntriesForRoute(ruteroName: String): List<RouteEntryEntity>

    @Query("SELECT COUNT(*) FROM route_entries WHERE rutero = :ruteroName")
    suspend fun countEntriesForRoute(ruteroName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<RouteEntryEntity>)

    @Transaction
    suspend fun deleteAllAndInsert(entries: List<RouteEntryEntity>) {
        deleteAll()
        insertAll(entries)
    }

    @Query("DELETE FROM route_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM route_entries WHERE rutero = :ruteroName")
    suspend fun deleteRoute(ruteroName: String)
}

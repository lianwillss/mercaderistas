package com.rutamercaderistas.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PromotionDao {

    @Query("DELETE FROM promotions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(promotions: List<PromotionEntity>)

    @Query("SELECT MAX(lastUpdated) FROM promotions")
    suspend fun getLastUpdated(): Long?

    @Query("SELECT COUNT(*) FROM promotions")
    suspend fun count(): Int

    @Query("SELECT * FROM promotions")
    suspend fun getAll(): List<PromotionEntity>

    @Query("DELETE FROM promotions WHERE endDate < :today")
    suspend fun deleteExpired(today: String): Int
}

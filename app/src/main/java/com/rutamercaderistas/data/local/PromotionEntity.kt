package com.rutamercaderistas.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "promotions",
    indices = [Index("brand"), Index("chain")],
)
data class PromotionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val chain: String,
    val productName: String,
    val price: String,
    val startDate: String = "",
    val endDate: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
)

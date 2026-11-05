package com.example.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budget.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM assets ORDER BY isPinned DESC, name ASC")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: String): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity)

    @Update
    suspend fun updateAsset(asset: AssetEntity)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("UPDATE assets SET balance = balance + :delta WHERE id = :id")
    suspend fun updateBalance(id: String, delta: Double)

    @Query("UPDATE assets SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateIsPinned(id: String, isPinned: Boolean)

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM assets WHERE type = 'LIQUID'")
    fun getSumOfLiquidAssetBalances(): Flow<Double>

    /** Synchronous version for use inside @Transaction blocks */
    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM assets WHERE type = 'LIQUID'")
    suspend fun getSumOfLiquidAssetBalancesSync(): Double
}

package com.example.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.budget.data.local.entity.PendingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PendingEntity>>

    @Query("SELECT * FROM pending_transactions ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<PendingEntity>

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getById(id: String): PendingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingEntity)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun delete(id: String)
}

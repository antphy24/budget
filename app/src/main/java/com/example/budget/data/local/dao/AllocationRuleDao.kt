package com.example.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.budget.data.local.entity.AllocationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllocationRuleDao {

    @Query("SELECT * FROM allocation_rules ORDER BY sort_order ASC")
    fun getAllRules(): Flow<List<AllocationRuleEntity>>

    @Query("SELECT * FROM allocation_rules ORDER BY sort_order ASC")
    suspend fun getAllRulesOnce(): List<AllocationRuleEntity>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM allocation_rules")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: AllocationRuleEntity)

    @Query("DELETE FROM allocation_rules WHERE fund_id = :fundId")
    suspend fun deleteRule(fundId: String)
}

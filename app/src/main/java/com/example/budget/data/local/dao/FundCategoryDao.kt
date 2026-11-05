package com.example.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budget.data.local.entity.FundCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FundCategoryDao {

    @Query("SELECT * FROM fund_categories ORDER BY isPinned DESC, name ASC")
    fun getAllFundCategories(): Flow<List<FundCategoryEntity>>

    @Query("SELECT * FROM fund_categories WHERE id = :id")
    suspend fun getFundCategoryById(id: String): FundCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFundCategory(fund: FundCategoryEntity)

    @Update
    suspend fun updateFundCategory(fund: FundCategoryEntity)

    @Delete
    suspend fun deleteFundCategory(fund: FundCategoryEntity)

    @Query("UPDATE fund_categories SET balance = balance + :delta WHERE id = :id")
    suspend fun updateBalance(id: String, delta: Double)

    @Query("UPDATE fund_categories SET isPinned = :isPinned WHERE id = :id")
    suspend fun updateIsPinned(id: String, isPinned: Boolean)

    @Query("UPDATE fund_categories SET balance = :newBalance WHERE id = :id")
    suspend fun resetBalance(id: String, newBalance: Double)

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM fund_categories")
    fun getSumOfAllFundBalances(): Flow<Double>

    /** Synchronous version for use inside @Transaction blocks */
    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM fund_categories")
    suspend fun getSumOfAllFundBalancesSync(): Double

    // --- Zero-Based Budgeting Queries ---
    
    @Query("SELECT * FROM fund_categories WHERE type = 'MONTHLY_SPENDING' ORDER BY isPinned DESC, name ASC")
    fun getMonthlySpendingEnvelopes(): Flow<List<FundCategoryEntity>>

    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM fund_categories WHERE type = 'MONTHLY_SPENDING'")
    fun getSumOfMonthlySpendingBalances(): Flow<Double>
}

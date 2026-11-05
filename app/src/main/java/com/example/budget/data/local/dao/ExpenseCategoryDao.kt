package com.example.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budget.data.local.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllExpenseCategories(): Flow<List<ExpenseCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseCategory(category: ExpenseCategoryEntity)

    @Update
    suspend fun updateExpenseCategory(category: ExpenseCategoryEntity)

    @Delete
    suspend fun deleteExpenseCategory(category: ExpenseCategoryEntity)
    
    @Query("UPDATE transactions SET expense_category = 'Uncategorized' WHERE expense_category = :categoryName")
    suspend fun clearCategoryFromTransactions(categoryName: String)
}

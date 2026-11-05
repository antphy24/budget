package com.example.budget.domain.repository

import com.example.budget.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow

interface ExpenseCategoryRepository {
    fun getAllExpenseCategories(): Flow<List<ExpenseCategory>>
    suspend fun insertExpenseCategory(category: ExpenseCategory)
    suspend fun updateExpenseCategory(category: ExpenseCategory)
    suspend fun deleteExpenseCategory(category: ExpenseCategory)
}

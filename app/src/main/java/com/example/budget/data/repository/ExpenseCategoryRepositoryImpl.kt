package com.example.budget.data.repository

import androidx.room.withTransaction
import com.example.budget.data.local.AppDatabase
import com.example.budget.data.local.dao.ExpenseCategoryDao
import com.example.budget.data.local.entity.ExpenseCategoryEntity
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.repository.ExpenseCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExpenseCategoryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val expenseCategoryDao: ExpenseCategoryDao
) : ExpenseCategoryRepository {

    override fun getAllExpenseCategories(): Flow<List<ExpenseCategory>> {
        return expenseCategoryDao.getAllExpenseCategories().map { entities ->
            entities.map { ExpenseCategory(it.id, it.name) }
        }
    }

    override suspend fun insertExpenseCategory(category: ExpenseCategory) {
        expenseCategoryDao.insertExpenseCategory(ExpenseCategoryEntity(category.id, category.name))
    }

    override suspend fun updateExpenseCategory(category: ExpenseCategory) {
        expenseCategoryDao.updateExpenseCategory(ExpenseCategoryEntity(category.id, category.name))
    }

    override suspend fun deleteExpenseCategory(category: ExpenseCategory) {
        db.withTransaction {
            // Revert all transactions with this category back to "Uncategorized"
            expenseCategoryDao.clearCategoryFromTransactions(category.name)
            expenseCategoryDao.deleteExpenseCategory(ExpenseCategoryEntity(category.id, category.name))
        }
    }
}

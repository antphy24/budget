package com.example.budget.domain.repository

import com.example.budget.domain.model.FundCategory
import kotlinx.coroutines.flow.Flow

interface FundCategoryRepository {
    fun getAllFundCategories(): Flow<List<FundCategory>>
    suspend fun getFundCategoryById(id: String): FundCategory?
    suspend fun insertFundCategory(fund: FundCategory)
    suspend fun updateFundCategory(fund: FundCategory)
    suspend fun deleteFundCategory(fund: FundCategory)
    suspend fun resetBalance(id: String, newBalance: Double)
    fun getSumOfAllBalances(): Flow<Double>
    suspend fun togglePinFundCategory(id: String, isPinned: Boolean)
    fun getMonthlySpendingEnvelopes(): Flow<List<FundCategory>>
    fun getSumOfMonthlySpendingBalances(): Flow<Double>
}

package com.example.budget.data.repository

import com.example.budget.data.local.dao.FundCategoryDao
import com.example.budget.data.mapper.toFundCategory
import com.example.budget.data.mapper.toFundCategoryEntity
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.repository.FundCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FundCategoryRepositoryImpl @Inject constructor(
    private val dao: FundCategoryDao
) : FundCategoryRepository {

    override fun getAllFundCategories(): Flow<List<FundCategory>> {
        return dao.getAllFundCategories().map { entities ->
            entities.map { it.toFundCategory() }
        }
    }

    override suspend fun getFundCategoryById(id: String): FundCategory? {
        return dao.getFundCategoryById(id)?.toFundCategory()
    }

    override suspend fun insertFundCategory(fund: FundCategory) {
        dao.insertFundCategory(fund.toFundCategoryEntity())
    }

    override suspend fun updateFundCategory(fund: FundCategory) {
        dao.updateFundCategory(fund.toFundCategoryEntity())
    }

    override suspend fun deleteFundCategory(fund: FundCategory) {
        dao.deleteFundCategory(fund.toFundCategoryEntity())
    }

    override suspend fun resetBalance(id: String, newBalance: Double) {
        dao.resetBalance(id, newBalance)
    }

    override fun getSumOfAllBalances(): Flow<Double> {
        return dao.getSumOfAllFundBalances()
    }

    override suspend fun togglePinFundCategory(id: String, isPinned: Boolean) {
        dao.updateIsPinned(id, isPinned)
    }

    override fun getMonthlySpendingEnvelopes(): Flow<List<FundCategory>> {
        return dao.getMonthlySpendingEnvelopes().map { entities ->
            entities.map { it.toFundCategory() }
        }
    }

    override fun getSumOfMonthlySpendingBalances(): Flow<Double> {
        return dao.getSumOfMonthlySpendingBalances()
    }
}

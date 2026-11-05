package com.example.budget.data.repository

import com.example.budget.data.local.AppDatabase
import com.example.budget.data.local.dao.AssetDao
import com.example.budget.data.local.dao.FundCategoryDao
import com.example.budget.data.local.dao.TransactionDao
import com.example.budget.data.mapper.toTransaction
import com.example.budget.data.mapper.toTransactionEntity
import com.example.budget.domain.exception.InvariantViolationException
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.TransactionRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.abs

class TransactionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val transactionDao: TransactionDao,
    private val assetDao: AssetDao,
    private val fundCategoryDao: FundCategoryDao
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions().map { entities ->
            entities.map { it.toTransaction() }
        }
    }

    override suspend fun getTransactionById(id: String): Transaction? {
        return transactionDao.getTransactionById(id)?.toTransaction()
    }

    override suspend fun processTransaction(transaction: Transaction) {
        db.withTransaction {
            // 1. Insert the transaction record
            transactionDao.insertTransaction(transaction.toTransactionEntity())

            // 2. Apply balance effects
            applyBalanceEffects(transaction, reverse = false)

            // 3. Verify the Golden Rule invariant
            verifyGoldenRule()
        }
    }

    override suspend fun updateTransaction(
        oldTransaction: Transaction,
        newTransaction: Transaction
    ) {
        db.withTransaction {
            // 1. Reverse the old transaction's balance effects
            applyBalanceEffects(oldTransaction, reverse = true)

            // 2. Apply the new transaction's balance effects
            applyBalanceEffects(newTransaction, reverse = false)

            // 3. Update the transaction record
            transactionDao.updateTransaction(newTransaction.toTransactionEntity())

            // 4. Verify the Golden Rule
            verifyGoldenRule()
        }
    }

    override suspend fun reverseAndDeleteTransaction(transaction: Transaction) {
        db.withTransaction {
            // 1. Reverse the balance effects
            applyBalanceEffects(transaction, reverse = true)

            // 2. Delete the record
            transactionDao.deleteTransaction(transaction.toTransactionEntity())

            // 3. Verify the Golden Rule
            verifyGoldenRule()
        }
    }

    override fun getExpenseSumByCategoryForMonth(
        startOfMonth: Long,
        endOfMonth: Long
    ): Flow<List<com.example.budget.domain.model.CategorySum>> {
        return transactionDao.getExpenseSumByCategoryForMonth(startOfMonth, endOfMonth).map { list ->
            list.map { com.example.budget.domain.model.CategorySum(it.expenseCategory, it.sumAmount) }
        }
    }

    override suspend fun processTransactions(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        db.withTransaction {
            transactions.forEach { txn ->
                transactionDao.insertTransaction(txn.toTransactionEntity())
                applyBalanceEffects(txn, reverse = false)
            }
            verifyGoldenRule()
        }
    }

    override fun getExpenseByFundSince(start: Long): Flow<Map<String, Double>> =
        transactionDao.getExpenseByFundSince(start).map { list -> list.associate { it.fundId to it.total } }

    override suspend fun getExpenseByFundSinceOnce(start: Long): Map<String, Double> =
        transactionDao.getExpenseByFundSinceOnce(start).associate { it.fundId to it.total }

    override suspend fun getAssignedIntoFundSinceOnce(start: Long): Map<String, Double> =
        transactionDao.getAssignedIntoFundSinceOnce(start).associate { it.fundId to it.total }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun applyBalanceEffects(txn: Transaction, reverse: Boolean) {
        val sign = if (reverse) -1.0 else 1.0

        when (txn.type) {
            TransactionType.INCOME -> {
                txn.primaryAssetId?.let { assetDao.updateBalance(it, txn.totalAmount * sign) }
                txn.primaryFundId?.let { fundCategoryDao.updateBalance(it, txn.totalAmount * sign) }
            }

            TransactionType.BUDGET_ASSIGNMENT -> {
                txn.primaryFundId?.let { fundCategoryDao.updateBalance(it, -txn.totalAmount * sign) }
                txn.secondaryFundId?.let { fundCategoryDao.updateBalance(it, txn.totalAmount * sign) }
            }

            TransactionType.EXPENSE -> {
                txn.primaryAssetId?.let { assetDao.updateBalance(it, -txn.totalAmount * sign) }
                txn.primaryFundId?.let { fundCategoryDao.updateBalance(it, -txn.totalAmount * sign) }
            }

            TransactionType.ASSET_TRANSFER -> {
                txn.primaryAssetId?.let { assetDao.updateBalance(it, -txn.totalAmount * sign) }
                txn.secondaryAssetId?.let { assetDao.updateBalance(it, txn.totalAmount * sign) }

                txn.primaryFundId?.let { fundCategoryDao.updateBalance(it, -txn.totalAmount * sign) }
                txn.secondaryFundId?.let { fundCategoryDao.updateBalance(it, txn.totalAmount * sign) }
            }
        }
    }

    private suspend fun verifyGoldenRule() {
        val liquidSum = assetDao.getSumOfLiquidAssetBalancesSync()
        val fundSum = fundCategoryDao.getSumOfAllFundBalancesSync()

        if (abs(liquidSum - fundSum) > 0.01) {
            throw InvariantViolationException(liquidSum, fundSum)
        }
    }
}

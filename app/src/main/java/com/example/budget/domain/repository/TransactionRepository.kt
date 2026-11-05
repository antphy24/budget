package com.example.budget.domain.repository

import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.CategorySum
import kotlinx.coroutines.flow.Flow

/**
 * Repository for the double-entry transaction ledger.
 *
 * All write operations (process, update, reverse+delete) are wrapped in
 * Room @Transaction blocks and enforce the invariant:
 *   Sum(LIQUID Asset balances) == Sum(FundCategory balances)
 */
interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    suspend fun getTransactionById(id: String): Transaction?

    /**
     * Process a new transaction: insert the record and atomically
     * update all affected asset/fund balances. Verifies the invariant.
     */
    suspend fun processTransaction(transaction: Transaction)

    /**
     * Update an existing transaction: reverse the old balance effects,
     * apply the new ones, and update the record. Verifies the invariant.
     */
    suspend fun updateTransaction(oldTransaction: Transaction, newTransaction: Transaction)

    /**
     * Reverse and delete a transaction: undo all balance effects
     * and remove the record. Verifies the invariant.
     */
    suspend fun reverseAndDeleteTransaction(transaction: Transaction)

    fun getExpenseSumByCategoryForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySum>>

    /**
     * Process several transactions atomically (all succeed or none do).
     * Used for income distribution and the end-of-cycle sweep.
     */
    suspend fun processTransactions(transactions: List<Transaction>)

    /** fundId → total spent from that pocket since [start] */
    fun getExpenseByFundSince(start: Long): Flow<Map<String, Double>>
    suspend fun getExpenseByFundSinceOnce(start: Long): Map<String, Double>

    /** fundId → total assigned into that pocket since [start] */
    suspend fun getAssignedIntoFundSinceOnce(start: Long): Map<String, Double>
}

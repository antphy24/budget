package com.example.budget.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.budget.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

data class CategorySum(
    @ColumnInfo(name = "expense_category") val expenseCategory: String,
    @ColumnInfo(name = "sum_amount") val sumAmount: Double
)

data class FundSum(
    @ColumnInfo(name = "fund_id") val fundId: String,
    @ColumnInfo(name = "total") val total: Double
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(txn: TransactionEntity)

    @Update
    suspend fun updateTransaction(txn: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(txn: TransactionEntity)

    @Query("""
        SELECT expense_category, SUM(total_amount) as sum_amount 
        FROM transactions 
        WHERE type = 'EXPENSE' 
          AND timestamp >= :startOfMonth 
          AND timestamp <= :endOfMonth
        GROUP BY expense_category
        ORDER BY sum_amount DESC
    """)
    fun getExpenseSumByCategoryForMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<CategorySum>>

    /** Total spent from each pocket since [start] */
    @Query("""
        SELECT primary_fund_id AS fund_id, SUM(total_amount) AS total
        FROM transactions
        WHERE type = 'EXPENSE'
          AND primary_fund_id IS NOT NULL
          AND timestamp >= :start
        GROUP BY primary_fund_id
    """)
    fun getExpenseByFundSince(start: Long): Flow<List<FundSum>>

    @Query("""
        SELECT primary_fund_id AS fund_id, SUM(total_amount) AS total
        FROM transactions
        WHERE type = 'EXPENSE'
          AND primary_fund_id IS NOT NULL
          AND timestamp >= :start
        GROUP BY primary_fund_id
    """)
    suspend fun getExpenseByFundSinceOnce(start: Long): List<FundSum>

    /** Total assigned INTO each pocket since [start] */
    @Query("""
        SELECT secondary_fund_id AS fund_id, SUM(total_amount) AS total
        FROM transactions
        WHERE type = 'BUDGET_ASSIGNMENT'
          AND secondary_fund_id IS NOT NULL
          AND timestamp >= :start
        GROUP BY secondary_fund_id
    """)
    suspend fun getAssignedIntoFundSinceOnce(start: Long): List<FundSum>
}

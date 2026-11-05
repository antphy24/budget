package com.example.budget.domain.model

/**
 * Domain model for a double-entry transaction, now supporting splits and tags.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val timestamp: Long,
    val totalAmount: Double,
    val primaryAssetId: String?,
    val secondaryAssetId: String?,
    val primaryFundId: String?,
    val secondaryFundId: String?,
    val expenseCategory: String,
    val memo: String
)

package com.example.budget.data.mapper

import com.example.budget.data.local.entity.TransactionEntity
import com.example.budget.domain.model.Transaction

fun TransactionEntity.toTransaction(): Transaction {
    return Transaction(
        id = id,
        type = type,
        timestamp = timestamp,
        totalAmount = totalAmount,
        primaryAssetId = primaryAssetId,
        secondaryAssetId = secondaryAssetId,
        primaryFundId = primaryFundId,
        secondaryFundId = secondaryFundId,
        expenseCategory = expenseCategory,
        memo = memo
    )
}

fun Transaction.toTransactionEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        type = type,
        timestamp = timestamp,
        totalAmount = totalAmount,
        primaryAssetId = primaryAssetId,
        secondaryAssetId = secondaryAssetId,
        primaryFundId = primaryFundId,
        secondaryFundId = secondaryFundId,
        expenseCategory = expenseCategory,
        memo = memo
    )
}

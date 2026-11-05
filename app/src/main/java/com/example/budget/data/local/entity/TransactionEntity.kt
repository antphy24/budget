package com.example.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.budget.domain.model.TransactionType

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val type: TransactionType,
    val timestamp: Long,
    @ColumnInfo(name = "total_amount")
    val totalAmount: Double,
    @ColumnInfo(name = "primary_asset_id")
    val primaryAssetId: String?,
    @ColumnInfo(name = "secondary_asset_id")
    val secondaryAssetId: String?,
    @ColumnInfo(name = "primary_fund_id")
    val primaryFundId: String?,
    @ColumnInfo(name = "secondary_fund_id")
    val secondaryFundId: String?,
    @ColumnInfo(name = "expense_category")
    val expenseCategory: String,
    val memo: String
)

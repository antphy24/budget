package com.example.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A transaction read from a scan or notification, waiting for the user to confirm it. */
@Entity(tableName = "pending_transactions")
data class PendingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** SCAN, SHARE or NOTIFICATION */
    val source: String,
    /** EXPENSE, INCOME or TRANSFER */
    val kind: String,
    val amount: Double,
    val timestamp: Long,
    val merchant: String,
    val category: String,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    @ColumnInfo(name = "asset2_id") val asset2Id: String?,
    @ColumnInfo(name = "fund_id") val fundId: String?,
    val reference: String?,
    /** JSON array of {name, amount, category, fundId} */
    @ColumnInfo(name = "items_json") val itemsJson: String,
    @ColumnInfo(name = "split_items") val splitItems: Boolean,
    val confidence: Double,
    /** Where it came from, e.g. "DANA notification" or "Screenshot" */
    val origin: String
)

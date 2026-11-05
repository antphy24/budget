package com.example.budget.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "allocation_rules")
data class AllocationRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "fund_id")
    val fundId: String,
    /** "FIXED" or "PERCENT" (see RuleKind) */
    val kind: String,
    val amount: Double,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int
)

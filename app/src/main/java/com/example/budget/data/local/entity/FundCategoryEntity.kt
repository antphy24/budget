package com.example.budget.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.budget.domain.model.EnvelopeType

@Entity(tableName = "fund_categories")
data class FundCategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: EnvelopeType,
    val balance: Double,
    val isPinned: Boolean = false
)

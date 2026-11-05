package com.example.budget.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.budget.domain.model.AssetType

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: AssetType,
    val balance: Double,
    val isPinned: Boolean = false
)

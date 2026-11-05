package com.example.budget.domain.model

data class Asset(
    val id: String,
    val name: String,
    val type: AssetType,
    val balance: Double,
    val isPinned: Boolean = false
)

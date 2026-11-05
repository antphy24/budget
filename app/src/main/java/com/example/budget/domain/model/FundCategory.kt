package com.example.budget.domain.model

data class FundCategory(
    val id: String,
    val name: String,
    val type: EnvelopeType,
    val balance: Double,
    val isPinned: Boolean = false
)

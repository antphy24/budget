package com.example.budget.domain.model

/**
 * The four logical transaction types in the double-entry envelope system.
 *
 * INCOME:            External → Asset + FundCategory(INCOME_HOLDING)
 * BUDGET_ASSIGNMENT: FundCategory → FundCategory
 * EXPENSE:           Asset + FundCategory → External
 * ASSET_TRANSFER:    Asset → Asset (fund required if crossing LIQUID/NON_LIQUID boundary)
 */
enum class TransactionType {
    INCOME,
    BUDGET_ASSIGNMENT,
    EXPENSE,
    ASSET_TRANSFER
}

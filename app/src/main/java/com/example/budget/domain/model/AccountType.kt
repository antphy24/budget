package com.example.budget.domain.model

/**
 * Represents the type of account involved in a transaction leg.
 * EXTERNAL represents money entering or leaving the system entirely.
 */
enum class AccountType {
    ASSET,
    FUND_CATEGORY,
    EXTERNAL
}

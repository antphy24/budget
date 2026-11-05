package com.example.budget.domain.exception

/**
 * Thrown when a transaction would violate the core invariant:
 *   Sum(LIQUID Asset balances) == Sum(FundCategory balances)
 *
 * Because this is thrown inside a Room @Transaction block,
 * the entire database operation is automatically rolled back.
 */
class InvariantViolationException(
    val liquidAssetSum: Double,
    val fundCategorySum: Double
) : RuntimeException(
    "Invariant violated: Sum of LIQUID asset balances ($liquidAssetSum) " +
    "!= Sum of FundCategory balances ($fundCategorySum)"
)

package com.example.budget.domain.model

/**
 * How a pocket gets filled when income is distributed.
 *
 * FIXED:   fill the pocket up to [AllocationRule.amount] per budget cycle
 *          (counting everything already assigned to it this cycle, so a
 *          second income in the same cycle won't fill it twice).
 * PERCENT: take [AllocationRule.amount] percent of what is left after all
 *          FIXED pockets were filled.
 */
enum class RuleKind {
    FIXED,
    PERCENT
}

data class AllocationRule(
    val fundId: String,
    val kind: RuleKind,
    val amount: Double,
    val sortOrder: Int = 0
)

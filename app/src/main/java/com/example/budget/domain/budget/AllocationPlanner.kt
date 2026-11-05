package com.example.budget.domain.budget

import com.example.budget.domain.model.AllocationRule
import com.example.budget.domain.model.RuleKind
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class PlannedMove(
    val fromFundId: String,
    val toFundId: String,
    val amount: Double,
    val reason: MoveReason
)

enum class MoveReason {
    /** Leftover of last cycle moved to savings */
    SWEEP,
    /** Fill a FIXED pocket */
    FIXED,
    /** Percentage share */
    PERCENT,
    /** What remains after all rules → savings pocket */
    REMAINDER
}

data class AllocationPlan(
    val moves: List<PlannedMove>,
    /** Money that stays in the income pocket (no savings pocket set, or nothing to do) */
    val leftInHolding: Double
) {
    val totalDistributed: Double get() = moves.filter { it.reason != MoveReason.SWEEP }.sumOf { it.amount }
}

object AllocationPlanner {

    /**
     * Plan how to distribute [available] money sitting in the income pocket.
     *
     * 1. FIXED rules (by priority) are filled up to their target for this cycle,
     *    minus what was already assigned to them this cycle ([assignedThisCycle]).
     * 2. PERCENT rules take their share of what is left after step 1.
     * 3. Whatever remains goes to the savings pocket (if one is set).
     *
     * Amounts are whole rupiah (rounded down), so nothing is ever over-assigned.
     */
    fun plan(
        available: Double,
        holdingFundId: String,
        savingsFundId: String?,
        rules: List<AllocationRule>,
        existingFundIds: Set<String>,
        assignedThisCycle: Map<String, Double>
    ): AllocationPlan {
        if (available <= 0.0) return AllocationPlan(emptyList(), max(available, 0.0))

        val validRules = rules.filter { it.fundId != holdingFundId && it.fundId in existingFundIds && it.amount > 0 }
        val moves = mutableListOf<PlannedMove>()
        var remaining = floor(available)

        // 1. FIXED
        validRules.filter { it.kind == RuleKind.FIXED }
            .sortedBy { it.sortOrder }
            .forEach { rule ->
                val alreadyAssigned = assignedThisCycle[rule.fundId] ?: 0.0
                val need = max(0.0, rule.amount - alreadyAssigned)
                val give = floor(min(need, remaining))
                if (give > 0) {
                    moves += PlannedMove(holdingFundId, rule.fundId, give, MoveReason.FIXED)
                    remaining -= give
                }
            }

        // 2. PERCENT (of what is left after FIXED)
        val base = remaining
        validRules.filter { it.kind == RuleKind.PERCENT }
            .sortedBy { it.sortOrder }
            .forEach { rule ->
                val share = floor(base * rule.amount / 100.0)
                val give = min(share, remaining)
                if (give > 0) {
                    moves += PlannedMove(holdingFundId, rule.fundId, give, MoveReason.PERCENT)
                    remaining -= give
                }
            }

        // 3. Remainder → savings
        var leftInHolding = remaining + (available - floor(available)) // keep sub-rupiah cents in holding
        if (savingsFundId != null && savingsFundId != holdingFundId && savingsFundId in existingFundIds && remaining > 0) {
            moves += PlannedMove(holdingFundId, savingsFundId, remaining, MoveReason.REMAINDER)
            leftInHolding -= remaining
        }

        return AllocationPlan(moves, leftInHolding)
    }

    /**
     * Moves that sweep every positive balance of the given spending pockets into savings.
     */
    fun planSweep(
        spendingBalances: Map<String, Double>,
        savingsFundId: String
    ): List<PlannedMove> =
        spendingBalances
            .filter { (id, balance) -> id != savingsFundId && balance > 0.0 }
            .map { (id, balance) -> PlannedMove(id, savingsFundId, balance, MoveReason.SWEEP) }
}

package com.example.budget.domain.budget

enum class PaceStatus(val severity: Int) {
    /** Spending is on track (or the pocket has no budget this cycle) */
    OK(0),
    /** Spending faster than time passes */
    FAST(1),
    /** 90%+ of the pocket is used */
    LOW(2),
    /** Balance below zero */
    OVERSPENT(3)
}

data class PocketPace(
    val fundId: String,
    val balance: Double,
    val spentThisCycle: Double,
    /** What the pocket had available this cycle = balance + spent */
    val budgetThisCycle: Double,
    /** 0..1+ share of the budget that was spent */
    val usedFraction: Double,
    /** 0..1 share of the cycle that has passed */
    val elapsedFraction: Double,
    val daysRemaining: Int,
    val perDayLeft: Double,
    val status: PaceStatus
)

object PocketPaceCalculator {

    /** How far ahead of time spending may run before we call it FAST */
    private const val FAST_MARGIN = 0.15
    private const val LOW_THRESHOLD = 0.90

    fun calculate(
        fundId: String,
        balance: Double,
        spentThisCycle: Double,
        cycle: BudgetCycle,
        now: Long = System.currentTimeMillis()
    ): PocketPace {
        val budget = balance + spentThisCycle
        val elapsed = cycle.elapsedFraction(now)
        val daysLeft = cycle.daysRemaining(now)
        val used = if (budget > 0.0) spentThisCycle / budget else if (spentThisCycle > 0) 1.0 else 0.0

        val status = when {
            balance < 0.0 -> PaceStatus.OVERSPENT
            budget <= 0.0 -> PaceStatus.OK
            used >= LOW_THRESHOLD -> PaceStatus.LOW
            used > elapsed + FAST_MARGIN -> PaceStatus.FAST
            else -> PaceStatus.OK
        }

        return PocketPace(
            fundId = fundId,
            balance = balance,
            spentThisCycle = spentThisCycle,
            budgetThisCycle = budget,
            usedFraction = used,
            elapsedFraction = elapsed,
            daysRemaining = daysLeft,
            perDayLeft = if (balance > 0) balance / daysLeft else 0.0,
            status = status
        )
    }
}

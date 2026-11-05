package com.example.budget.domain.budget

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A budget cycle runs from payday (00:00) up to, but not including, the next payday.
 * With payday = 27: 27 Oct 00:00 → 27 Nov 00:00.
 */
data class BudgetCycle(
    val start: Long,
    val end: Long
) {
    val lengthMillis: Long get() = end - start

    /** 0.0 at the start of the cycle, 1.0 at the end. */
    fun elapsedFraction(now: Long): Double =
        ((now - start).toDouble() / lengthMillis.toDouble()).coerceIn(0.0, 1.0)

    /** Whole days left including today (at least 1). */
    fun daysRemaining(now: Long): Int {
        val millisLeft = (end - now).coerceAtLeast(0L)
        val days = Math.ceil(millisLeft.toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
        return days.coerceAtLeast(1)
    }

    companion object {
        /** Payday is limited to 1..28 so it exists in every month. */
        fun clampPayday(day: Int): Int = day.coerceIn(1, 28)

        /** The cycle that contains [timestamp]. */
        fun containing(timestamp: Long, payday: Int): BudgetCycle {
            val day = clampPayday(payday)
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.get(Calendar.DAY_OF_MONTH) < day) {
                cal.add(Calendar.MONTH, -1)
            }
            cal.set(Calendar.DAY_OF_MONTH, day)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis
            return BudgetCycle(start, end)
        }

        /**
         * The cycle that income received at [timestamp] is meant for.
         * Income that arrives up to [earlyDays] days before payday counts for the
         * NEW cycle (salary often lands a day or two early).
         */
        fun forIncome(timestamp: Long, payday: Int, earlyDays: Int = EARLY_INCOME_DAYS): BudgetCycle {
            val shifted = timestamp + TimeUnit.DAYS.toMillis(earlyDays.toLong())
            return containing(shifted, payday)
        }

        const val EARLY_INCOME_DAYS = 3
    }
}

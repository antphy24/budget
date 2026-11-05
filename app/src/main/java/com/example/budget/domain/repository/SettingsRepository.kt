package com.example.budget.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val isBalanceHidden: Flow<Boolean>
    suspend fun setBalanceHidden(isHidden: Boolean)

    // --- Pockets / budget cycle ---

    /** Day of month the budget cycle starts (1..28). Default 27. */
    val payday: StateFlow<Int>
    suspend fun setPayday(day: Int)

    /** Pocket where income lands before it is distributed. */
    val holdingFundId: StateFlow<String?>
    suspend fun setHoldingFundId(id: String?)

    /** Pocket that receives leftovers at the end of a cycle and the remainder of distributions. */
    val savingsFundId: StateFlow<String?>
    suspend fun setSavingsFundId(id: String?)

    /** Start of the last cycle whose leftovers were swept to savings (0 = never). */
    val lastSweepCycleStart: StateFlow<Long>
    suspend fun setLastSweepCycleStart(cycleStart: Long)

    /** Highest alert severity already notified for a pocket in a cycle (avoids repeat notifications). */
    fun getNotifiedSeverity(fundId: String, cycleStart: Long): Int
    fun setNotifiedSeverity(fundId: String, cycleStart: Long, severity: Int)

    // --- Smart capture ---

    /** Package names whose payment notifications are read */
    val watchedApps: StateFlow<Set<String>>
    suspend fun setAppWatched(packageName: String, watched: Boolean)

    /** Apps that posted a notification mentioning rupiah: package → app name */
    val seenPaymentApps: StateFlow<Map<String, String>>
    fun rememberPaymentApp(packageName: String, label: String)
}

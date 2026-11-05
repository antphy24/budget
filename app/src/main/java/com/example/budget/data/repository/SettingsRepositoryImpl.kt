package com.example.budget.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("budget_settings", Context.MODE_PRIVATE)

    private val _isBalanceHidden = MutableStateFlow(prefs.getBoolean("hide_balance", false))
    override val isBalanceHidden: Flow<Boolean> = _isBalanceHidden.asStateFlow()

    override suspend fun setBalanceHidden(isHidden: Boolean) {
        prefs.edit().putBoolean("hide_balance", isHidden).apply()
        _isBalanceHidden.value = isHidden
    }

    // --- Pockets / budget cycle ---

    private val _payday = MutableStateFlow(BudgetCycle.clampPayday(prefs.getInt(KEY_PAYDAY, 27)))
    override val payday: StateFlow<Int> = _payday.asStateFlow()

    override suspend fun setPayday(day: Int) {
        val clamped = BudgetCycle.clampPayday(day)
        prefs.edit().putInt(KEY_PAYDAY, clamped).apply()
        _payday.value = clamped
    }

    private val _holdingFundId = MutableStateFlow(prefs.getString(KEY_HOLDING, null))
    override val holdingFundId: StateFlow<String?> = _holdingFundId.asStateFlow()

    override suspend fun setHoldingFundId(id: String?) {
        prefs.edit().putString(KEY_HOLDING, id).apply()
        _holdingFundId.value = id
    }

    private val _savingsFundId = MutableStateFlow(prefs.getString(KEY_SAVINGS, null))
    override val savingsFundId: StateFlow<String?> = _savingsFundId.asStateFlow()

    override suspend fun setSavingsFundId(id: String?) {
        prefs.edit().putString(KEY_SAVINGS, id).apply()
        _savingsFundId.value = id
    }

    // First run: treat the current cycle as already swept, so the app never
    // sweeps pockets in the middle of the cycle you are in right now.
    private val _lastSweep = MutableStateFlow(
        if (prefs.contains(KEY_LAST_SWEEP)) prefs.getLong(KEY_LAST_SWEEP, 0L)
        else BudgetCycle.forIncome(System.currentTimeMillis(), _payday.value).start.also {
            prefs.edit().putLong(KEY_LAST_SWEEP, it).apply()
        }
    )
    override val lastSweepCycleStart: StateFlow<Long> = _lastSweep.asStateFlow()

    override suspend fun setLastSweepCycleStart(cycleStart: Long) {
        prefs.edit().putLong(KEY_LAST_SWEEP, cycleStart).apply()
        _lastSweep.value = cycleStart
    }

    override fun getNotifiedSeverity(fundId: String, cycleStart: Long): Int =
        prefs.getInt(alertKey(fundId, cycleStart), 0)

    override fun setNotifiedSeverity(fundId: String, cycleStart: Long, severity: Int) {
        prefs.edit().putInt(alertKey(fundId, cycleStart), severity).apply()
    }

    private fun alertKey(fundId: String, cycleStart: Long) = "alert_${fundId}_$cycleStart"

    // --- Smart capture ---

    init {
        // Scanning is fully on-device now; forget any cloud API key saved by version 1.2
        if (prefs.contains("gemini_api_key")) prefs.edit().remove("gemini_api_key").apply()
    }

    private val _watched = MutableStateFlow(prefs.getStringSet("watched_apps", emptySet())?.toSet() ?: emptySet())
    override val watchedApps: StateFlow<Set<String>> = _watched.asStateFlow()

    override suspend fun setAppWatched(packageName: String, watched: Boolean) {
        val next = if (watched) _watched.value + packageName else _watched.value - packageName
        prefs.edit().putStringSet("watched_apps", next).apply()
        _watched.value = next
    }

    private val _seen = MutableStateFlow(
        (prefs.getStringSet("seen_payment_apps", emptySet()) ?: emptySet())
            .mapNotNull { it.split('|', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
            .toMap()
    )
    override val seenPaymentApps: StateFlow<Map<String, String>> = _seen.asStateFlow()

    @Synchronized
    override fun rememberPaymentApp(packageName: String, label: String) {
        if (_seen.value[packageName] == label) return
        val next = _seen.value + (packageName to label)
        prefs.edit().putStringSet("seen_payment_apps", next.map { (k, v) -> "$k|$v" }.toSet()).apply()
        _seen.value = next
    }

    private companion object {
        const val KEY_PAYDAY = "payday"
        const val KEY_HOLDING = "holding_fund_id"
        const val KEY_SAVINGS = "savings_fund_id"
        const val KEY_LAST_SWEEP = "last_sweep_cycle_start"
    }
}

package com.example.budget.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.budget.PocketPace
import com.example.budget.domain.budget.PocketPaceCalculator
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val safeToSpendBalance: Double = 0.0,
    val activeSpendingEnvelopes: List<FundCategory> = emptyList(),
    val isBalanceHidden: Boolean = false,
    /** fundId → spending pace this cycle */
    val pace: Map<String, PocketPace> = emptyMap(),
    /** Money waiting in the income pocket */
    val unallocated: Double = 0.0,
    /** A new cycle started and leftovers haven't been swept yet */
    val sweepPending: Boolean = false
)

private data class PocketSettings(
    val holdingFundId: String?,
    val savingsFundId: String?,
    val payday: Int,
    val lastSweep: Long
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val fundCategoryRepository: FundCategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pocketSettings = combine(
                settingsRepository.holdingFundId,
                settingsRepository.savingsFundId,
                settingsRepository.payday,
                settingsRepository.lastSweepCycleStart
            ) { holding, savings, payday, lastSweep -> PocketSettings(holding, savings, payday, lastSweep) }

            val cycleExpenses = settingsRepository.payday.flatMapLatest { payday ->
                val cycle = BudgetCycle.containing(System.currentTimeMillis(), payday)
                transactionRepository.getExpenseByFundSince(cycle.start).map { cycle to it }
            }

            combine(
                fundCategoryRepository.getAllFundCategories(),
                cycleExpenses,
                pocketSettings,
                settingsRepository.isBalanceHidden
            ) { funds, (cycle, spentByFund), s, isHidden ->
                val now = System.currentTimeMillis()
                val spending = funds.filter {
                    it.type == EnvelopeType.MONTHLY_SPENDING && it.id != s.holdingFundId && it.id != s.savingsFundId
                }
                val pace = spending.associate { f ->
                    f.id to PocketPaceCalculator.calculate(f.id, f.balance, spentByFund[f.id] ?: 0.0, cycle, now)
                }
                DashboardState(
                    safeToSpendBalance = spending.sumOf { it.balance },
                    activeSpendingEnvelopes = spending,
                    isBalanceHidden = isHidden,
                    pace = pace,
                    unallocated = funds.firstOrNull { it.id == s.holdingFundId }?.balance ?: 0.0,
                    sweepPending = s.savingsFundId != null &&
                        s.lastSweep < BudgetCycle.forIncome(now, s.payday).start
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun toggleHideBalance() {
        viewModelScope.launch {
            settingsRepository.setBalanceHidden(!_state.value.isBalanceHidden)
        }
    }

    fun togglePin(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            fundCategoryRepository.togglePinFundCategory(id, isPinned)
        }
    }
}

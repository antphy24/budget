package com.example.budget.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.Bootstrap
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.budget.PocketPace
import com.example.budget.domain.budget.PocketPaceCalculator
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.presentation.common.TxPresenter
import com.example.budget.presentation.common.TxRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class PocketView(val fund: FundCategory, val pace: PocketPace)

data class HomeState(
    val loaded: Boolean = false,
    val hidden: Boolean = false,
    val safeToSpend: Double = 0.0,
    val cycleStart: Long = 0L,
    val cycleEnd: Long = 0L,
    val dayOfCycle: Int = 1,
    val daysInCycle: Int = 30,
    val daysToPayday: Int = 0,
    val unallocated: Double = 0.0,
    val sweepPending: Boolean = false,
    val spending: List<PocketView> = emptyList(),
    val reserves: List<FundCategory> = emptyList(),
    val recent: List<TxRow> = emptyList(),
    val assets: Map<String, Asset> = emptyMap(),
    val funds: Map<String, FundCategory> = emptyMap(),
    val hasAccounts: Boolean = true
)

private data class PocketSettings(val holding: String?, val savings: String?, val payday: Int, val lastSweep: Long)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val funds: FundCategoryRepository,
    private val assets: AssetRepository,
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
    bootstrap: Bootstrap
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch { bootstrap.ensure() }
        viewModelScope.launch {
            val pocketSettings = combine(
                settings.holdingFundId, settings.savingsFundId, settings.payday, settings.lastSweepCycleStart
            ) { h, s, p, l -> PocketSettings(h, s, p, l) }

            combine(
                funds.getAllFundCategories(),
                assets.getAllAssets(),
                transactions.getAllTransactions(),
                pocketSettings,
                settings.isBalanceHidden
            ) { fundList, assetList, txs, s, hidden ->
                build(fundList, assetList, txs, s, hidden)
            }.collect { _state.value = it }
        }
    }

    private fun build(
        fundList: List<FundCategory>,
        assetList: List<Asset>,
        txs: List<Transaction>,
        s: PocketSettings,
        hidden: Boolean
    ): HomeState {
        val now = System.currentTimeMillis()
        val cycle = BudgetCycle.containing(now, s.payday)
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val daysInCycle = ((cycle.end - cycle.start + dayMs / 2) / dayMs).toInt().coerceAtLeast(1)
        val dayOfCycle = ((now - cycle.start) / dayMs).toInt() + 1

        val spentByFund = txs.asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.timestamp >= cycle.start && it.primaryFundId != null }
            .groupBy { it.primaryFundId!! }
            .mapValues { (_, l) -> l.sumOf { it.totalAmount } }

        val spending = fundList
            .filter { it.type == EnvelopeType.MONTHLY_SPENDING && it.id != s.holding && it.id != s.savings }
            .map { PocketView(it, PocketPaceCalculator.calculate(it.id, it.balance, spentByFund[it.id] ?: 0.0, cycle, now)) }

        val reserves = fundList
            .filter { it.id != s.holding && (it.type != EnvelopeType.MONTHLY_SPENDING || it.id == s.savings) }
            .sortedWith(compareByDescending<FundCategory> { it.id == s.savings }.thenByDescending { it.isPinned })

        val assetMap = assetList.associateBy { it.id }
        val fundMap = fundList.associateBy { it.id }

        return HomeState(
            loaded = true,
            hidden = hidden,
            safeToSpend = spending.sumOf { it.fund.balance },
            cycleStart = cycle.start,
            cycleEnd = cycle.end,
            dayOfCycle = dayOfCycle.coerceIn(1, daysInCycle),
            daysInCycle = daysInCycle,
            daysToPayday = (daysInCycle - dayOfCycle + 1).coerceAtLeast(0),
            unallocated = fundMap[s.holding]?.balance ?: 0.0,
            sweepPending = s.savings != null && s.lastSweep < BudgetCycle.forIncome(now, s.payday).start,
            spending = spending,
            reserves = reserves,
            recent = txs.take(5).map { TxPresenter.row(it, assetMap, fundMap) },
            assets = assetMap,
            funds = fundMap,
            hasAccounts = assetList.isNotEmpty()
        )
    }

    fun toggleHidden() {
        viewModelScope.launch { settings.setBalanceHidden(!_state.value.hidden) }
    }

    fun delete(tx: Transaction) {
        viewModelScope.launch { runCatching { transactions.reverseAndDeleteTransaction(tx) } }
    }
}

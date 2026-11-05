package com.example.budget.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.presentation.common.DayGroup
import com.example.budget.presentation.common.TxPresenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class ActivityFilter(val label: String) { All("All"), Spending("Spending"), Income("Income"), Moves("Transfers") }

data class ActivityState(
    val hidden: Boolean = false,
    val periodLabel: String = "",
    val isCurrent: Boolean = true,
    val filter: ActivityFilter = ActivityFilter.All,
    val income: Double = 0.0,
    val spent: Double = 0.0,
    val groups: List<DayGroup> = emptyList(),
    val assets: Map<String, Asset> = emptyMap(),
    val funds: Map<String, FundCategory> = emptyMap()
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    assets: AssetRepository,
    funds: FundCategoryRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /** 0 = current cycle, -1 = previous, … */
    private val offset = MutableStateFlow(0)
    private val filter = MutableStateFlow(ActivityFilter.All)
    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val controls = combine(offset, filter, settings.payday) { o, f, p -> Triple(o, f, p) }
            combine(
                transactions.getAllTransactions(),
                assets.getAllAssets(),
                funds.getAllFundCategories(),
                controls,
                settings.isBalanceHidden
            ) { txs, assetList, fundList, (o, f, payday), hidden ->
                build(txs, assetList, fundList, o, f, payday, hidden)
            }.collect { _state.value = it }
        }
    }

    private fun build(
        txs: List<Transaction>, assetList: List<Asset>, fundList: List<FundCategory>,
        o: Int, f: ActivityFilter, payday: Int, hidden: Boolean
    ): ActivityState {
        val cycle = cycleAt(o, payday)
        val inPeriod = txs.filter { it.timestamp >= cycle.start && it.timestamp < cycle.end }
        val shown = inPeriod.filter {
            when (f) {
                ActivityFilter.All -> true
                ActivityFilter.Spending -> it.type == TransactionType.EXPENSE
                ActivityFilter.Income -> it.type == TransactionType.INCOME
                ActivityFilter.Moves -> it.type == TransactionType.ASSET_TRANSFER || it.type == TransactionType.BUDGET_ASSIGNMENT
            }
        }
        val a = assetList.associateBy { it.id }
        val fm = fundList.associateBy { it.id }
        val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
        return ActivityState(
            hidden = hidden,
            periodLabel = "${fmt.format(Date(cycle.start))} – ${fmt.format(Date(cycle.end - 1))}",
            isCurrent = o == 0,
            filter = f,
            income = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.totalAmount },
            spent = inPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.totalAmount },
            groups = TxPresenter.groupByDay(shown.map { TxPresenter.row(it, a, fm) }),
            assets = a,
            funds = fm
        )
    }

    private fun cycleAt(o: Int, payday: Int): BudgetCycle {
        var c = BudgetCycle.containing(System.currentTimeMillis(), payday)
        repeat(-o) { c = BudgetCycle.containing(c.start - 1, payday) }
        return c
    }

    fun previous() = offset.update { it - 1 }
    fun next() = offset.update { minOf(0, it + 1) }
    fun setFilter(f: ActivityFilter) { filter.value = f }

    fun delete(tx: Transaction) {
        viewModelScope.launch { runCatching { transactions.reverseAndDeleteTransaction(tx) } }
    }
}

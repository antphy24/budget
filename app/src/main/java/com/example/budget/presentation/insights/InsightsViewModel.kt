package com.example.budget.presentation.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class Share(val name: String, val amount: Double, val fraction: Float, val count: Int)

data class InsightsState(
    val hidden: Boolean = false,
    val periodLabel: String = "",
    val isCurrent: Boolean = true,
    val spent: Double = 0.0,
    val income: Double = 0.0,
    val perDay: Double = 0.0,
    /** Change vs the previous cycle at the same point in time; null when there's nothing to compare */
    val changeVsPrevious: Double? = null,
    val byCategory: List<Share> = emptyList(),
    val byPocket: List<Share> = emptyList(),
    val topNotes: List<Share> = emptyList()
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    transactions: TransactionRepository,
    funds: FundCategoryRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val offset = MutableStateFlow(0)
    private val _state = MutableStateFlow(InsightsState())
    val state: StateFlow<InsightsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                transactions.getAllTransactions(),
                funds.getAllFundCategories(),
                offset,
                settings.payday,
                settings.isBalanceHidden
            ) { txs, fundList, o, payday, hidden -> build(txs, fundList, o, payday, hidden) }
                .collect { _state.value = it }
        }
    }

    private fun build(txs: List<Transaction>, fundList: List<FundCategory>, o: Int, payday: Int, hidden: Boolean): InsightsState {
        val now = System.currentTimeMillis()
        val cycle = cycleAt(o, payday, now)
        val prev = BudgetCycle.containing(cycle.start - 1, payday)
        val fundNames = fundList.associate { it.id to it.name }

        val inCycle = txs.filter { it.timestamp >= cycle.start && it.timestamp < cycle.end }
        val expenses = inCycle.filter { it.type == TransactionType.EXPENSE }
        val spent = expenses.sumOf { it.totalAmount }

        val effectiveEnd = if (o == 0) now else cycle.end
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val days = ((effectiveEnd - cycle.start) / dayMs + 1).coerceAtLeast(1)

        // Compare like with like: previous cycle up to the same day
        val elapsed = effectiveEnd - cycle.start
        val prevSpent = txs.filter {
            it.type == TransactionType.EXPENSE && it.timestamp >= prev.start && it.timestamp < prev.start + elapsed
        }.sumOf { it.totalAmount }
        val change = if (prevSpent > 0) (spent - prevSpent) / prevSpent else null

        fun shares(groups: Map<String, List<Transaction>>, limit: Int = 12): List<Share> {
            val sums = groups.map { (k, l) -> Triple(k, l.sumOf { it.totalAmount }, l.size) }.sortedByDescending { it.second }
            val max = sums.firstOrNull()?.second ?: 0.0
            return sums.take(limit).map { (k, v, n) -> Share(k, v, if (max > 0) (v / max).toFloat() else 0f, n) }
        }

        val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
        return InsightsState(
            hidden = hidden,
            periodLabel = "${fmt.format(Date(cycle.start))} – ${fmt.format(Date(cycle.end - 1))}",
            isCurrent = o == 0,
            spent = spent,
            income = inCycle.filter { it.type == TransactionType.INCOME }.sumOf { it.totalAmount },
            perDay = spent / days,
            changeVsPrevious = change,
            byCategory = shares(expenses.groupBy { it.expenseCategory.ifBlank { "Uncategorized" } }),
            byPocket = shares(expenses.groupBy { it.primaryFundId?.let(fundNames::get) ?: "No pocket" }),
            topNotes = shares(
                expenses.filter { it.memo.isNotBlank() }.groupBy { it.memo.trim().lowercase().replaceFirstChar { c -> c.uppercase() } },
                limit = 5
            )
        )
    }

    private fun cycleAt(o: Int, payday: Int, now: Long): BudgetCycle {
        var c = BudgetCycle.containing(now, payday)
        repeat(-o) { c = BudgetCycle.containing(c.start - 1, payday) }
        return c
    }

    fun previous() = offset.update { it - 1 }
    fun next() = offset.update { minOf(0, it + 1) }
}

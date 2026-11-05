package com.example.budget.presentation.pockets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.AllocationPlanner
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.budget.MoveReason
import com.example.budget.domain.budget.PlannedMove
import com.example.budget.domain.model.AllocationRule
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.RuleKind
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AllocationRuleRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PreviewLine(
    val fromName: String,
    val toName: String,
    val amount: Double,
    val reason: MoveReason
)

data class PocketPlanState(
    val funds: List<FundCategory> = emptyList(),
    val rules: Map<String, AllocationRule> = emptyMap(),
    val payday: Int = 27,
    val cycle: BudgetCycle? = null,
    val holdingFundId: String? = null,
    val savingsFundId: String? = null,
    val holdingBalance: Double = 0.0,
    val sweepPending: Boolean = false,
    val sweepEnabled: Boolean = true,
    val preview: List<PreviewLine> = emptyList(),
    val leftInHolding: Double = 0.0,
    val percentTotal: Double = 0.0,
    val isBusy: Boolean = false,
    val message: String? = null,
    // rule dialog
    val editingFund: FundCategory? = null,
    val kindInput: RuleKind? = null,
    val amountInput: String = ""
) {
    val canConfirm: Boolean get() = (preview.isNotEmpty() || sweepPending) && !isBusy
}

@HiltViewModel
class PocketPlanViewModel @Inject constructor(
    private val fundCategoryRepository: FundCategoryRepository,
    private val ruleRepository: AllocationRuleRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PocketPlanState())
    val state: StateFlow<PocketPlanState> = _state.asStateFlow()

    private val sweepEnabled = MutableStateFlow(true)

    /** Moves that "Confirm" will execute, kept in sync with the preview */
    private var pendingMoves: List<PlannedMove> = emptyList()
    private var pendingIncomeCycleStart: Long = 0L
    private var pendingIncludesSweepDecision: Boolean = false

    init {
        viewModelScope.launch {
            val settings = combine(
                settingsRepository.payday,
                settingsRepository.holdingFundId,
                settingsRepository.savingsFundId,
                settingsRepository.lastSweepCycleStart,
                sweepEnabled
            ) { payday, holding, savings, lastSweep, sweepOn ->
                SettingsSnapshot(payday, holding, savings, lastSweep, sweepOn)
            }
            combine(
                fundCategoryRepository.getAllFundCategories(),
                ruleRepository.getAllRules(),
                settings
            ) { funds, rules, s -> Triple(funds, rules, s) }
                .collectLatest { (funds, rules, s) -> recompute(funds, rules, s) }
        }
    }

    private data class SettingsSnapshot(
        val payday: Int,
        val holdingFundId: String?,
        val savingsFundId: String?,
        val lastSweep: Long,
        val sweepEnabled: Boolean
    )

    private suspend fun recompute(funds: List<FundCategory>, rules: List<AllocationRule>, s: SettingsSnapshot) {
        val now = System.currentTimeMillis()
        val cycle = BudgetCycle.containing(now, s.payday)
        val incomeCycle = BudgetCycle.forIncome(now, s.payday)
        val byId = funds.associateBy { it.id }
        val holding = s.holdingFundId?.let { byId[it] }
        val savings = s.savingsFundId?.let { byId[it] }

        val sweepPending = savings != null && s.lastSweep < incomeCycle.start
        val moves = mutableListOf<PlannedMove>()

        if (sweepPending && s.sweepEnabled && savings != null) {
            val spendingBalances = funds
                .filter { it.type == EnvelopeType.MONTHLY_SPENDING && it.id != holding?.id && it.id != savings.id }
                .associate { it.id to it.balance }
            moves += AllocationPlanner.planSweep(spendingBalances, savings.id)
        }

        var leftInHolding = 0.0
        if (holding != null && holding.balance > 0.0) {
            val assigned = transactionRepository.getAssignedIntoFundSinceOnce(incomeCycle.start)
            val plan = AllocationPlanner.plan(
                available = holding.balance,
                holdingFundId = holding.id,
                savingsFundId = savings?.id,
                rules = rules,
                existingFundIds = byId.keys,
                assignedThisCycle = assigned
            )
            moves += plan.moves
            leftInHolding = plan.leftInHolding
        }

        pendingMoves = moves
        pendingIncomeCycleStart = incomeCycle.start
        pendingIncludesSweepDecision = sweepPending

        _state.update {
            it.copy(
                funds = funds,
                rules = rules.associateBy { r -> r.fundId },
                payday = s.payday,
                cycle = cycle,
                holdingFundId = holding?.id,
                savingsFundId = savings?.id,
                holdingBalance = holding?.balance ?: 0.0,
                sweepPending = sweepPending,
                sweepEnabled = s.sweepEnabled,
                preview = moves.map { m ->
                    PreviewLine(
                        fromName = byId[m.fromFundId]?.name ?: "?",
                        toName = byId[m.toFundId]?.name ?: "?",
                        amount = m.amount,
                        reason = m.reason
                    )
                },
                leftInHolding = leftInHolding,
                percentTotal = rules.filter { r -> r.kind == RuleKind.PERCENT && r.fundId in byId }.sumOf { r -> r.amount }
            )
        }
    }

    // --- Settings ---

    fun changePayday(delta: Int) {
        viewModelScope.launch { settingsRepository.setPayday(_state.value.payday + delta) }
    }

    fun setHoldingFund(id: String?) {
        viewModelScope.launch { settingsRepository.setHoldingFundId(id) }
    }

    fun setSavingsFund(id: String?) {
        viewModelScope.launch { settingsRepository.setSavingsFundId(id) }
    }

    fun setSweepEnabled(enabled: Boolean) {
        sweepEnabled.value = enabled
    }

    // --- Rule dialog ---

    fun openRuleEditor(fund: FundCategory) {
        val rule = _state.value.rules[fund.id]
        _state.update {
            it.copy(
                editingFund = fund,
                kindInput = rule?.kind,
                amountInput = rule?.amount?.let { a -> if (a % 1.0 == 0.0) a.toLong().toString() else a.toString() } ?: ""
            )
        }
    }

    fun onKindChanged(kind: RuleKind?) = _state.update { it.copy(kindInput = kind) }
    fun onAmountChanged(text: String) = _state.update { it.copy(amountInput = text.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
    fun dismissRuleEditor() = _state.update { it.copy(editingFund = null) }

    fun saveRule() {
        val st = _state.value
        val fund = st.editingFund ?: return
        val kind = st.kindInput
        viewModelScope.launch {
            if (kind == null) {
                ruleRepository.removeRule(fund.id)
            } else {
                // FIXED: "1.500.000" → 1500000 (dots are thousands separators)
                // PERCENT: "2,5" or "2.5" → 2.5
                val amount = when (kind) {
                    RuleKind.FIXED -> st.amountInput.filter { it.isDigit() }.toDoubleOrNull()
                    RuleKind.PERCENT -> st.amountInput.replace(',', '.').toDoubleOrNull()
                }
                if (amount == null || amount <= 0) {
                    _state.update { it.copy(message = "Enter an amount") }
                    return@launch
                }
                if (kind == RuleKind.PERCENT && amount > 100) {
                    _state.update { it.copy(message = "Percent must be between 0 and 100") }
                    return@launch
                }
                ruleRepository.setRule(fund.id, kind, amount)
            }
            _state.update { it.copy(editingFund = null) }
        }
    }

    // --- Distribute ---

    fun confirm() {
        val moves = pendingMoves
        val cycleStart = pendingIncomeCycleStart
        val markSweep = pendingIncludesSweepDecision
        if (moves.isEmpty() && !markSweep) return

        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val base = System.currentTimeMillis()
                val txns = moves.mapIndexed { i, m ->
                    Transaction(
                        id = UUID.randomUUID().toString(),
                        type = TransactionType.BUDGET_ASSIGNMENT,
                        timestamp = base + i, // keeps the order in history
                        totalAmount = m.amount,
                        primaryAssetId = null,
                        secondaryAssetId = null,
                        primaryFundId = m.fromFundId,
                        secondaryFundId = m.toFundId,
                        expenseCategory = "Uncategorized",
                        memo = when (m.reason) {
                            MoveReason.SWEEP -> "Leftover swept to savings"
                            MoveReason.FIXED -> "Auto-distribute (fixed)"
                            MoveReason.PERCENT -> "Auto-distribute (%)"
                            MoveReason.REMAINDER -> "Auto-distribute (remainder)"
                        }
                    )
                }
                transactionRepository.processTransactions(txns)
                // Whether the sweep ran or was switched off, it is decided for this cycle.
                if (markSweep) settingsRepository.setLastSweepCycleStart(cycleStart)
                _state.update { it.copy(isBusy = false, message = "Done — ${txns.size} moves saved") }
            } catch (e: Exception) {
                _state.update { it.copy(isBusy = false, message = e.message ?: "Could not distribute") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

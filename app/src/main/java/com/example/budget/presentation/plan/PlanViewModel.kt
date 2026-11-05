package com.example.budget.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.AllocationPlanner
import com.example.budget.domain.budget.Bootstrap
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

data class PreviewLine(val fromName: String, val toName: String, val amount: Double, val reason: MoveReason)

/** Pocket being created (id == null) or edited in the sheet */
data class PocketEditor(
    val id: String? = null,
    val name: String = "",
    val type: EnvelopeType = EnvelopeType.MONTHLY_SPENDING,
    val kind: RuleKind? = null,
    val amountText: String = "",
    val balance: Double = 0.0,
    val isSystem: Boolean = false
)

data class PlanState(
    val loaded: Boolean = false,
    val hidden: Boolean = false,
    val funds: List<FundCategory> = emptyList(),
    val rules: Map<String, AllocationRule> = emptyMap(),
    val payday: Int = 27,
    val cycleStart: Long = 0L,
    val cycleEnd: Long = 0L,
    val holdingFundId: String? = null,
    val savingsFundId: String? = null,
    val holdingBalance: Double = 0.0,
    val sweepPending: Boolean = false,
    val sweepEnabled: Boolean = true,
    val preview: List<PreviewLine> = emptyList(),
    val leftInHolding: Double = 0.0,
    val percentTotal: Double = 0.0,
    val fixedTotal: Double = 0.0,
    val isBusy: Boolean = false,
    val message: String? = null,
    val editor: PocketEditor? = null
) {
    val canConfirm: Boolean get() = (preview.isNotEmpty() || sweepPending) && !isBusy
    val pockets: List<FundCategory> get() = funds.filter { it.id != holdingFundId }
}

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val fundRepo: FundCategoryRepository,
    private val ruleRepo: AllocationRuleRepository,
    private val settings: SettingsRepository,
    private val txRepo: TransactionRepository,
    bootstrap: Bootstrap
) : ViewModel() {

    private val _state = MutableStateFlow(PlanState())
    val state: StateFlow<PlanState> = _state.asStateFlow()

    private val sweepEnabled = MutableStateFlow(true)
    private var pendingMoves: List<PlannedMove> = emptyList()
    private var pendingCycleStart = 0L
    private var pendingSweepDecision = false

    private data class Snap(
        val payday: Int, val holding: String?, val savings: String?, val lastSweep: Long, val sweepOn: Boolean
    )

    init {
        viewModelScope.launch { bootstrap.ensure() }
        viewModelScope.launch {
            val snap = combine(
                settings.payday, settings.holdingFundId, settings.savingsFundId,
                settings.lastSweepCycleStart, sweepEnabled
            ) { p, h, s, l, on -> Snap(p, h, s, l, on) }
            combine(fundRepo.getAllFundCategories(), ruleRepo.getAllRules(), snap, settings.isBalanceHidden) { f, r, s, hidden ->
                Triple(f, r, s) to hidden
            }.collectLatest { (t, hidden) -> recompute(t.first, t.second, t.third, hidden) }
        }
    }

    private suspend fun recompute(funds: List<FundCategory>, rules: List<AllocationRule>, s: Snap, hidden: Boolean) {
        val now = System.currentTimeMillis()
        val cycle = BudgetCycle.containing(now, s.payday)
        val incomeCycle = BudgetCycle.forIncome(now, s.payday)
        val byId = funds.associateBy { it.id }
        val holding = s.holding?.let { byId[it] }
        val savings = s.savings?.let { byId[it] }

        val sweepPending = savings != null && s.lastSweep < incomeCycle.start
        val moves = mutableListOf<PlannedMove>()
        if (sweepPending && s.sweepOn && savings != null) {
            val balances = funds
                .filter { it.type == EnvelopeType.MONTHLY_SPENDING && it.id != holding?.id && it.id != savings.id }
                .associate { it.id to it.balance }
            moves += AllocationPlanner.planSweep(balances, savings.id)
        }
        var left = 0.0
        if (holding != null && holding.balance > 0.0) {
            val plan = AllocationPlanner.plan(
                available = holding.balance,
                holdingFundId = holding.id,
                savingsFundId = savings?.id,
                rules = rules,
                existingFundIds = byId.keys,
                assignedThisCycle = txRepo.getAssignedIntoFundSinceOnce(incomeCycle.start)
            )
            moves += plan.moves
            left = plan.leftInHolding
        }
        pendingMoves = moves
        pendingCycleStart = incomeCycle.start
        pendingSweepDecision = sweepPending

        val validRules = rules.filter { it.fundId in byId }
        _state.update {
            it.copy(
                loaded = true,
                hidden = hidden,
                funds = funds,
                rules = validRules.associateBy { r -> r.fundId },
                payday = s.payday,
                cycleStart = cycle.start,
                cycleEnd = cycle.end,
                holdingFundId = holding?.id,
                savingsFundId = savings?.id,
                holdingBalance = holding?.balance ?: 0.0,
                sweepPending = sweepPending,
                sweepEnabled = s.sweepOn,
                preview = moves.map { m ->
                    PreviewLine(byId[m.fromFundId]?.name ?: "?", byId[m.toFundId]?.name ?: "?", m.amount, m.reason)
                },
                leftInHolding = left,
                percentTotal = validRules.filter { r -> r.kind == RuleKind.PERCENT }.sumOf { r -> r.amount },
                fixedTotal = validRules.filter { r -> r.kind == RuleKind.FIXED }.sumOf { r -> r.amount }
            )
        }
    }

    // --- Cycle settings ---

    fun changePayday(delta: Int) { viewModelScope.launch { settings.setPayday(_state.value.payday + delta) } }
    fun setHoldingFund(id: String?) { viewModelScope.launch { settings.setHoldingFundId(id) } }
    fun setSavingsFund(id: String?) { viewModelScope.launch { settings.setSavingsFundId(id) } }
    fun setSweepEnabled(enabled: Boolean) { sweepEnabled.value = enabled }

    // --- Pocket editor ---

    fun newPocket() = _state.update { it.copy(editor = PocketEditor()) }

    fun editPocket(fund: FundCategory) {
        val st = _state.value
        val rule = st.rules[fund.id]
        _state.update {
            it.copy(
                editor = PocketEditor(
                    id = fund.id,
                    name = fund.name,
                    type = fund.type,
                    kind = rule?.kind,
                    amountText = rule?.amount?.let(::formatInput) ?: "",
                    balance = fund.balance,
                    isSystem = fund.id == st.holdingFundId || fund.id == st.savingsFundId
                )
            )
        }
    }

    fun updateEditor(transform: (PocketEditor) -> PocketEditor) =
        _state.update { s -> s.copy(editor = s.editor?.let(transform)) }

    fun closeEditor() = _state.update { it.copy(editor = null) }

    fun saveEditor() {
        val e = _state.value.editor ?: return
        val name = e.name.trim()
        if (name.isEmpty()) { toast("Give the pocket a name"); return }
        val amount = parseAmount(e.kind, e.amountText)
        if (e.kind != null && (amount == null || amount <= 0)) { toast("Enter an amount for the rule"); return }
        if (e.kind == RuleKind.PERCENT && amount != null && amount > 100) { toast("A percentage can't be more than 100"); return }

        viewModelScope.launch {
            val id = e.id ?: UUID.randomUUID().toString()
            if (e.id == null) {
                fundRepo.insertFundCategory(FundCategory(id, name, e.type, 0.0))
            } else {
                val current = fundRepo.getFundCategoryById(id) ?: return@launch
                fundRepo.updateFundCategory(current.copy(name = name, type = e.type))
            }
            if (e.kind == null || amount == null) ruleRepo.removeRule(id) else ruleRepo.setRule(id, e.kind, amount)
            _state.update { it.copy(editor = null) }
        }
    }

    fun deleteEditorPocket() {
        val e = _state.value.editor ?: return
        val id = e.id ?: return
        if (e.isSystem) { toast("This pocket is used by the app. Choose another one in Cycle settings first."); return }
        if (kotlin.math.abs(e.balance) >= 1.0) { toast("Move this pocket's money out before deleting it"); return }
        viewModelScope.launch {
            val fund = fundRepo.getFundCategoryById(id) ?: return@launch
            runCatching { fundRepo.deleteFundCategory(fund) }
                .onSuccess { ruleRepo.removeRule(id); _state.update { it.copy(editor = null, message = "Deleted ${fund.name}") } }
                .onFailure { toast("Couldn't delete: it's still used by past transactions") }
        }
    }

    // --- Distribute ---

    fun confirm() {
        val moves = pendingMoves
        val cycleStart = pendingCycleStart
        val markSweep = pendingSweepDecision
        if (moves.isEmpty() && !markSweep) return
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val base = System.currentTimeMillis()
                val txns = moves.mapIndexed { i, m ->
                    Transaction(
                        id = UUID.randomUUID().toString(),
                        type = TransactionType.BUDGET_ASSIGNMENT,
                        timestamp = base + i,
                        totalAmount = m.amount,
                        primaryAssetId = null,
                        secondaryAssetId = null,
                        primaryFundId = m.fromFundId,
                        secondaryFundId = m.toFundId,
                        expenseCategory = Bootstrap.UNCATEGORIZED,
                        memo = when (m.reason) {
                            MoveReason.SWEEP -> "Leftover to savings"
                            MoveReason.FIXED, MoveReason.PERCENT -> "Distributed"
                            MoveReason.REMAINDER -> "Remainder to savings"
                        }
                    )
                }
                txRepo.processTransactions(txns)
                if (markSweep) settings.setLastSweepCycleStart(cycleStart)
                val distributed = moves.filter { it.reason != MoveReason.SWEEP }.sumOf { it.amount }
                _state.update {
                    it.copy(
                        isBusy = false,
                        message = if (distributed > 0) "Distributed into ${moves.count { m -> m.reason != MoveReason.SWEEP }} pockets" else "Leftovers moved to savings"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isBusy = false, message = e.message ?: "Couldn't distribute") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    private fun toast(m: String) = _state.update { it.copy(message = m) }

    companion object {
        fun parseAmount(kind: RuleKind?, text: String): Double? = when (kind) {
            RuleKind.FIXED -> text.filter { it.isDigit() }.toDoubleOrNull()
            RuleKind.PERCENT -> text.replace(',', '.').toDoubleOrNull()
            null -> null
        }

        fun formatInput(a: Double): String =
            if (a % 1.0 == 0.0) a.toLong().toString() else a.toString().replace('.', ',')
    }
}

package com.example.budget.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.budget.MoveReason
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.RuleKind
import com.example.budget.presentation.common.BudgetSheet
import com.example.budget.presentation.common.SheetTitle
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget

@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    Box(Modifier.fillMaxSize()) {
        PlanContent(
            state = state,
            actions = PlanActions(
                onNewPocket = viewModel::newPocket,
                onEditPocket = viewModel::editPocket,
                onConfirm = viewModel::confirm,
                onSweepToggle = viewModel::setSweepEnabled,
                onPayday = viewModel::changePayday,
                onHolding = viewModel::setHoldingFund,
                onSavings = viewModel::setSavingsFund
            )
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    state.editor?.let { editor ->
        PocketSheet(
            editor = editor,
            onChange = viewModel::updateEditor,
            onSave = viewModel::saveEditor,
            onDelete = viewModel::deleteEditorPocket,
            onDismiss = viewModel::closeEditor
        )
    }
}

data class PlanActions(
    val onNewPocket: () -> Unit = {},
    val onEditPocket: (FundCategory) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onSweepToggle: (Boolean) -> Unit = {},
    val onPayday: (Int) -> Unit = {},
    val onHolding: (String?) -> Unit = {},
    val onSavings: (String?) -> Unit = {}
)

@Composable
fun PlanContent(state: PlanState, actions: PlanActions) {
    val c = Budget.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.paper),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            PageHeader("Plan", modifier = Modifier.statusBarsPadding())
        }

        item { DistributeCard(state, actions) }

        item { SectionHeader("Pockets", action = "New pocket", onAction = actions.onNewPocket) }
        item {
            Group {
                if (state.loaded && state.pockets.isEmpty()) {
                    EmptyNote("No pockets yet", "Create one for each purpose: food, transport, bills, savings.")
                }
                state.pockets.forEachIndexed { i, f ->
                    if (i > 0) Hairline(inset = 70.dp)
                    PocketPlanRow(f, state, onClick = { actions.onEditPocket(f) })
                }
            }
        }
        item {
            val savingsName = state.funds.firstOrNull { it.id == state.savingsFundId }?.name ?: "savings"
            val parts = buildList {
                if (state.fixedTotal > 0) add("Fixed amounts need ${rupiah(state.fixedTotal, state.hidden)} each cycle.")
                if (state.percentTotal > 0) add("Percentages add up to ${formatPct(state.percentTotal)}% of what's left.")
                add("Anything remaining goes to $savingsName.")
            }
            Text(
                parts.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.percentTotal > 100) c.ruby else c.inkSoft,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp)
            )
        }

        item { SectionHeader("Cycle") }
        item {
            Group {
                ListRow(
                    title = "Payday",
                    subtitle = "Each cycle starts on this day of the month",
                    trailing = { Stepper(state.payday, onMinus = { actions.onPayday(-1) }, onPlus = { actions.onPayday(1) }) }
                )
                Hairline()
                PocketPickerRow("Income lands in", state.funds, state.holdingFundId, actions.onHolding)
                Hairline()
                PocketPickerRow("Leftovers go to", state.funds, state.savingsFundId, actions.onSavings)
            }
        }
        item {
            Text(
                "When a new cycle starts, what's left in spending pockets moves to your savings pocket. Bills and savings goals keep their balance. Income that arrives up to 3 days before payday counts for the new cycle.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun DistributeCard(state: PlanState, actions: PlanActions) {
    val c = Budget.colors
    val hasMoney = state.holdingBalance >= 1.0
    if (!hasMoney && !state.sweepPending) {
        Group {
            ListRow(
                title = "Nothing to distribute",
                subtitle = "Income you log lands in ${state.funds.firstOrNull { it.id == state.holdingFundId }?.name ?: "your income pocket"}, then you split it here."
            )
        }
        return
    }
    Group {
        Column(Modifier.padding(16.dp)) {
            Text(if (hasMoney) "Ready to distribute" else "A new cycle has started", style = MaterialTheme.typography.labelLarge, color = c.inkSoft)
            if (hasMoney) MoneyText(state.holdingBalance, style = MaterialTheme.typography.displayMedium, hidden = state.hidden)

            if (state.sweepPending) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { actions.onSweepToggle(!state.sweepEnabled) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CheckDot(state.sweepEnabled)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "First move last cycle's leftovers to savings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink
                    )
                }
            }

            if (state.preview.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                state.preview.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (line.reason) {
                                MoveReason.SWEEP -> "${line.fromName} to ${line.toName}"
                                MoveReason.REMAINDER -> "${line.toName}, the rest"
                                else -> line.toName
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (line.reason == MoveReason.SWEEP) c.inkSoft else c.ink,
                            modifier = Modifier.weight(1f)
                        )
                        MoneyText(line.amount, style = MaterialTheme.typography.titleSmall, hidden = state.hidden)
                    }
                }
                if (state.leftInHolding >= 1.0) {
                    Text(
                        "${rupiah(state.leftInHolding, state.hidden)} stays here until you add rules or a savings pocket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else if (hasMoney) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Give your pockets a fixed amount or a percentage, and this splits itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft
                )
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = when {
                    state.preview.isEmpty() && state.sweepPending -> "Start the new cycle"
                    state.preview.all { it.reason == MoveReason.SWEEP } -> "Move leftovers"
                    else -> "Distribute"
                },
                onClick = actions.onConfirm,
                enabled = state.canConfirm,
                loading = state.isBusy
            )
        }
    }
}

@Composable
private fun CheckDot(checked: Boolean) {
    val c = Budget.colors
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) c.emerald else c.sunk),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Outlined.Check, null, tint = c.onEmerald, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PocketPlanRow(f: FundCategory, state: PlanState, onClick: () -> Unit) {
    val c = Budget.colors
    val rule = state.rules[f.id]
    val type = typeLabel(f.type)
    val ruleText = when {
        f.id == state.savingsFundId -> "Receives leftovers and the rest of each income"
        rule?.kind == RuleKind.FIXED -> "$type, fills to ${rupiah(rule!!.amount, state.hidden)}"
        rule?.kind == RuleKind.PERCENT -> "$type, ${formatPct(rule!!.amount)}% of income"
        else -> "$type, no rule yet"
    }
    ListRow(
        title = f.name,
        subtitle = ruleText,
        subtitleColor = if (rule == null && f.id != state.savingsFundId) c.inkFaint else c.inkSoft,
        leading = { Glyph(iconForName(f.name)) },
        trailing = {
            MoneyText(f.balance, style = MaterialTheme.typography.titleSmall, hidden = state.hidden, color = if (f.balance < 0) c.ruby else c.ink)
        },
        onClick = onClick
    )
}

@Composable
private fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Budget.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.sunk),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Remove, "Earlier", tint = c.ink, modifier = Modifier.size(18.dp)) }
        Text("$value", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.widthIn(min = 24.dp))
        IconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, "Later", tint = c.ink, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun PocketPickerRow(label: String, funds: List<FundCategory>, selectedId: String?, onSelect: (String?) -> Unit) {
    val c = Budget.colors
    var open by remember { mutableStateOf(false) }
    ListRow(
        title = label,
        trailing = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(funds.firstOrNull { it.id == selectedId }?.name ?: "Choose", style = MaterialTheme.typography.bodyLarge, color = c.inkSoft)
                    Icon(Icons.Outlined.ExpandMore, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(c.surface)) {
                    funds.forEach { f ->
                        DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); open = false })
                    }
                }
            }
        },
        onClick = { open = true }
    )
}

@Composable
private fun PocketSheet(
    editor: PocketEditor,
    onChange: ((PocketEditor) -> PocketEditor) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Budget.colors
    BudgetSheet(onDismiss) {
        SheetTitle(if (editor.id == null) "New pocket" else "Edit pocket")
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(8.dp))
            FieldLabel("Name")
            FieldWell(editor.name, { v -> onChange { it.copy(name = v) } }, placeholder = "Food, Transport, Kos…")

            Spacer(Modifier.height(18.dp))
            FieldLabel("Kind")
            Segmented(
                options = listOf(EnvelopeType.MONTHLY_SPENDING, EnvelopeType.FIXED_BILL, EnvelopeType.SINKING_FUND),
                selected = editor.type,
                label = ::typeLabel,
                onSelect = { t -> onChange { it.copy(type = t) } }
            )
            Text(
                when (editor.type) {
                    EnvelopeType.MONTHLY_SPENDING -> "For day-to-day spending. Leftovers move to savings when a new cycle starts, and you get a warning when it runs low."
                    EnvelopeType.FIXED_BILL -> "For bills with a set amount, like kos or wifi. The balance is kept between cycles."
                    EnvelopeType.SINKING_FUND -> "For saving up over time. The balance is kept between cycles."
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
                modifier = Modifier.padding(top = 8.dp)
            )

            run {
                Spacer(Modifier.height(18.dp))
                FieldLabel("When income arrives")
                Segmented(
                    options = listOf<RuleKind?>(null, RuleKind.FIXED, RuleKind.PERCENT),
                    selected = editor.kind,
                    label = { k -> when (k) { null -> "Nothing"; RuleKind.FIXED -> "Fixed amount"; RuleKind.PERCENT -> "Percentage" } },
                    onSelect = { k -> onChange { it.copy(kind = k) } }
                )
                if (editor.kind != null) {
                    Spacer(Modifier.height(10.dp))
                    FieldWell(
                        value = editor.amountText,
                        onValueChange = { v -> onChange { it.copy(amountText = v.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }) } },
                        prefix = if (editor.kind == RuleKind.FIXED) "Rp" else null,
                        suffix = if (editor.kind == RuleKind.PERCENT) "%" else null,
                        placeholder = if (editor.kind == RuleKind.FIXED) "600000" else "20",
                        keyboardType = KeyboardType.Number
                    )
                    Text(
                        if (editor.kind == RuleKind.FIXED) "Filled up to this amount each cycle, first, before percentages."
                        else "Takes this share of what's left after fixed amounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            PrimaryButton(if (editor.id == null) "Create pocket" else "Save changes", onClick = onSave)
            if (editor.id != null && !editor.isSystem) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete pocket", style = MaterialTheme.typography.labelLarge, color = c.ruby)
                }
            }
        }
    }
}

fun typeLabel(t: EnvelopeType): String = when (t) {
    EnvelopeType.MONTHLY_SPENDING -> "Spending"
    EnvelopeType.FIXED_BILL -> "Bill"
    EnvelopeType.SINKING_FUND -> "Savings goal"
}

private fun formatPct(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

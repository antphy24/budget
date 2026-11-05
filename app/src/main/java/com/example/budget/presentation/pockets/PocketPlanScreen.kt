package com.example.budget.presentation.pockets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.budget.MoveReason
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.RuleKind
import com.example.budget.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketPlanScreen(
    viewModel: PocketPlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pocket Plan") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------- Distribute ----------------
            item { DistributeCard(state, viewModel) }

            // ---------------- Cycle & pockets ----------------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Budget cycle", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Payday: day", modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.changePayday(-1) }) { Icon(Icons.Default.Remove, "Earlier") }
                            Text("${state.payday}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.changePayday(1) }) { Icon(Icons.Default.Add, "Later") }
                        }
                        state.cycle?.let { c ->
                            val fmt = SimpleDateFormat("d MMM", Locale("id", "ID"))
                            Text(
                                "This cycle: ${fmt.format(Date(c.start))} – ${fmt.format(Date(c.end - 1))}. " +
                                    "Income received up to 3 days before payday counts for the new cycle.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        HorizontalDivider()
                        FundPicker(
                            label = "Income lands in",
                            funds = state.funds,
                            selectedId = state.holdingFundId,
                            onSelect = viewModel::setHoldingFund
                        )
                        FundPicker(
                            label = "Leftovers & remainder go to",
                            funds = state.funds,
                            selectedId = state.savingsFundId,
                            onSelect = viewModel::setSavingsFund
                        )
                        Text(
                            "At each new cycle, what's left in MONTHLY_SPENDING pockets moves to savings. " +
                                "FIXED_BILL and SINKING_FUND pockets keep their balance.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ---------------- Rules ----------------
            item {
                Column {
                    Text("Allocation rules", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Fixed pockets are filled first (up to their amount per cycle), then percentages " +
                            "of what's left. Percent total: ${formatNumber(state.percentTotal)}%" +
                            if (state.percentTotal > 100) " — over 100%!" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.percentTotal > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val rulePockets = state.funds.filter { it.id != state.holdingFundId && it.id != state.savingsFundId }
            items(rulePockets, key = { it.id }) { fund ->
                val rule = state.rules[fund.id]
                ListItem(
                    headlineContent = { Text(fund.name) },
                    supportingContent = { Text(fund.type.name, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Text(
                            when (rule?.kind) {
                                RuleKind.FIXED -> CurrencyFormatter.formatToIDR(rule!!.amount)
                                RuleKind.PERCENT -> "${formatNumber(rule!!.amount)}%"
                                null -> "No rule"
                            },
                            fontWeight = if (rule != null) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (rule != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable { viewModel.openRuleEditor(fund) }
                )
            }
        }
    }

    state.editingFund?.let { fund -> RuleDialog(fund, state, viewModel) }
}

@Composable
private fun DistributeCard(state: PocketPlanState, viewModel: PocketPlanViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Unallocated income", style = MaterialTheme.typography.titleMedium)
            Text(
                CurrencyFormatter.formatToIDR(state.holdingBalance),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (state.holdingFundId == null) {
                Text("Choose the pocket where income lands (below) to start.", style = MaterialTheme.typography.bodySmall)
            } else {

                if (state.sweepPending) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.sweepEnabled, onCheckedChange = viewModel::setSweepEnabled)
                        Text("New cycle: sweep last cycle's leftovers to savings first")
                    }
                }

                if (state.preview.isEmpty()) {
                    Text(
                        if (state.holdingBalance > 0) "No rules apply yet — add rules below." else "Nothing to distribute.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    state.preview.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                when (line.reason) {
                                    MoveReason.SWEEP -> "${line.fromName} → ${line.toName}"
                                    MoveReason.FIXED -> "${line.toName} (fixed)"
                                    MoveReason.PERCENT -> "${line.toName} (%)"
                                    MoveReason.REMAINDER -> "${line.toName} (remainder)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(CurrencyFormatter.formatToIDR(line.amount), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.leftInHolding >= 1.0) {
                        Text(
                            "Stays unallocated: ${CurrencyFormatter.formatToIDR(state.leftInHolding)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = viewModel::confirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            state.preview.isEmpty() && state.sweepPending ->
                                if (state.sweepEnabled) "Nothing to sweep — OK" else "Skip sweep this cycle"
                            state.preview.isNotEmpty() && state.preview.all { it.reason == MoveReason.SWEEP } -> "Sweep leftovers"
                            else -> "Distribute"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FundPicker(
    label: String,
    funds: List<FundCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = funds.firstOrNull { it.id == selectedId }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected?.name ?: "Choose…")
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
                funds.forEach { f ->
                    DropdownMenuItem(text = { Text(f.name) }, onClick = { onSelect(f.id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(fund: FundCategory, state: PocketPlanState, viewModel: PocketPlanViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissRuleEditor,
        title = { Text(fund.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.kindInput == null, onClick = { viewModel.onKindChanged(null) }, label = { Text("None") })
                    FilterChip(selected = state.kindInput == RuleKind.FIXED, onClick = { viewModel.onKindChanged(RuleKind.FIXED) }, label = { Text("Fixed Rp") })
                    FilterChip(selected = state.kindInput == RuleKind.PERCENT, onClick = { viewModel.onKindChanged(RuleKind.PERCENT) }, label = { Text("Percent") })
                }
                if (state.kindInput != null) {
                    OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = viewModel::onAmountChanged,
                        label = { Text(if (state.kindInput == RuleKind.FIXED) "Amount per cycle (Rp)" else "Percent of what's left (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (state.kindInput == RuleKind.FIXED)
                            "Filled up to this amount each cycle. A second income in the same cycle won't fill it twice."
                        else
                            "Taken from each income after all fixed pockets are filled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (fund.type != EnvelopeType.MONTHLY_SPENDING) {
                    Text("Tip: this is a ${fund.type.name} pocket, so its balance is kept between cycles.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveRule) { Text("Save") } },
        dismissButton = { TextButton(onClick = viewModel::dismissRuleEditor) { Text("Cancel") } }
    )
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

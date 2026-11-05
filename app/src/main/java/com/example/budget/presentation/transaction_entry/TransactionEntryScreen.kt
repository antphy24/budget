package com.example.budget.presentation.transaction_entry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.model.TransactionType
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun typeLabel(t: TransactionType) = when (t) {
    TransactionType.EXPENSE -> "Spend"
    TransactionType.INCOME -> "Receive"
    TransactionType.ASSET_TRANSFER -> "Transfer"
    TransactionType.BUDGET_ASSIGNMENT -> "Move"
}

@Composable
fun TransactionEntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionEntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.isSaved) { if (state.isSaved) onNavigateBack() }
    TransactionEntryContent(state, onEvent = viewModel::onEvent, onClose = onNavigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryContent(
    state: TransactionEntryState,
    onEvent: (TransactionEntryEvent) -> Unit,
    onClose: () -> Unit
) {
    val c = Budget.colors
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top: close + type
        Row(
            Modifier.padding(start = 8.dp, end = Gutter, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = c.ink) }
            Spacer(Modifier.width(4.dp))
            Segmented(
                options = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.ASSET_TRANSFER, TransactionType.BUDGET_ASSIGNMENT),
                selected = state.type,
                label = ::typeLabel,
                onSelect = { onEvent(TransactionEntryEvent.ChangedType(it)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Amount + note
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MoneyText(
                amount = state.amount,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.2f),
                color = when {
                    state.amount <= 0 -> c.inkFaint
                    state.type == TransactionType.INCOME -> c.emerald
                    else -> c.ink
                },
                symbolColor = c.inkSoft
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = state.memo,
                onValueChange = { onEvent(TransactionEntryEvent.EnteredMemo(it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(c.emerald),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (state.memo.isEmpty()) {
                            Text(
                                when (state.type) {
                                    TransactionType.EXPENSE -> "What was it for?"
                                    TransactionType.INCOME -> "Where's it from?"
                                    else -> "Add a note"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.inkFaint,
                                textAlign = TextAlign.Center
                            )
                        }
                        inner()
                    }
                }
            )
        }

        // Choices
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            val accounts = state.assets
            when (state.type) {
                TransactionType.EXPENSE -> {
                    ChipLine("Category") {
                        state.expenseCategories.forEach { cat ->
                            ChoiceChip(
                                cat.name, selected = state.expenseCategory == cat.name,
                                onClick = { onEvent(TransactionEntryEvent.SelectedExpenseCategory(cat.name)) },
                                icon = iconForName(cat.name)
                            )
                        }
                    }
                    ChipLine("From pocket") {
                        state.pocketsForPicking.forEach { f ->
                            ChoiceChip(f.name, state.primaryFundId == f.id, { onEvent(TransactionEntryEvent.SelectedPrimaryFund(f.id)) })
                        }
                    }
                    ChipLine("Paid with") {
                        accounts.forEach { a ->
                            ChoiceChip(a.name, state.primaryAssetId == a.id, { onEvent(TransactionEntryEvent.SelectedPrimaryAsset(a.id)) })
                        }
                    }
                }
                TransactionType.INCOME -> {
                    ChipLine("Into account") {
                        accounts.filter { it.type == AssetType.LIQUID }.forEach { a ->
                            ChoiceChip(a.name, state.primaryAssetId == a.id, { onEvent(TransactionEntryEvent.SelectedPrimaryAsset(a.id)) })
                        }
                    }
                    ChipLine("Into pocket") {
                        state.pocketsForPicking.sortedByDescending { it.id == state.holdingFundId }.forEach { f ->
                            ChoiceChip(f.name, state.primaryFundId == f.id, { onEvent(TransactionEntryEvent.SelectedPrimaryFund(f.id)) })
                        }
                    }
                }
                TransactionType.ASSET_TRANSFER -> {
                    ChipLine("From account") {
                        accounts.forEach { a ->
                            ChoiceChip(a.name, state.primaryAssetId == a.id, { onEvent(TransactionEntryEvent.SelectedPrimaryAsset(a.id)) })
                        }
                    }
                    ChipLine("To account") {
                        accounts.filter { it.id != state.primaryAssetId }.forEach { a ->
                            ChoiceChip(a.name, state.secondaryAssetId == a.id, { onEvent(TransactionEntryEvent.SelectedSecondaryAsset(a.id)) })
                        }
                    }
                }
                TransactionType.BUDGET_ASSIGNMENT -> {
                    ChipLine("From pocket") {
                        state.funds.forEach { f ->
                            ChoiceChip(f.name, state.primaryFundId == f.id, { onEvent(TransactionEntryEvent.SelectedPrimaryFund(f.id)) })
                        }
                    }
                    ChipLine("To pocket") {
                        state.pocketsForPicking.filter { it.id != state.primaryFundId }.forEach { f ->
                            ChoiceChip(f.name, state.secondaryFundId == f.id, { onEvent(TransactionEntryEvent.SelectedSecondaryFund(f.id)) })
                        }
                    }
                }
            }
            if (accounts.isEmpty() && state.type != TransactionType.BUDGET_ASSIGNMENT) {
                Text(
                    "Add an account in Settings first: a bank, an e-wallet or cash.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.amber,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp)
                )
            }
            ChipLine("When") {
                val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH) }
                val isToday = remember(state.timestamp) {
                    val a = Calendar.getInstance().apply { timeInMillis = state.timestamp }
                    val b = Calendar.getInstance()
                    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
                }
                ChoiceChip(
                    if (isToday) "Today, ${SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(state.timestamp))}" else fmt.format(Date(state.timestamp)),
                    selected = false,
                    onClick = { showDate = true },
                    icon = Icons.Outlined.CalendarToday
                )
            }
        }

        // Error line + keypad + save
        AnimatedVisibility(state.saveError != null) {
            Text(
                state.saveError ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ruby,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 6.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.sunk)
                .padding(horizontal = Gutter - 8.dp, vertical = 12.dp)
        ) {
            AmountKeypad(
                onDigits = { d -> onEvent(TransactionEntryEvent.EnteredTotalAmount(appendDigits(state.totalAmount, d))); onEvent(TransactionEntryEvent.DismissError) },
                onBackspace = { onEvent(TransactionEntryEvent.EnteredTotalAmount(state.totalAmount.dropLast(1))) },
                onClear = { onEvent(TransactionEntryEvent.EnteredTotalAmount("")) }
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = when (state.type) {
                    TransactionType.EXPENSE -> "Save spending"
                    TransactionType.INCOME -> "Save income"
                    TransactionType.ASSET_TRANSFER -> "Save transfer"
                    TransactionType.BUDGET_ASSIGNMENT -> "Move money"
                },
                onClick = { onEvent(TransactionEntryEvent.SaveTransaction) },
                enabled = state.amount > 0
            )
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.timestamp)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    dateState.selectedDateMillis?.let { picked ->
                        val cur = Calendar.getInstance().apply { timeInMillis = state.timestamp }
                        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = picked }
                        val next = Calendar.getInstance().apply {
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
                                cur.get(Calendar.HOUR_OF_DAY), cur.get(Calendar.MINUTE), 0)
                        }
                        onEvent(TransactionEntryEvent.ChangedTimestamp(next.timeInMillis))
                        showTime = true
                    }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = c.surface)
        ) { DatePicker(state = dateState) }
    }
    if (showTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = state.timestamp }
        val timeState = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = c.surface,
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    val next = Calendar.getInstance().apply {
                        timeInMillis = state.timestamp
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                    }
                    onEvent(TransactionEntryEvent.ChangedTimestamp(next.timeInMillis))
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Keep time") } },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
private fun ChipLine(label: String, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Budget.colors.inkSoft,
            modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

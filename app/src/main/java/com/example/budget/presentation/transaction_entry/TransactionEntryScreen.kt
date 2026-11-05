package com.example.budget.presentation.transaction_entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionEntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }

    if (state.saveError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(TransactionEntryEvent.DismissError) },
            title = { Text("Validation Error") },
            text = { Text(state.saveError!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.onEvent(TransactionEntryEvent.DismissError) }) {
                    Text("OK")
                }
            }
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.timestamp)
    
    // We recreate timePickerState whenever the dialog opens to ensure it has the latest time
    var timePickerState by remember { mutableStateOf<TimePickerState?>(null) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val dateMillis = datePickerState.selectedDateMillis
                    if (dateMillis != null) {
                        val currentCal = Calendar.getInstance().apply { timeInMillis = state.timestamp }
                        val newCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        newCal.set(Calendar.HOUR_OF_DAY, currentCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, currentCal.get(Calendar.MINUTE))
                        viewModel.onEvent(TransactionEntryEvent.ChangedTimestamp(newCal.timeInMillis))
                        
                        // Setup and show time picker next
                        timePickerState = TimePickerState(
                            initialHour = currentCal.get(Calendar.HOUR_OF_DAY),
                            initialMinute = currentCal.get(Calendar.MINUTE),
                            is24Hour = true
                        )
                        showTimePicker = true
                    }
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker && timePickerState != null) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val cal = Calendar.getInstance().apply { timeInMillis = state.timestamp }
                    cal.set(Calendar.HOUR_OF_DAY, timePickerState!!.hour)
                    cal.set(Calendar.MINUTE, timePickerState!!.minute)
                    viewModel.onEvent(TransactionEntryEvent.ChangedTimestamp(cal.timeInMillis))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState!!)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Transaction") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                // Transaction Type Selector
                ScrollableTabRow(
                    selectedTabIndex = TransactionType.values().indexOf(state.type),
                    edgePadding = 0.dp,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    TransactionType.values().forEach { type ->
                        Tab(
                            selected = state.type == type,
                            onClick = { viewModel.onEvent(TransactionEntryEvent.ChangedType(type)) },
                            text = { Text(type.name.replace("_", " ")) }
                        )
                    }
                }

                // Date/Time
                val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
                OutlinedTextField(
                    value = formatter.format(Date(state.timestamp)),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false, // Disable direct editing, handle click via box
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    label = { Text("Date & Time") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { showDatePicker = true }
                )

                OutlinedTextField(
                    value = state.totalAmount,
                    onValueChange = { viewModel.onEvent(TransactionEntryEvent.EnteredTotalAmount(it)) },
                    label = { Text("Total Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = { viewModel.onEvent(TransactionEntryEvent.EnteredMemo(it)) },
                    label = { Text("Memo") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                // Asset Fields based on Type
                when (state.type) {
                    TransactionType.INCOME -> {
                        ItemDropdown(
                            label = "Destination Asset",
                            items = state.assets.map { it.id to it.name },
                            selectedId = state.primaryAssetId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryAsset(it)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ItemDropdown(
                            label = "Fund Category (Optional)",
                            items = state.funds.map { it.id to it.name },
                            selectedId = state.primaryFundId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryFund(it)) }
                        )
                    }
                    TransactionType.EXPENSE -> {
                        ItemDropdown(
                            label = "Source Asset",
                            items = state.assets.map { it.id to it.name },
                            selectedId = state.primaryAssetId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryAsset(it)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ItemDropdown(
                            label = "Fund Category (Optional)",
                            items = state.funds.map { it.id to it.name },
                            selectedId = state.primaryFundId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryFund(it)) }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Expense Category", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.expenseCategories.forEach { category ->
                                FilterChip(
                                    selected = state.expenseCategory == category.name,
                                    onClick = { viewModel.onEvent(TransactionEntryEvent.SelectedExpenseCategory(category.name)) },
                                    label = { Text(category.name) }
                                )
                            }
                        }
                    }
                    TransactionType.ASSET_TRANSFER -> {
                        ItemDropdown(
                            label = "From Asset",
                            items = state.assets.map { it.id to it.name },
                            selectedId = state.primaryAssetId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryAsset(it)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ItemDropdown(
                            label = "To Asset",
                            items = state.assets.map { it.id to it.name },
                            selectedId = state.secondaryAssetId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedSecondaryAsset(it)) }
                        )
                    }
                    TransactionType.BUDGET_ASSIGNMENT -> {
                        ItemDropdown(
                            label = "Source Fund",
                            items = state.funds.map { it.id to it.name },
                            selectedId = state.primaryFundId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedPrimaryFund(it)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ItemDropdown(
                            label = "Destination Fund",
                            items = state.funds.map { it.id to it.name },
                            selectedId = state.secondaryFundId,
                            onItemSelected = { viewModel.onEvent(TransactionEntryEvent.SelectedSecondaryFund(it)) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { viewModel.onEvent(TransactionEntryEvent.SaveTransaction) },
                    enabled = state.totalAmount.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Transaction")
                }
            }
        }
    }
}

// Keeping the ItemDropdown helper the same as before
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDropdown(
    label: String,
    items: List<Pair<String, String>>,
    selectedId: String?,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.find { it.first == selectedId }?.second ?: "Select..."

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onItemSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

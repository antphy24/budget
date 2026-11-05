package com.example.budget.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.dateRange.start,
            initialSelectedEndDateMillis = state.dateRange.end
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDateRange(
                        start = dateRangePickerState.selectedStartDateMillis,
                        end = dateRangePickerState.selectedEndDateMillis
                    )
                    showDateRangePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setDateRange(null, null)
                    showDateRangePicker = false
                }) {
                    Text("Clear Filter")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ledger History") },
                actions = {
                    IconButton(onClick = { showDateRangePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Filter by Date")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val rangeText = if (state.dateRange.start != null && state.dateRange.end != null) {
                        "${dateFormat.format(Date(state.dateRange.start!!))} - ${dateFormat.format(Date(state.dateRange.end!!))}"
                    } else if (state.dateRange.start != null) {
                        "From ${dateFormat.format(Date(state.dateRange.start!!))}"
                    } else if (state.dateRange.end != null) {
                        "Until ${dateFormat.format(Date(state.dateRange.end!!))}"
                    } else {
                        "All Time"
                    }
                    Text(
                        text = "Summary: $rangeText",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.formatToIDR(state.income), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Expense", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.formatToIDR(state.expense), color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Difference", style = MaterialTheme.typography.bodySmall)
                            val diffColor = if (state.difference >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            Text(CurrencyFormatter.formatToIDR(state.difference), color = diffColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(state.transactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onDelete = { viewModel.deleteTransaction(transaction) }
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, onDelete: () -> Unit) {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    
    val (typeColor, amountColor) = when (transaction.type) {
        TransactionType.INCOME -> Color(0xFF4CAF50) to Color(0xFF4CAF50)
        TransactionType.EXPENSE -> Color(0xFFF44336) to Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurface to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.type.name.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                if (transaction.memo.isNotBlank()) {
                    Text(transaction.memo, style = MaterialTheme.typography.bodyMedium)
                }
                Text(formatter.format(Date(transaction.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CurrencyFormatter.formatToIDR(transaction.totalAmount),
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete")
                }
            }
        }
    }
}

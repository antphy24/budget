package com.example.budget.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.R
import com.example.budget.domain.budget.PaceStatus
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import com.example.budget.utils.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTransactionEntry: () -> Unit,
    onOpenPockets: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    historyViewModel: com.example.budget.presentation.history.TransactionHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Ask once for notification permission (Android 13+) so pocket alerts can show
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* nothing to do: alerts are simply skipped if denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = historyState.dateRange.start,
            initialSelectedEndDateMillis = historyState.dateRange.end
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    historyViewModel.setDateRange(
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
                    historyViewModel.setDateRange(null, null)
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
                title = { Text("Budget Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.toggleHideBalance() }) {
                        Icon(
                            painter = painterResource(id = if (state.isBalanceHidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                            contentDescription = "Toggle Balance Visibility"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToTransactionEntry) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Liquid Assets Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Safe to Spend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = CurrencyFormatter.formatToIDR(state.safeToSpendBalance, state.isBalanceHidden),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (state.unallocated >= 1.0 || state.sweepPending) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPockets() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (state.unallocated >= 1.0)
                                "${CurrencyFormatter.formatToIDR(state.unallocated, state.isBalanceHidden)} waiting to be distributed"
                            else
                                "New cycle started",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (state.sweepPending) "Sweep last cycle's leftovers to savings and fill your pockets · Tap"
                            else "Tap to split it into your pockets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Monthly Envelopes", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            // Funds List & Ledger History
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(state.activeSpendingEnvelopes) { fund ->
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fund.name, fontWeight = FontWeight.Bold)
                                val pace = state.pace[fund.id]
                                if (pace == null || pace.budgetThisCycle <= 0.0) {
                                    Text(fund.type.name, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    val paceColor = when (pace.status) {
                                        PaceStatus.OK -> MaterialTheme.colorScheme.primary
                                        PaceStatus.FAST -> Color(0xFFFF9800)
                                        PaceStatus.LOW, PaceStatus.OVERSPENT -> MaterialTheme.colorScheme.error
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { pace.usedFraction.toFloat().coerceIn(0f, 1f) },
                                        color = paceColor,
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val label = when (pace.status) {
                                        PaceStatus.OVERSPENT -> "Overspent"
                                        PaceStatus.LOW -> "Almost empty"
                                        PaceStatus.FAST -> "Spending fast"
                                        PaceStatus.OK -> "On track"
                                    }
                                    Text(
                                        text = "$label · ${(pace.usedFraction * 100).roundToInt()}% used, " +
                                            "${(pace.elapsedFraction * 100).roundToInt()}% of cycle · " +
                                            "${CurrencyFormatter.formatToIDR(pace.perDayLeft, state.isBalanceHidden)}/day",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (pace.status == PaceStatus.OK) MaterialTheme.colorScheme.onSurfaceVariant else paceColor
                                    )
                                }
                            }
                            Text(
                                text = CurrencyFormatter.formatToIDR(fund.balance, state.isBalanceHidden),
                                fontWeight = FontWeight.SemiBold,
                                color = if (fund.balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            IconButton(
                                onClick = { viewModel.togglePin(fund.id, !fund.isPinned) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = if (fund.isPinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_outline),
                                    contentDescription = "Pin",
                                    tint = if (fund.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ledger History", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showDateRangePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Filter by Date")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Summary Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            val rangeText = if (historyState.dateRange.start != null && historyState.dateRange.end != null) {
                                "${dateFormat.format(Date(historyState.dateRange.start!!))} - ${dateFormat.format(Date(historyState.dateRange.end!!))}"
                            } else if (historyState.dateRange.start != null) {
                                "From ${dateFormat.format(Date(historyState.dateRange.start!!))}"
                            } else if (historyState.dateRange.end != null) {
                                "Until ${dateFormat.format(Date(historyState.dateRange.end!!))}"
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
                                    Text(CurrencyFormatter.formatToIDR(historyState.income, state.isBalanceHidden), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Expense", style = MaterialTheme.typography.bodySmall)
                                    Text(CurrencyFormatter.formatToIDR(historyState.expense, state.isBalanceHidden), color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("Difference", style = MaterialTheme.typography.bodySmall)
                                    val diffColor = if (historyState.difference >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    Text(CurrencyFormatter.formatToIDR(historyState.difference, state.isBalanceHidden), color = diffColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                
                items(historyState.transactions) { transaction ->
                    com.example.budget.presentation.history.TransactionItem(
                        transaction = transaction,
                        onDelete = { historyViewModel.deleteTransaction(transaction) }
                    )
                }
            }
        }
    }
}

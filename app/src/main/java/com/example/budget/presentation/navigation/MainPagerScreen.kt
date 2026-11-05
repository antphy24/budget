package com.example.budget.presentation.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.budget.presentation.accounts.AccountsScreen
import com.example.budget.presentation.asset_management.AssetManagementScreen
import com.example.budget.presentation.dashboard.DashboardScreen
import com.example.budget.presentation.expense_categories.ExpenseCategoryListScreen
import com.example.budget.presentation.fund_management.FundManagementScreen
import com.example.budget.presentation.statistics.StatisticsScreen
import com.example.budget.presentation.pockets.PocketPlanScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPagerScreen(
    onNavigateToTransactionEntry: () -> Unit
) {
    var currentScreen by remember { mutableIntStateOf(0) }
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentScreen == 0,
                    onClick = { currentScreen = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PieChart, contentDescription = "Statistics") },
                    label = { Text("Statistics") },
                    selected = currentScreen == 1,
                    onClick = { currentScreen = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Accounts") },
                    label = { Text("Accounts") },
                    selected = currentScreen == 2,
                    onClick = { currentScreen = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MoreVert, contentDescription = "More") },
                    label = { Text("More") },
                    selected = currentScreen >= 3,
                    onClick = { showBottomSheet = true }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                0 -> DashboardScreen(
                    onNavigateToTransactionEntry = onNavigateToTransactionEntry,
                    onOpenPockets = { currentScreen = 6 }
                )
                1 -> StatisticsScreen()
                2 -> AccountsScreen()
                3 -> AssetManagementScreen()
                4 -> FundManagementScreen()
                5 -> ExpenseCategoryListScreen()
                6 -> PocketPlanScreen()
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "More Pages", 
                    style = MaterialTheme.typography.titleLarge, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Asset Management") },
                    leadingContent = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    modifier = Modifier.clickable {
                        currentScreen = 3
                        showBottomSheet = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Fund Management") },
                    leadingContent = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    modifier = Modifier.clickable {
                        currentScreen = 4
                        showBottomSheet = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Pocket Plan") },
                    supportingContent = { Text("Payday, allocation rules, distribute income") },
                    leadingContent = { Icon(Icons.Default.Savings, contentDescription = null) },
                    modifier = Modifier.clickable {
                        currentScreen = 6
                        showBottomSheet = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Expense Categories") },
                    leadingContent = { Icon(Icons.Default.Category, contentDescription = null) },
                    modifier = Modifier.clickable {
                        currentScreen = 5
                        showBottomSheet = false
                    }
                )
            }
        }
    }
}

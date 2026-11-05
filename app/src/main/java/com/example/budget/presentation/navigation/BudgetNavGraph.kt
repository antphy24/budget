package com.example.budget.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.budget.presentation.transaction_entry.TransactionEntryScreen

sealed class Screen(val route: String) {
    object MainPager : Screen("main_pager")
    object TransactionEntry : Screen("transaction_entry")
}

@Composable
fun BudgetNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.MainPager.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.MainPager.route) {
            MainPagerScreen(
                onNavigateToTransactionEntry = { navController.navigate(Screen.TransactionEntry.route) }
            )
        }
        composable(route = Screen.TransactionEntry.route) {
            TransactionEntryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

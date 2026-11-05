package com.example.budget.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.budget.presentation.activity.ActivityScreen
import com.example.budget.presentation.home.HomeScreen
import com.example.budget.presentation.inbox.InboxScreen
import com.example.budget.presentation.insights.InsightsScreen
import com.example.budget.presentation.plan.PlanScreen
import com.example.budget.presentation.settings.SettingsScreen
import com.example.budget.presentation.transaction_entry.TransactionEntryScreen
import com.example.budget.ui.theme.Budget
import com.example.budget.update.UpdateHost

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object TransactionEntry : Screen("entry")
    object Inbox : Screen("inbox")
    object Settings : Screen("settings?newAccount={newAccount}") {
        fun go(newAccount: Boolean = false) = "settings?newAccount=$newAccount"
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Activity("Activity", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Insights("Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    Plan("Plan", Icons.Outlined.Savings, Icons.Filled.Savings)
}

private const val SLIDE_MS = 280

@Composable
fun BudgetNavGraph(
    navController: NavHostController = rememberNavController(),
    openInbox: Boolean = false,
    onInboxOpened: () -> Unit = {}
) {
    // Screenshots shared to the app: show what was read
    LaunchedEffect(openInbox) {
        if (openInbox) {
            navController.navigate(Screen.Inbox.route) { launchSingleTop = true }
            onInboxOpened()
        }
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        enterTransition = { fadeIn(tween(SLIDE_MS)) + slideInHorizontally(tween(SLIDE_MS)) { it / 8 } },
        exitTransition = { fadeOut(tween(SLIDE_MS / 2)) },
        popEnterTransition = { fadeIn(tween(SLIDE_MS)) },
        popExitTransition = { fadeOut(tween(SLIDE_MS / 2)) + slideOutHorizontally(tween(SLIDE_MS)) { it / 8 } }
    ) {
        composable(Screen.Main.route) {
            MainTabs(
                onAdd = { navController.navigate(Screen.TransactionEntry.route) },
                onSettings = { newAccount -> navController.navigate(Screen.Settings.go(newAccount)) },
                onInbox = { navController.navigate(Screen.Inbox.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.Inbox.route) {
            InboxScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Screen.Settings.go(false)) }
            )
        }
        composable(
            Screen.TransactionEntry.route,
            enterTransition = { slideInVertically(tween(SLIDE_MS)) { it / 6 } + fadeIn(tween(SLIDE_MS)) },
            popExitTransition = { slideOutVertically(tween(SLIDE_MS)) { it / 6 } + fadeOut(tween(SLIDE_MS / 2)) }
        ) {
            TransactionEntryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            Screen.Settings.route,
            arguments = listOf(navArgument("newAccount") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            SettingsScreen(
                onBack = { navController.popBackStack() },
                startWithNewAccount = entry.arguments?.getBoolean("newAccount") ?: false
            )
        }
    }
    UpdateHost()
}

@Composable
private fun MainTabs(onAdd: () -> Unit, onSettings: (Boolean) -> Unit, onInbox: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    // Ask once for notification permission (Android 13+) so pocket alerts can show
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(Budget.colors.paper)) {
        Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { t ->
            when (t) {
                Tab.Home -> HomeScreen(
                    onOpenSettings = { onSettings(false) },
                    onAddAccount = { onSettings(true) },
                    onOpenPlan = { tab = Tab.Plan },
                    onSeeAll = { tab = Tab.Activity },
                    onInbox = onInbox
                )
                Tab.Activity -> ActivityScreen()
                Tab.Insights -> InsightsScreen()
                Tab.Plan -> PlanScreen()
            }
        }
        BottomBar(
            selected = tab,
            onSelect = { tab = it },
            onAdd = onAdd,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = Budget.colors
    Column(modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(Tab.Home, selected, onSelect)
            BarItem(Tab.Activity, selected, onSelect)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 56.dp, height = 44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(c.emerald)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction", tint = c.onEmerald)
                }
            }
            BarItem(Tab.Insights, selected, onSelect)
            BarItem(Tab.Plan, selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.BarItem(tab: Tab, selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Budget.colors
    val isSel = tab == selected
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(if (isSel) tab.selectedIcon else tab.icon, contentDescription = null, tint = if (isSel) c.ink else c.inkFaint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (isSel) c.ink else c.inkFaint)
    }
}

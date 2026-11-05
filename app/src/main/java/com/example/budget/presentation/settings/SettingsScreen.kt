package com.example.budget.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.AssetType
import com.example.budget.presentation.common.BudgetSheet
import com.example.budget.presentation.common.SheetTitle
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import com.example.budget.update.UpdateConfig
import com.example.budget.update.UpdateState
import com.example.budget.scan.PaymentNotificationListener
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    startWithNewAccount: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val update by viewModel.updates.state.collectAsState()
    val c = Budget.colors
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { if (startWithNewAccount) viewModel.newAccount() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
    LaunchedEffect(update) {
        when (val u = update) {
            is UpdateState.UpToDate -> { snackbar.showSnackbar("You have the latest version"); viewModel.updates.dismiss() }
            is UpdateState.Failed -> if (u.info == null) { snackbar.showSnackbar(u.message); viewModel.updates.dismiss() }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                PageHeader(
                    "Settings",
                    modifier = Modifier.statusBarsPadding(),
                    leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = c.ink) } }
                )
            }

            // Accounts
            item { SectionHeader("Accounts", action = "Add", onAction = viewModel::newAccount) }
            item {
                Group {
                    if (state.assets.isEmpty()) {
                        EmptyNote(
                            "No accounts yet",
                            "Add each place you keep money, like BRI, DANA or cash, with what's in it today.",
                            action = "Add account",
                            onAction = viewModel::newAccount
                        )
                    }
                    state.assets.forEachIndexed { i, a ->
                        if (i > 0) Hairline(inset = 70.dp)
                        AccountRow(a, state.hidden, onClick = { viewModel.editAccount(a) })
                    }
                }
            }
            if (state.assets.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = Gutter, vertical = 10.dp)) {
                        Text("Available money", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, modifier = Modifier.weight(1f))
                        MoneyText(state.liquidTotal, style = MaterialTheme.typography.titleSmall, hidden = state.hidden)
                    }
                }
                if (state.otherTotal != 0.0) {
                    item {
                        Row(Modifier.padding(horizontal = Gutter)) {
                            Text("Other assets", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, modifier = Modifier.weight(1f))
                            MoneyText(state.otherTotal, style = MaterialTheme.typography.titleSmall, hidden = state.hidden)
                        }
                    }
                }
            }

            // Categories
            item { SectionHeader("Spending categories", action = "Add", onAction = viewModel::newCategory) }
            item {
                FlowRow(
                    Modifier.padding(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.categories.forEach { cat ->
                        ChoiceChip(cat.name, selected = false, onClick = { viewModel.editCategory(cat) }, icon = iconForName(cat.name))
                    }
                }
            }

            // Smart capture
            item { SectionHeader("Smart capture") }
            item { SmartCapture(state, viewModel) }

            // Privacy
            item { SectionHeader("Privacy") }
            item {
                Group {
                    ListRow(
                        title = "Hide amounts",
                        subtitle = "Show dots instead of numbers, for using the app in public",
                        leading = { Glyph(Icons.Outlined.Visibility) },
                        trailing = {
                            Switch(
                                checked = state.hidden,
                                onCheckedChange = { viewModel.toggleHidden() },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.emerald, checkedThumbColor = c.onEmerald)
                            )
                        },
                        onClick = viewModel::toggleHidden
                    )
                }
            }

            // App
            item { SectionHeader("App") }
            item {
                Group {
                    ListRow(
                        title = "Check for updates",
                        subtitle = "Version ${state.version.name} (${state.version.code})",
                        leading = { Glyph(Icons.Outlined.SystemUpdate) },
                        trailing = {
                            if (update is UpdateState.Checking) CircularProgressIndicator(Modifier.size(20.dp), color = c.emerald, strokeWidth = 2.dp)
                        },
                        onClick = { viewModel.updates.check(manual = true) }
                    )
                    Hairline(inset = 70.dp)
                    ListRow(
                        title = "Copy download link",
                        subtitle = "Always the newest version, for another phone",
                        leading = { Glyph(Icons.Outlined.ContentCopy) },
                        onClick = { clipboard.setText(AnnotatedString(UpdateConfig.LATEST_APK_URL)) }
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    state.accountEditor?.let { e ->
        BudgetSheet(viewModel::closeAccount) {
            SheetTitle(if (e.id == null) "New account" else "Edit account")
            Column(Modifier.padding(horizontal = Gutter)) {
                Spacer(Modifier.height(8.dp))
                FieldLabel("Name")
                FieldWell(e.name, { v -> viewModel.updateAccount { it.copy(name = v) } }, placeholder = "BRI, DANA, Cash…")
                if (e.id == null) {
                    Spacer(Modifier.height(18.dp))
                    FieldLabel("Kind")
                    Segmented(
                        options = listOf(AssetType.LIQUID, AssetType.NON_LIQUID),
                        selected = e.type,
                        label = { if (it == AssetType.LIQUID) "Spendable" else "Other asset" },
                        onSelect = { t -> viewModel.updateAccount { it.copy(type = t) } }
                    )
                    Text(
                        if (e.type == AssetType.LIQUID) "Bank accounts, e-wallets and cash. Their money is split into your pockets."
                        else "Things like gold or a deposit you don't spend from. Counted separately.",
                        style = MaterialTheme.typography.bodySmall, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                FieldLabel(if (e.id == null) "Balance today" else "Current balance")
                FieldWell(
                    value = e.balanceText.filter { it.isDigit() }.let { if (it.isEmpty()) "" else groupRupiah(it.toDouble()) },
                    onValueChange = { v -> viewModel.updateAccount { it.copy(balanceText = v.filter { ch -> ch.isDigit() }) } },
                    prefix = "Rp",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number
                )
                Text(
                    if (e.id == null && e.type == AssetType.LIQUID) "This goes to To distribute, ready to split into your pockets."
                    else if (e.id != null && e.type == AssetType.LIQUID) "If it differs from the app, the difference is recorded as a correction in To distribute."
                    else "",
                    style = MaterialTheme.typography.bodySmall, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(if (e.id == null) "Add account" else "Save changes", onClick = viewModel::saveAccount)
                if (e.id != null) {
                    Spacer(Modifier.height(8.dp))
                    DangerTextButton("Delete account", viewModel::deleteAccount)
                }
            }
        }
    }

    state.categoryEditor?.let { e ->
        BudgetSheet(viewModel::closeCategory) {
            SheetTitle(if (e.category == null) "New category" else "Edit category")
            Column(Modifier.padding(horizontal = Gutter)) {
                Spacer(Modifier.height(8.dp))
                FieldWell(e.name, viewModel::updateCategoryName, placeholder = "Coffee, Parking, Laundry…")
                Spacer(Modifier.height(24.dp))
                PrimaryButton(if (e.category == null) "Add category" else "Save changes", onClick = viewModel::saveCategory)
                if (e.category != null) {
                    Spacer(Modifier.height(8.dp))
                    DangerTextButton("Delete category", viewModel::deleteCategory)
                }
            }
        }
    }
}

@Composable
private fun SmartCapture(state: SettingsState, viewModel: SettingsViewModel) {
    val c = Budget.colors
    val context = LocalContext.current
    var listenerOn by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) listenerOn = PaymentNotificationListener.isEnabled(context) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Group {
        ListRow(
            title = "Read on this phone",
            subtitle = "Receipts, screenshots and notifications are read on your phone. Nothing is uploaded.",
            leading = { Glyph(Icons.Outlined.Lock, tint = c.emerald, background = c.emeraldTint) }
        )
        Hairline()
        ListRow(
            title = "Read payment notifications",
            subtitle = if (listenerOn) "On. Choose which apps below" else "Off. Turn on Budget payment reader in Android's settings",
            leading = { Glyph(Icons.Outlined.NotificationsActive, tint = if (listenerOn) c.emerald else c.ink, background = if (listenerOn) c.emeraldTint else c.sunk) },
            trailing = { Text(if (listenerOn) "Change" else "Turn on", style = MaterialTheme.typography.labelLarge, color = c.emerald) },
            onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        )
        if (listenerOn) {
            Hairline()
            if (state.paymentApps.isEmpty()) {
                Text(
                    "Apps that send payment notifications (with \"Rp\") will appear here after their next notification. Then tick the ones to read.",
                    style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                state.paymentApps.entries.sortedBy { it.value.lowercase() }.forEachIndexed { i, (pkg, label) ->
                    if (i > 0) Hairline()
                    ListRow(
                        title = label,
                        trailing = {
                            Switch(
                                checked = pkg in state.watchedApps,
                                onCheckedChange = { on -> viewModel.setAppWatched(pkg, on) },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.emerald, checkedThumbColor = c.onEmerald)
                            )
                        },
                        onClick = { viewModel.setAppWatched(pkg, pkg !in state.watchedApps) }
                    )
                }
            }
        }
    }
    Text(
        "Only ticked apps are read, and only messages that mention an amount. What's found waits in To review until you add it. When you correct a category or pocket there, Budget remembers it for that merchant.",
        style = MaterialTheme.typography.bodySmall, color = c.inkSoft,
        modifier = Modifier.padding(horizontal = Gutter, vertical = 10.dp)
    )
}

@Composable
private fun AccountRow(a: Asset, hidden: Boolean, onClick: () -> Unit) {
    val n = a.name.lowercase()
    val icon = when {
        a.type == AssetType.NON_LIQUID -> Icons.Outlined.AccountBalance
        listOf("cash", "tunai", "dompet").any { n.contains(it) } -> Icons.Outlined.Payments
        listOf("dana", "ovo", "gopay", "shopee", "wallet", "linkaja").any { n.contains(it) } -> Icons.Outlined.Wallet
        else -> Icons.Outlined.AccountBalance
    }
    ListRow(
        title = a.name,
        subtitle = if (a.type == AssetType.LIQUID) "Spendable" else "Other asset",
        leading = { Glyph(icon) },
        trailing = { MoneyText(a.balance, style = MaterialTheme.typography.titleSmall, hidden = hidden) },
        onClick = onClick
    )
}

@Composable
private fun DangerTextButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Budget.colors.ruby)
    }
}

package com.example.budget.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.budget.PaceStatus
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.presentation.common.TxDetailSheet
import com.example.budget.presentation.common.TxRow
import com.example.budget.presentation.common.TxRowView
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeActions(
    val onToggleHidden: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onDistribute: () -> Unit = {},
    val onOpenPlan: () -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onTxClick: (TxRow) -> Unit = {}
)

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddAccount: () -> Unit,
    onOpenPlan: () -> Unit,
    onSeeAll: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<TxRow?>(null) }

    HomeContent(
        state = state,
        actions = HomeActions(
            onToggleHidden = viewModel::toggleHidden,
            onOpenSettings = onOpenSettings,
            onAddAccount = onAddAccount,
            onDistribute = onOpenPlan,
            onOpenPlan = onOpenPlan,
            onSeeAll = onSeeAll,
            onTxClick = { selected = it }
        )
    )

    selected?.let { row ->
        TxDetailSheet(
            row = row,
            assets = state.assets,
            funds = state.funds,
            hidden = state.hidden,
            onDelete = { viewModel.delete(row.tx); selected = null },
            onDismiss = { selected = null }
        )
    }
}

@Composable
fun HomeContent(state: HomeState, actions: HomeActions) {
    val c = Budget.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.paper),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { TopRow(state.hidden, actions) }
        item { Hero(state) }

        if (!state.hasAccounts) {
            item {
                Spacer(Modifier.height(12.dp))
                Group {
                    ListRow(
                        title = "Add your first account",
                        subtitle = "A bank, an e-wallet or cash: wherever your money is",
                        leading = { Glyph(Icons.Outlined.AccountBalanceWallet, tint = c.emerald, background = c.emeraldTint) },
                        trailing = { SoftButton("Add", onClick = actions.onAddAccount) },
                        onClick = actions.onAddAccount
                    )
                }
            }
        }

        if (state.unallocated >= 1.0 || state.sweepPending) {
            item {
                Spacer(Modifier.height(12.dp))
                Group {
                    ListRow(
                        title = if (state.unallocated >= 1.0) "${rupiah(state.unallocated, state.hidden)} ready" else "New cycle started",
                        subtitle = if (state.unallocated >= 1.0) "Split it into your pockets" else "Move leftovers to savings",
                        leading = { Glyph(Icons.Outlined.Inbox, tint = c.emerald, background = c.emeraldTint) },
                        trailing = { SoftButton("Distribute", onClick = actions.onDistribute, contentColor = c.onEmerald, background = c.emerald) },
                        onClick = actions.onDistribute
                    )
                }
            }
        }

        item { SectionHeader("Pockets", action = "Plan", onAction = actions.onOpenPlan) }
        if (state.loaded && state.spending.isEmpty()) {
            item {
                Group {
                    EmptyNote(
                        title = "No spending pockets yet",
                        body = "A pocket holds money for one purpose, like food or transport. Create a few, then distribute your income into them.",
                        action = "Create pockets",
                        onAction = actions.onOpenPlan
                    )
                }
            }
        } else {
            item {
                Group {
                    state.spending.forEachIndexed { i, p ->
                        if (i > 0) Hairline()
                        PocketRow(p, state.hidden, onClick = actions.onOpenPlan)
                    }
                }
            }
        }

        if (state.reserves.isNotEmpty()) {
            item { SectionHeader("Set aside") }
            item {
                Group {
                    state.reserves.forEachIndexed { i, f ->
                        if (i > 0) Hairline(inset = 70.dp)
                        ReserveRow(f, state.hidden, onClick = actions.onOpenPlan)
                    }
                }
            }
        }

        item { SectionHeader("Recent", action = if (state.recent.isNotEmpty()) "See all" else null, onAction = actions.onSeeAll) }
        item {
            Group {
                if (state.recent.isEmpty()) {
                    EmptyNote("Nothing recorded yet", "Tap + to log what you spend or receive.")
                } else {
                    state.recent.forEachIndexed { i, row ->
                        if (i > 0) Hairline(inset = 70.dp)
                        TxRowView(row, state.hidden, onClick = { actions.onTxClick(row) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopRow(hidden: Boolean, actions: HomeActions) {
    val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(Date()) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = Gutter, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(today, style = MaterialTheme.typography.titleSmall, color = Budget.colors.inkSoft, modifier = Modifier.weight(1f))
        IconButton(onClick = actions.onToggleHidden) {
            Icon(
                if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                tint = Budget.colors.ink
            )
        }
        IconButton(onClick = actions.onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Budget.colors.ink)
        }
    }
}

/** The one bold element: safe-to-spend on deep emerald, with the cycle as a strip of days. */
@Composable
private fun Hero(state: HomeState) {
    val c = Budget.colors
    val mint = Color(0xFF8FE0C2)
    val paydayFmt = remember { SimpleDateFormat("d MMM", Locale.ENGLISH) }
    Column(
        Modifier
            .padding(horizontal = Gutter - 4.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(c.hero)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text("Safe to spend", style = MaterialTheme.typography.labelLarge, color = c.onHeroSoft)
        Spacer(Modifier.height(6.dp))
        MoneyText(
            amount = state.safeToSpend,
            style = MaterialTheme.typography.displayLarge,
            color = c.onHero,
            symbolColor = c.onHeroSoft,
            hidden = state.hidden
        )
        Spacer(Modifier.height(22.dp))

        // One segment per day of the cycle
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (d in 1..state.daysInCycle) {
                val color = when {
                    d < state.dayOfCycle -> c.onHero.copy(alpha = 0.55f)
                    d == state.dayOfCycle -> mint
                    else -> c.onHero.copy(alpha = 0.14f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Day ${state.dayOfCycle} of ${state.daysInCycle}",
                style = MaterialTheme.typography.labelMedium,
                color = c.onHeroSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (state.cycleEnd > 0) "Payday ${paydayFmt.format(Date(state.cycleEnd))}, in ${state.daysToPayday} ${if (state.daysToPayday == 1) "day" else "days"}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = c.onHero
            )
        }
    }
}

@Composable
private fun PocketRow(p: PocketView, hidden: Boolean, onClick: () -> Unit) {
    val c = Budget.colors
    val pace = p.pace
    val statusColor = when (pace.status) {
        PaceStatus.OK -> c.inkSoft
        PaceStatus.FAST -> c.amber
        PaceStatus.LOW, PaceStatus.OVERSPENT -> c.ruby
    }
    val caption = when {
        pace.budgetThisCycle <= 0.0 && pace.balance <= 0.0 -> "Empty. Distribute income to fill it"
        pace.status == PaceStatus.OVERSPENT -> "Overspent. Move money in to cover it"
        pace.status == PaceStatus.LOW -> "Almost empty"
        pace.status == PaceStatus.FAST -> "Too fast, ${rupiah(pace.perDayLeft, hidden)} a day"
        else -> "${rupiah(pace.perDayLeft, hidden)} a day until payday"
    }
    ListRow(
        title = p.fund.name,
        leading = { Glyph(iconForName(p.fund.name)) },
        trailing = {
            MoneyText(
                p.fund.balance,
                style = MaterialTheme.typography.titleSmall,
                hidden = hidden,
                color = if (p.fund.balance < 0) c.ruby else c.ink
            )
        },
        bottom = {
            Column {
                PaceRail(
                    remainingFraction = if (pace.budgetThisCycle > 0) (pace.balance / pace.budgetThisCycle).toFloat() else 0f,
                    expectedRemainingFraction = (1.0 - pace.elapsedFraction).toFloat(),
                    status = pace.status
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        caption, style = MaterialTheme.typography.bodySmall, color = statusColor,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (pace.budgetThisCycle > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hidden) "" else "of ${rupiahShort(pace.budgetThisCycle)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkFaint
                        )
                    }
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ReserveRow(f: FundCategory, hidden: Boolean, onClick: () -> Unit) {
    ListRow(
        title = f.name,
        subtitle = when (f.type) {
            EnvelopeType.FIXED_BILL -> "Bill"
            EnvelopeType.SINKING_FUND -> "Savings"
            EnvelopeType.MONTHLY_SPENDING -> "Savings"
        },
        leading = { Glyph(iconForName(f.name)) },
        trailing = { MoneyText(f.balance, style = MaterialTheme.typography.titleSmall, hidden = hidden) },
        onClick = onClick
    )
}

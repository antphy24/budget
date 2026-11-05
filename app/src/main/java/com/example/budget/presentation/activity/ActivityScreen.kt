package com.example.budget.presentation.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.presentation.common.TxDetailSheet
import com.example.budget.presentation.common.TxRow
import com.example.budget.presentation.common.TxRowView
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget

@Composable
fun ActivityScreen(viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<TxRow?>(null) }

    ActivityContent(
        state = state,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onFilter = viewModel::setFilter,
        onTxClick = { selected = it }
    )

    selected?.let { row ->
        TxDetailSheet(
            row = row, assets = state.assets, funds = state.funds, hidden = state.hidden,
            onDelete = { viewModel.delete(row.tx); selected = null },
            onDismiss = { selected = null }
        )
    }
}

@Composable
fun ActivityContent(
    state: ActivityState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFilter: (ActivityFilter) -> Unit,
    onTxClick: (TxRow) -> Unit
) {
    val c = Budget.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.paper),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { PageHeader("Activity", modifier = Modifier.statusBarsPadding()) }

        // Period switcher + totals
        item {
            Row(
                Modifier.padding(horizontal = Gutter - 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous cycle", tint = c.ink)
                }
                Text(
                    state.periodLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNext, enabled = !state.isCurrent) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next cycle",
                        tint = if (state.isCurrent) c.inkFaint else c.ink
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = Gutter).padding(top = 4.dp, bottom = 16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Spent", style = MaterialTheme.typography.labelMedium, color = c.inkSoft)
                    MoneyText(state.spent, style = MaterialTheme.typography.headlineMedium, hidden = state.hidden)
                }
                Column(Modifier.weight(1f)) {
                    Text("Received", style = MaterialTheme.typography.labelMedium, color = c.inkSoft)
                    MoneyText(state.income, style = MaterialTheme.typography.headlineMedium, hidden = state.hidden, color = c.emerald)
                }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityFilter.entries.forEach { f ->
                    ChoiceChip(f.label, selected = state.filter == f, onClick = { onFilter(f) })
                }
            }
        }

        if (state.groups.isEmpty()) {
            item {
                EmptyNote(
                    title = "Nothing in this cycle",
                    body = if (state.isCurrent) "What you log with + shows up here, grouped by day." else "Try another cycle with the arrows above."
                )
            }
        }

        items(state.groups, key = { it.dayStart }) { group ->
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Gutter, end = Gutter, top = 22.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(group.label, style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
                    if (group.spent > 0) {
                        MoneyText(
                            -group.spent,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.inkSoft,
                            hidden = state.hidden
                        )
                    }
                }
                Group {
                    group.rows.forEachIndexed { i, row ->
                        if (i > 0) Hairline(inset = 70.dp)
                        TxRowView(row, state.hidden, onClick = { onTxClick(row) })
                    }
                }
            }
        }
    }
}

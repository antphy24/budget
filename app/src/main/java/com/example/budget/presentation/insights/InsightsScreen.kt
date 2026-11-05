package com.example.budget.presentation.insights

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    InsightsContent(state, onPrevious = viewModel::previous, onNext = viewModel::next)
}

@Composable
fun InsightsContent(state: InsightsState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val c = Budget.colors
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(c.paper),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item { PageHeader("Insights", modifier = Modifier.statusBarsPadding()) }
        item {
            Row(Modifier.padding(horizontal = Gutter - 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous cycle", tint = c.ink) }
                Text(state.periodLabel, style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.weight(1f))
                IconButton(onClick = onNext, enabled = !state.isCurrent) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next cycle", tint = if (state.isCurrent) c.inkFaint else c.ink)
                }
            }
        }

        // Headline figure
        item {
            Column(Modifier.padding(horizontal = Gutter).padding(top = 4.dp)) {
                Text(if (state.isCurrent) "Spent so far" else "Spent", style = MaterialTheme.typography.labelLarge, color = c.inkSoft)
                MoneyText(state.spent, style = MaterialTheme.typography.displayLarge, hidden = state.hidden)
                Spacer(Modifier.height(6.dp))
                val change = state.changeVsPrevious
                val changeText = when {
                    change == null -> null
                    abs(change) < 0.02 -> "About the same as last cycle"
                    change < 0 -> "${(abs(change) * 100).roundToInt()}% less than last cycle"
                    else -> "${(change * 100).roundToInt()}% more than last cycle"
                }
                Text(
                    buildString {
                        append("${rupiah(state.perDay, state.hidden)} a day on average")
                        if (changeText != null) append(". $changeText")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        change == null || abs(change) < 0.02 -> c.inkSoft
                        change < 0 -> c.emerald
                        else -> c.amber
                    }
                )
            }
        }

        // In vs out
        item {
            Spacer(Modifier.height(20.dp))
            Group {
                Column(Modifier.padding(16.dp)) {
                    val max = maxOf(state.income, state.spent, 1.0)
                    FlowBar("Received", state.income, (state.income / max).toFloat(), c.emerald, state.hidden)
                    Spacer(Modifier.height(14.dp))
                    FlowBar("Spent", state.spent, (state.spent / max).toFloat(), c.ink, state.hidden)
                    Spacer(Modifier.height(14.dp))
                    val net = state.income - state.spent
                    Row {
                        Text("Left over", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, modifier = Modifier.weight(1f))
                        MoneyText(net, style = MaterialTheme.typography.titleSmall, hidden = state.hidden, color = if (net < 0) c.ruby else c.ink)
                    }
                }
            }
        }

        if (state.byCategory.isEmpty()) {
            item {
                EmptyNote(
                    "No spending in this cycle",
                    "Once you log spending, you'll see which categories and pockets it went to."
                )
            }
        } else {
            item { SectionHeader("By category") }
            item { ShareGroup(state.byCategory, state.spent, state.hidden, withIcons = true) }
            item { SectionHeader("By pocket") }
            item { ShareGroup(state.byPocket, state.spent, state.hidden, withIcons = true) }
            if (state.topNotes.isNotEmpty()) {
                item { SectionHeader("Most frequent") }
                item { ShareGroup(state.topNotes, state.spent, state.hidden, withIcons = false, showCount = true) }
            }
        }
    }
}

@Composable
private fun FlowBar(label: String, amount: Double, fraction: Float, color: Color, hidden: Boolean) {
    val c = Budget.colors
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "flow")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft, modifier = Modifier.weight(1f))
            MoneyText(amount, style = MaterialTheme.typography.titleSmall, hidden = hidden)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.sunk)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ShareGroup(shares: List<Share>, total: Double, hidden: Boolean, withIcons: Boolean, showCount: Boolean = false) {
    val c = Budget.colors
    Group {
        shares.forEachIndexed { i, s ->
            if (i > 0) Hairline(inset = if (withIcons) 70.dp else 16.dp)
            val pct = if (total > 0) (s.amount / total * 100).roundToInt() else 0
            ListRow(
                title = s.name,
                leading = if (withIcons) ({ Glyph(iconForName(s.name)) }) else null,
                trailing = { MoneyText(s.amount, style = MaterialTheme.typography.titleSmall, hidden = hidden) },
                bottom = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val f by animateFloatAsState(s.fraction, tween(600), label = "share")
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.sunk)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(c.ink)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (showCount) "${s.count}×" else "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.inkSoft,
                            modifier = Modifier.widthIn(min = 32.dp)
                        )
                    }
                }
            )
        }
    }
}

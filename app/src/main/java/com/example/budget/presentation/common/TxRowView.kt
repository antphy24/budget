package com.example.budget.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.budget.ui.components.Glyph
import com.example.budget.ui.components.ListRow
import com.example.budget.ui.components.MoneyText
import com.example.budget.ui.components.SignMode
import com.example.budget.ui.theme.Budget

/** One transaction row: glyph, title, subtitle, amount. Spending is ink, income emerald. */
@Composable
fun TxRowView(
    row: TxRow,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Budget.colors
    ListRow(
        title = row.title,
        subtitle = row.subtitle.ifEmpty { row.time },
        modifier = modifier,
        leading = {
            Glyph(
                row.icon,
                tint = if (row.kind == TxKind.In) c.emerald else c.ink,
                background = if (row.kind == TxKind.In) c.emeraldTint else c.sunk
            )
        },
        trailing = {
            MoneyText(
                amount = row.amount,
                style = MaterialTheme.typography.titleSmall,
                hidden = hidden,
                sign = when (row.kind) {
                    TxKind.In -> SignMode.Always
                    TxKind.Out -> SignMode.Auto
                    else -> SignMode.None
                },
                color = when (row.kind) {
                    TxKind.In -> c.emerald
                    TxKind.Out -> c.ink
                    else -> c.inkSoft
                }
            )
        },
        onClick = onClick
    )
}

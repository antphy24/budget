package com.example.budget.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.TransactionType
import com.example.budget.ui.components.*
import com.example.budget.ui.theme.Budget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom sheet with the app's surface colour and a small drag handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Budget.colors.paper,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Budget.colors.line)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            content = content
        )
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Budget.colors.ink)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Budget.colors.inkSoft)
        }
    }
}

/** Details of one transaction, with delete (asks once to confirm). */
@Composable
fun TxDetailSheet(
    row: TxRow,
    assets: Map<String, Asset>,
    funds: Map<String, FundCategory>,
    hidden: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Budget.colors
    var confirming by remember { mutableStateOf(false) }
    val tx = row.tx
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH) }

    BudgetSheet(onDismiss) {
        Column(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = c.inkSoft)
            Spacer(Modifier.height(4.dp))
            MoneyText(
                amount = row.amount,
                style = MaterialTheme.typography.displayMedium,
                hidden = hidden,
                sign = if (row.kind == TxKind.In) SignMode.Always else if (row.kind == TxKind.Out) SignMode.Auto else SignMode.None,
                color = if (row.kind == TxKind.In) c.emerald else c.ink
            )
        }
        Spacer(Modifier.height(12.dp))
        Group {
            DetailLine("When", dateFmt.format(Date(tx.timestamp)))
            Hairline()
            DetailLine(
                "Type",
                when (tx.type) {
                    TransactionType.EXPENSE -> "Spending"
                    TransactionType.INCOME -> "Income"
                    TransactionType.ASSET_TRANSFER -> "Transfer between accounts"
                    TransactionType.BUDGET_ASSIGNMENT -> "Moved between pockets"
                }
            )
            tx.primaryAssetId?.let { assets[it] }?.let {
                Hairline(); DetailLine(if (tx.type == TransactionType.ASSET_TRANSFER) "From account" else "Account", it.name)
            }
            tx.secondaryAssetId?.let { assets[it] }?.let { Hairline(); DetailLine("To account", it.name) }
            tx.primaryFundId?.let { funds[it] }?.let {
                Hairline(); DetailLine(if (tx.type == TransactionType.BUDGET_ASSIGNMENT) "From pocket" else "Pocket", it.name)
            }
            tx.secondaryFundId?.let { funds[it] }?.let { Hairline(); DetailLine("To pocket", it.name) }
            if (tx.type == TransactionType.EXPENSE) {
                Hairline(); DetailLine("Category", tx.expenseCategory)
            }
            if (tx.memo.isNotBlank()) {
                Hairline(); DetailLine("Note", tx.memo)
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = Gutter - 4.dp)) {
            if (!confirming) {
                PrimaryButton(
                    "Delete transaction",
                    onClick = { confirming = true },
                    color = c.rubyTint,
                    contentColor = c.ruby
                )
            } else {
                Column {
                    Text(
                        "Balances of the accounts and pockets involved will be restored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.inkSoft,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Delete for good", onClick = onDelete, color = c.ruby, contentColor = c.surface)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Budget.colors.inkSoft, modifier = Modifier.width(112.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Budget.colors.ink, modifier = Modifier.weight(1f))
    }
}

package com.example.budget.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.ui.components.iconForName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class TxKind { Out, In, Transfer, Move }

/** A transaction ready to show in a list. */
data class TxRow(
    val tx: Transaction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** Signed from the user's point of view: spending negative, income positive, moves 0-sign */
    val amount: Double,
    val kind: TxKind,
    val time: String
)

data class DayGroup(val label: String, val dayStart: Long, val rows: List<TxRow>, val spent: Double)

object TxPresenter {
    private val timeFmt = SimpleDateFormat("HH:mm", Locale("id", "ID"))

    fun row(tx: Transaction, assets: Map<String, Asset>, funds: Map<String, FundCategory>): TxRow {
        val asset = tx.primaryAssetId?.let { assets[it]?.name }
        val asset2 = tx.secondaryAssetId?.let { assets[it]?.name }
        val fund = tx.primaryFundId?.let { funds[it]?.name }
        val fund2 = tx.secondaryFundId?.let { funds[it]?.name }
        val memo = tx.memo.trim()
        val time = timeFmt.format(Date(tx.timestamp))
        return when (tx.type) {
            TransactionType.EXPENSE -> {
                val category = tx.expenseCategory.ifBlank { "Uncategorized" }
                TxRow(
                    tx = tx,
                    title = memo.ifEmpty { category },
                    subtitle = listOfNotNull(if (memo.isNotEmpty()) category else null, fund, asset).joinToString(", "),
                    icon = iconForName(category),
                    amount = -tx.totalAmount,
                    kind = TxKind.Out,
                    time = time
                )
            }
            TransactionType.INCOME -> TxRow(
                tx = tx,
                title = memo.ifEmpty { "Income" },
                subtitle = listOfNotNull(asset?.let { "Into $it" }, fund).joinToString(", "),
                icon = Icons.Outlined.Payments,
                amount = tx.totalAmount,
                kind = TxKind.In,
                time = time
            )
            TransactionType.ASSET_TRANSFER -> TxRow(
                tx = tx,
                title = memo.ifEmpty { "Transfer" },
                subtitle = "${asset ?: "?"} to ${asset2 ?: "?"}",
                icon = Icons.Outlined.SwapHoriz,
                amount = tx.totalAmount,
                kind = TxKind.Transfer,
                time = time
            )
            TransactionType.BUDGET_ASSIGNMENT -> TxRow(
                tx = tx,
                title = memo.ifEmpty { "Moved between pockets" },
                subtitle = "${fund ?: "?"} to ${fund2 ?: "?"}",
                icon = Icons.Outlined.SyncAlt,
                amount = tx.totalAmount,
                kind = TxKind.Move,
                time = time
            )
        }
    }

    /** Groups rows (newest first) by calendar day with "Today" / "Yesterday" labels. */
    fun groupByDay(rows: List<TxRow>, now: Long = System.currentTimeMillis()): List<DayGroup> {
        val todayStart = startOfDay(now)
        val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
        val dayFmt = SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH)
        val dayFmtYear = SimpleDateFormat("EEEE, d MMM yyyy", Locale.ENGLISH)
        val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        return rows.groupBy { startOfDay(it.tx.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) ->
                val year = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.YEAR)
                val label = when (day) {
                    todayStart -> "Today"
                    yesterdayStart -> "Yesterday"
                    else -> (if (year == thisYear) dayFmt else dayFmtYear).format(Date(day))
                }
                DayGroup(label, day, list, list.filter { it.kind == TxKind.Out }.sumOf { -it.amount })
            }
    }

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

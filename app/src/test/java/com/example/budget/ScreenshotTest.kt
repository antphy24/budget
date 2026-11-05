package com.example.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.budget.MoveReason
import com.example.budget.domain.budget.PocketPaceCalculator
import com.example.budget.domain.model.*
import com.example.budget.presentation.activity.ActivityContent
import com.example.budget.presentation.activity.ActivityFilter
import com.example.budget.presentation.activity.ActivityState
import com.example.budget.presentation.common.TxPresenter
import com.example.budget.presentation.home.HomeActions
import com.example.budget.presentation.home.HomeContent
import com.example.budget.presentation.home.HomeState
import com.example.budget.presentation.home.PocketView
import com.example.budget.presentation.insights.InsightsContent
import com.example.budget.presentation.insights.InsightsState
import com.example.budget.presentation.insights.Share
import com.example.budget.presentation.navigation.BottomBar
import com.example.budget.presentation.navigation.Tab
import com.example.budget.presentation.plan.PlanActions
import com.example.budget.presentation.plan.PlanContent
import com.example.budget.presentation.plan.PlanState
import com.example.budget.presentation.plan.PreviewLine
import com.example.budget.presentation.transaction_entry.TransactionEntryContent
import com.example.budget.presentation.transaction_entry.TransactionEntryState
import com.example.budget.ui.theme.Budget
import com.example.budget.ui.theme.BudgetTheme
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Renders each screen with realistic sample data, in light and dark. */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 1.0)

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = System.currentTimeMillis()
    private val cycle = BudgetCycle.containing(now, 27)

    private val bri = Asset("a1", "BRI", AssetType.LIQUID, 4_350_000.0, true)
    private val dana = Asset("a2", "DANA", AssetType.LIQUID, 212_500.0)
    private val cash = Asset("a3", "Cash", AssetType.LIQUID, 185_000.0)
    private val assets = listOf(bri, dana, cash)

    private val holding = FundCategory("f0", "To distribute", EnvelopeType.SINKING_FUND, 1_250_000.0)
    private val food = FundCategory("f1", "Food & drinks", EnvelopeType.MONTHLY_SPENDING, 640_000.0)
    private val transport = FundCategory("f2", "Transport", EnvelopeType.MONTHLY_SPENDING, 95_000.0)
    private val jajan = FundCategory("f3", "Jajan", EnvelopeType.MONTHLY_SPENDING, -18_000.0)
    private val kos = FundCategory("f4", "Kos", EnvelopeType.FIXED_BILL, 800_000.0)
    private val savings = FundCategory("f5", "Savings", EnvelopeType.SINKING_FUND, 2_100_000.0)
    private val wedding = FundCategory("f6", "Wedding", EnvelopeType.SINKING_FUND, 1_255_000.0)
    private val funds = listOf(holding, food, transport, jajan, kos, savings, wedding)

    private fun tx(id: String, type: TransactionType, ago: Long, amount: Double, asset: String?, fund: String?, cat: String = "Uncategorized", memo: String = "", asset2: String? = null, fund2: String? = null) =
        Transaction(id, type, now - ago, amount, asset, asset2, fund, fund2, cat, memo)

    private val txs = listOf(
        tx("t1", TransactionType.EXPENSE, TimeUnit.HOURS.toMillis(1), 28_000.0, "a2", "f1", "Food & drinks", "Nasi kuning"),
        tx("t2", TransactionType.EXPENSE, TimeUnit.HOURS.toMillis(3), 15_000.0, "a3", "f2", "Transport", "Bensin"),
        tx("t3", TransactionType.INCOME, day + 3_600_000, 1_250_000.0, "a1", "f0", memo = "Honor pembinaan OSN"),
        tx("t4", TransactionType.EXPENSE, day + 7_200_000, 46_500.0, "a1", "f3", "Groceries", "Indomaret"),
        tx("t5", TransactionType.ASSET_TRANSFER, 2 * day, 200_000.0, "a1", null, asset2 = "a3", memo = "Tarik tunai ATM"),
        tx("t6", TransactionType.EXPENSE, 3 * day, 100_000.0, "a1", "f1", "Phone & internet", "Kuota"),
        tx("t7", TransactionType.BUDGET_ASSIGNMENT, 4 * day, 300_000.0, null, "f0", fund2 = "f2", memo = "Distributed")
    )
    private val aMap = assets.associateBy { it.id }
    private val fMap = funds.associateBy { it.id }
    private val rows = txs.map { TxPresenter.row(it, aMap, fMap) }

    private fun shot(dark: Boolean, content: @Composable () -> Unit) {
        paparazzi.snapshot {
            BudgetTheme(useDarkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Budget.colors.paper)) { content() }
            }
        }
    }

    private fun homeState(): HomeState {
        val spent = mapOf("f1" to 860_000.0, "f2" to 205_000.0, "f3" to 168_000.0)
        val spending = listOf(food, transport, jajan).map {
            PocketView(it, PocketPaceCalculator.calculate(it.id, it.balance, spent[it.id] ?: 0.0, cycle, now))
        }
        val dayOfCycle = ((now - cycle.start) / day).toInt() + 1
        val days = ((cycle.end - cycle.start + day / 2) / day).toInt()
        return HomeState(
            loaded = true, safeToSpend = spending.sumOf { it.fund.balance },
            cycleStart = cycle.start, cycleEnd = cycle.end, dayOfCycle = dayOfCycle, daysInCycle = days,
            daysToPayday = days - dayOfCycle + 1, unallocated = 1_250_000.0,
            spending = spending, reserves = listOf(savings, kos, wedding), recent = rows.take(5),
            assets = aMap, funds = fMap
        )
    }

    @Test fun home_light() = shot(false) { HomeWithBar() }
    @Test fun home_dark() = shot(true) { HomeWithBar() }

    @Composable private fun HomeWithBar() {
        HomeContent(homeState(), HomeActions())
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { BottomBar(Tab.Home, {}, {}) }
    }

    @Test fun home_empty() = shot(false) {
        HomeContent(HomeState(loaded = true, cycleStart = cycle.start, cycleEnd = cycle.end, daysInCycle = 31, dayOfCycle = 10, daysToPayday = 22, hasAccounts = false), HomeActions())
    }

    @Test fun activity_light() = shot(false) {
        ActivityContent(
            ActivityState(periodLabel = "27 Oct – 26 Nov", income = 1_250_000.0, spent = 189_500.0, groups = TxPresenter.groupByDay(rows), assets = aMap, funds = fMap),
            {}, {}, {}, {}
        )
    }

    @Test fun insights_light() = shot(false) { InsightsContent(insights(), {}, {}) }
    @Test fun insights_dark() = shot(true) { InsightsContent(insights(), {}, {}) }

    private fun insights() = InsightsState(
        periodLabel = "27 Oct – 26 Nov", spent = 1_233_000.0, income = 5_850_000.0, perDay = 123_300.0, changeVsPrevious = -0.12,
        byCategory = listOf(
            Share("Food & drinks", 612_000.0, 1f, 23), Share("Transport", 205_000.0, 0.33f, 11),
            Share("Groceries", 168_000.0, 0.27f, 4), Share("Phone & internet", 100_000.0, 0.16f, 1)
        ),
        byPocket = listOf(Share("Food & drinks", 712_000.0, 1f, 24), Share("Transport", 205_000.0, 0.29f, 11), Share("Jajan", 168_000.0, 0.24f, 4)),
        topNotes = listOf(Share("Nasi kuning", 168_000.0, 1f, 6), Share("Bensin", 90_000.0, 0.54f, 6))
    )

    @Test fun plan_light() = shot(false) {
        PlanContent(
            PlanState(
                loaded = true, funds = funds,
                rules = mapOf(
                    "f1" to AllocationRule("f1", RuleKind.FIXED, 1_500_000.0),
                    "f2" to AllocationRule("f2", RuleKind.FIXED, 300_000.0),
                    "f4" to AllocationRule("f4", RuleKind.FIXED, 800_000.0),
                    "f3" to AllocationRule("f3", RuleKind.PERCENT, 10.0)
                ),
                holdingFundId = "f0", savingsFundId = "f5", holdingBalance = 1_250_000.0,
                preview = listOf(
                    PreviewLine("To distribute", "Food & drinks", 640_000.0, MoveReason.FIXED),
                    PreviewLine("To distribute", "Transport", 95_000.0, MoveReason.FIXED),
                    PreviewLine("To distribute", "Jajan", 51_500.0, MoveReason.PERCENT),
                    PreviewLine("To distribute", "Savings", 463_500.0, MoveReason.REMAINDER)
                ),
                fixedTotal = 2_600_000.0, percentTotal = 10.0
            ),
            PlanActions()
        )
    }

    @Test fun entry_light() = shot(false) { TransactionEntryContent(entry(), {}, {}) }
    @Test fun entry_dark() = shot(true) { TransactionEntryContent(entry(), {}, {}) }

    private fun entry() = TransactionEntryState(
        totalAmount = "28000", memo = "Nasi kuning", primaryAssetId = "a2", primaryFundId = "f1",
        expenseCategory = "Food & drinks", assets = assets, funds = funds, holdingFundId = "f0",
        expenseCategories = listOf("Food & drinks", "Groceries", "Transport", "Bills", "Shopping", "Health").mapIndexed { i, n -> ExpenseCategory("c$i", n) }
    )
}

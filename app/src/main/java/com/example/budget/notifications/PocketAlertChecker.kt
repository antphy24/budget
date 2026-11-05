package com.example.budget.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.budget.domain.budget.BudgetCycle
import com.example.budget.domain.budget.PaceStatus
import com.example.budget.domain.budget.PocketPaceCalculator
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.utils.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Checks a pocket's spending pace after an expense and posts a notification
 * when it gets worse (FAST → LOW → OVERSPENT). Each level is notified at most
 * once per pocket per cycle.
 */
@Singleton
class PocketAlertChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val fundCategoryRepository: FundCategoryRepository,
    private val settingsRepository: SettingsRepository
) {

    /** App-wide scope so the check survives the entry screen closing right after saving. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onExpenseSaved(fundId: String) {
        scope.launch { runCatching { checkAfterExpense(fundId) } }
    }

    suspend fun checkAfterExpense(fundId: String) {
        val fund = fundCategoryRepository.getFundCategoryById(fundId) ?: return
        if (fund.type != EnvelopeType.MONTHLY_SPENDING) return
        if (fundId == settingsRepository.holdingFundId.value) return
        if (fundId == settingsRepository.savingsFundId.value) return

        val now = System.currentTimeMillis()
        val cycle = BudgetCycle.containing(now, settingsRepository.payday.value)
        val spent = transactionRepository.getExpenseByFundSinceOnce(cycle.start)[fundId] ?: 0.0
        val pace = PocketPaceCalculator.calculate(fundId, fund.balance, spent, cycle, now)

        if (pace.status == PaceStatus.OK) return
        val alreadyNotified = settingsRepository.getNotifiedSeverity(fundId, cycle.start)
        if (pace.status.severity <= alreadyNotified) return

        val usedPct = (pace.usedFraction * 100).roundToInt()
        val elapsedPct = (pace.elapsedFraction * 100).roundToInt()
        val (title, text) = when (pace.status) {
            PaceStatus.OVERSPENT -> "${fund.name} is overspent" to
                "Balance ${CurrencyFormatter.formatToIDR(fund.balance)}. Move money from another pocket to cover it."
            PaceStatus.LOW -> "${fund.name} is almost empty" to
                "$usedPct% used, ${CurrencyFormatter.formatToIDR(fund.balance)} left for ${pace.daysRemaining} days " +
                "(~${CurrencyFormatter.formatToIDR(pace.perDayLeft)}/day)."
            PaceStatus.FAST -> "${fund.name} is running low" to
                "$usedPct% used but only $elapsedPct% of the cycle has passed. " +
                "~${CurrencyFormatter.formatToIDR(pace.perDayLeft)}/day left until payday."
            PaceStatus.OK -> return
        }

        if (post(fundId.hashCode(), title, text)) {
            settingsRepository.setNotifiedSeverity(fundId, cycle.start, pace.status.severity)
        }
    }

    /** @return true if the notification was posted */
    private fun post(id: Int, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Pocket alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warns when a pocket is running low"
                }
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "pocket_alerts"
    }
}

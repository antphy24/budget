package com.example.budget.scan

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.budget.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reads payment notifications (e.g. "Pembayaran QRIS Rp25.000 berhasil") from the apps
 * you tick in Settings and turns them into drafts to review. Other notifications are ignored;
 * nothing is stored except the names of apps that mentioned rupiah, so you can pick them.
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    @Inject lateinit var scan: ScanRepository
    @Inject lateinit var settings: SettingsRepository

    private val recent = ArrayDeque<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val body = "$title\n$text".trim()
        if (!MONEY.containsMatchIn(body)) return

        val label = appLabel(pkg)
        settings.rememberPaymentApp(pkg, label)
        if (pkg !in settings.watchedApps.value) return

        // Apps often re-post the same notification; handle each one once
        val key = "$pkg|$body"
        if (key in recent) return
        recent.addLast(key)
        if (recent.size > 30) recent.removeFirst()

        scan.readNotification(label, body, sbn.postTime)
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        /** "Rp 25.000", "Rp25.000,00", "IDR 1,250,000" */
        private val MONEY = Regex("""(?i)(rp\.?|idr)\s?\d""")

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }
}

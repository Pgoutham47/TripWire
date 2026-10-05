package com.tripwire.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tripwire.app.MainActivity
import com.tripwire.app.R
import com.tripwire.app.intervene.WarningActivity
import com.tripwire.app.intervene.WarningRequest
import com.tripwire.app.ui.Ui
import com.tripwire.core.pipeline.QuietNotice

/** Notification channels and every notification Tripwire posts (INT-01, UI-04). */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_PROTECTION, "Protection status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The permanent notice that Tripwire is protecting you."
                    setShowBadge(false)
                },
                NotificationChannel(CH_NOTICES, "Pattern notices", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A quiet notice when a chat matches a known scam pattern."
                },
                NotificationChannel(CH_WARNINGS, "Scam warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Full-screen warnings before an install, a payment or screen sharing."
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CH_FOLLOWUP, "Follow-ups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Check-ins after you went ahead, and ally alert status."
                },
            ),
        )
    }

    /** UI-04: the foreground service's permanent notification. */
    fun protectionNotification(lang: String, activeCases: Int, paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            paused -> Ui.t("status.paused", lang)
            activeCases > 0 -> Ui.t("status.watching_cases", lang, "n" to activeCases.toString())
            else -> Ui.t("status.protecting", lang)
        }
        return NotificationCompat.Builder(context, CH_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(Ui.t("app.name", lang))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Journey 3: one quiet notice, tapping opens the timeline. */
    fun quietNotice(n: QuietNotice) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_CASE_ID, n.caseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, n.caseId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(
            n.caseId.hashCode(),
            NotificationCompat.Builder(context, CH_NOTICES)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    /**
     * Shows the full-screen warning. With "display over other apps" granted, Android lets a
     * background app start an activity, so the warning appears on top at once. Without it, the
     * fallback is a full-screen-intent notification (PRD 15.2).
     */
    fun launchWarning(req: WarningRequest) {
        val intent = WarningActivity.intent(context, req).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(context)) {
            runCatching { context.startActivity(intent); return }
        }
        val pi = PendingIntent.getActivity(context, req.interventionId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(
            WARNING_ID,
            NotificationCompat.Builder(context, CH_WARNINGS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(req.warning.headline)
                .setContentText(req.warning.reasons.firstOrNull() ?: req.warning.closing)
                .setStyle(NotificationCompat.BigTextStyle().bigText((req.warning.reasons + req.warning.closing).joinToString(" ")))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelWarning() = nm.cancel(WARNING_ID)

    fun followUp(id: Int, title: String, text: String, intent: Intent) {
        val pi = PendingIntent.getActivity(context, id, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(
            id,
            NotificationCompat.Builder(context, CH_FOLLOWUP)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun post(id: Int, n: Notification) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try {
                nm.notify(id, n)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS refused; the home screen shows it as a missing protection (ONB-07).
            }
        }
    }

    companion object {
        const val CH_PROTECTION = "protection"
        const val CH_NOTICES = "notices"
        const val CH_WARNINGS = "warnings"
        const val CH_FOLLOWUP = "followup"
        const val PROTECTION_ID = 1
        const val WARNING_ID = 2
    }
}

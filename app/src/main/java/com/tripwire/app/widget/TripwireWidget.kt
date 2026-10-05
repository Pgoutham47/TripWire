package com.tripwire.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.tripwire.app.MainActivity
import com.tripwire.app.R
import com.tripwire.app.graph
import com.tripwire.app.ui.Ui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget: protection status and suspicious chats at a glance, with "I already paid"
 * one tap away. It shows counts only, never names, numbers or message text, because a home
 * screen is visible to anyone holding the phone.
 */
class TripwireWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        /** Redraws every placed widget. Cheap when none are placed; safe to call from any thread. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, TripwireWidget::class.java))
            if (ids.isEmpty()) return
            val graph = app.graph
            graph.scope.launch(Dispatchers.IO) {
                val s = graph.settings.current
                val now = System.currentTimeMillis()
                val open = runCatching { graph.store.allCaseStates().count { it.isOpen } }.getOrDefault(0)
                val views = render(app, s.language, open, s.isPaused(now), s.paidShortcutUntil > now)
                manager.updateAppWidget(ids, views)
            }
        }

        private fun render(context: Context, lang: String, open: Int, paused: Boolean, paidPinned: Boolean): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_tripwire)
            val alarm = paused || open > 0 || paidPinned
            v.setTextViewText(
                R.id.widget_status,
                when {
                    paused -> Ui.t("status.paused", lang)
                    open == 1 -> Ui.t("status.watching_case", lang)
                    open > 1 -> Ui.t("status.watching_cases", lang, "n" to open.toString())
                    else -> Ui.t("status.protecting", lang)
                },
            )
            v.setTextViewText(R.id.widget_detail, Ui.t(if (open > 0) "widget.open_detail" else "home.offline", lang))
            v.setTextViewText(R.id.widget_paid, Ui.t("btn.already_paid", lang))
            v.setInt(R.id.widget_icon, "setColorFilter", ContextCompat.getColor(context, if (alarm) R.color.widget_danger else R.color.widget_safe))

            val open0 = PendingIntent.getActivity(
                context, 300, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val paid = PendingIntent.getActivity(
                context, 301,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAID_CASE, "")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open0)
            v.setOnClickPendingIntent(R.id.widget_paid, paid)
            return v
        }
    }
}

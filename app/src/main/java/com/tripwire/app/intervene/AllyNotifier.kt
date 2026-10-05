package com.tripwire.app.intervene

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tripwire.app.AppGraph
import com.tripwire.app.ui.Ui
import com.tripwire.core.evidence.AllyAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tells the trusted ally when a serious warning fires (ALY-02, ALY-03). The alert carries the
 * scam type, stage and time only, never message content (privacy rule 3).
 *
 * Delivery: Tripwire does not hold SMS permissions (PRD 15.2) and has no push backend yet, so it
 * prepares the text in the user's SMS app and asks the user to send it with one tap. Push
 * delivery through FCM (ALY-04) replaces this once the thin cloud service exists.
 */
class AllyNotifier(private val context: Context, private val graph: AppGraph) {

    fun send(alert: AllyAlert) {
        graph.scope.launch(Dispatchers.IO) {
            val ally = graph.store.dao.allies().firstOrNull() ?: return@launch
            val s = graph.settings.current
            val owner = s.ownerName.ifBlank { Ui.t("app.name", s.language) }
            val text = alert.text(graph.pipeline.explain, owner, s.language)
            val compose = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${ally.phone}")).putExtra("sms_body", text)
            graph.store.dao.updateAlly(ally.copy(alertsSent = ally.alertsSent + 1))
            graph.notifier.followUp(
                id = ALLY_NOTICE_ID,
                title = Ui.t("ally.tap_to_send", s.language, "ally" to ally.name),
                text = text,
                intent = compose,
            )
        }
    }

    /** One-tap call to the ally (ALY-06). Uses the dialer, so no call permission is needed. */
    fun dialIntent(phone: String) = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))

    companion object {
        const val ALLY_NOTICE_ID = 40
    }
}

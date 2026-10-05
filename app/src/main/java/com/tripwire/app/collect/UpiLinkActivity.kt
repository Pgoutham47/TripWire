package com.tripwire.app.collect

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.ComponentActivity
import com.tripwire.app.graph
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.parse.UpiUri
import kotlinx.coroutines.launch

/**
 * Handles `upi://pay` links and QR payloads (SIG-08, Appendix D). It reads the payee, asks the
 * pipeline for a verdict, then either shows the warning or passes the unchanged link to the
 * user's payment app. It has no UI of its own.
 */
class UpiLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val payment = uri?.toString()?.let { UpiUri.parse(it) }
        if (uri == null || payment == null) {
            uri?.let { forward(this, it) }
            finish()
            return
        }
        val graph = applicationContext.graph
        // Where the link was tapped, when that is a chat, payment or browser app; otherwise it is
        // shown simply as a "UPI link", never as a raw system package.
        val apps = graph.pack.apps
        val known = (apps.messaging + apps.payment).map { it.packageName }.toSet() + apps.linkInstallers
        val from = referrer?.host?.takeIf { it in known } ?: packageName
        lifecycleScope.launch {
            val decision = graph.guardian.decide(
                Observation(
                    type = EventType.UPI_LINK_OPENED,
                    app = from,
                    timestamp = System.currentTimeMillis(),
                    source = "upi_link",
                    upi = payment,
                ),
            )
            if (decision.show) {
                graph.guardian.showWarning(decision, forwardUri = uri.toString())
            } else {
                forward(this@UpiLinkActivity, uri)
            }
            finish()
        }
    }

    companion object {
        /** Opens the link in a payment app, never in Tripwire itself. */
        fun forward(context: Context, uri: Uri) {
            val view = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(view, null).apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, UpiLinkActivity::class.java)))
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }
}

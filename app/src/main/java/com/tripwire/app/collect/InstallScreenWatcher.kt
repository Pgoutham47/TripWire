package com.tripwire.app.collect

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tripwire.app.engine.Guardian
import com.tripwire.app.graph
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation

/**
 * SIG-11: sees Android's install screen open, so the warning appears before an app from a link
 * is installed. Optional on-screen reading (PRD 15.2): the service config limits events to the
 * system installer packages, and only the dialog's title and question are read. It is never
 * declared an accessibility tool (PRD 15.3), and Tripwire works fully without it (INT-09 covers
 * installs it misses).
 */
class InstallScreenWatcher : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastLabel: String? = null
    private var lastAt = 0L

    override fun onServiceConnected() {
        instance = this
        Guardian.debug { "install screen watcher connected" }
        // Android can restart the service (another accessibility client connecting does this),
        // so a "Don't install" from before the restart is picked up again here.
        if (cancelUntil > System.currentTimeMillis()) pollCancel()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Guardian.debug { "install screen watcher destroyed" }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in INSTALLERS) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        if (cancelUntil > System.currentTimeMillis() && tryCancel()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        // Only the "Do you want to install this app?" screen; progress and done screens are ignored.
        if (!isConfirmScreen(root)) return
        val label = firstText(root, TITLE_IDS)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 60 }

        val now = System.currentTimeMillis()
        if (label == lastLabel && now - lastAt < REPEAT_MS) return // one screen fires many events
        lastLabel = label
        lastAt = now
        Guardian.debug { "install screen seen from $pkg, app name found=${label != null}" }

        applicationContext.graph.guardian.submit(
            Observation(
                type = EventType.INSTALL_SCREEN_OPENED,
                app = pkg,
                timestamp = now,
                source = "screen",
                installedLabel = label,
                // The system installer only shows this screen for apps from a file or link; store
                // installs never open it, so the install source check fails (CHK-03).
                installerPackage = pkg,
            ),
        )
    }

    /** Checks until the installer is back in front, after the warning closes, then cancels it. */
    private fun pollCancel() {
        if (tryCancel()) return
        if (System.currentTimeMillis() < cancelUntil) handler.postDelayed({ pollCancel() }, POLL_MS)
    }

    /** Taps the installer's own Cancel if its confirm screen is in front. */
    private fun tryCancel(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() !in INSTALLERS || !isConfirmScreen(root)) return false
        val cancel = findById(root, CANCEL_IDS)
        val done = cancel?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true || performGlobalAction(GLOBAL_ACTION_BACK)
        cancelUntil = 0
        Guardian.debug { "install cancelled from the warning: $done" }
        return true
    }

    private fun isConfirmScreen(root: AccessibilityNodeInfo): Boolean =
        findById(root, QUESTION_IDS) != null ||
            root.findAccessibilityNodeInfosByText("install this app").isNotEmpty() ||
            root.findAccessibilityNodeInfosByText("इंस्टॉल करना").isNotEmpty()

    private fun firstText(root: AccessibilityNodeInfo, ids: List<String>): String? =
        findById(root, ids)?.text?.toString()

    private fun findById(root: AccessibilityNodeInfo, ids: List<String>): AccessibilityNodeInfo? {
        val pkg = root.packageName?.toString()
        for (id in ids) {
            val full = if (':' in id) listOf(id) else listOfNotNull(pkg?.let { "$it:id/$id" }, "com.android.packageinstaller:id/$id")
            for (f in full) root.findAccessibilityNodeInfosByViewId(f).firstOrNull()?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile private var instance: InstallScreenWatcher? = null

        /** Until when a "Don't install" is waiting for the installer to come back in front. */
        @Volatile private var cancelUntil = 0L

        /** System installers that show the confirm screen for apps from a file or link. */
        val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.miui.packageinstaller")

        private val TITLE_IDS = listOf("android:id/alertTitle", "app_name", "app_label")
        private val QUESTION_IDS = listOf("install_confirm_question")
        private val CANCEL_IDS = listOf("android:id/button2", "cancel_button")

        private const val REPEAT_MS = 60_000L
        private const val POLL_MS = 500L
        private const val CANCEL_WINDOW_MS = 3 * 60_000L

        /**
         * "Don't install": cancels the install screen the warning was shown over. Returns false
         * when on-screen reading is off, so the caller can fall back.
         */
        fun cancelInstall(): Boolean {
            val svc = instance
            Guardian.debug { "cancel install requested, watcher running=${svc != null}" }
            if (svc == null) return false
            cancelUntil = System.currentTimeMillis() + CANCEL_WINDOW_MS
            svc.handler.post { svc.pollCancel() }
            return true
        }

        fun enabled(context: Context): Boolean {
            val on = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = ComponentName(context, InstallScreenWatcher::class.java)
            return on.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
        }
    }
}

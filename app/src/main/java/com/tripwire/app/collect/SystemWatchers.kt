package com.tripwire.app.collect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.tripwire.app.AppGraph
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SIG-06: a newly installed app and where it came from. PACKAGE_ADDED is not delivered to
 * manifest receivers on modern Android, so the foreground service registers this at runtime.
 */
class InstallWatcher(private val context: Context, private val graph: AppGraph) : BroadcastReceiver() {

    fun register() {
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
    }

    fun unregister() = runCatching { context.unregisterReceiver(this) }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return // updates are not new installs
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        val installer = installerOf(pm, pkg)
        graph.guardian.submit(
            Observation(
                type = EventType.APP_INSTALLED,
                app = installer ?: "unknown",
                timestamp = System.currentTimeMillis(),
                source = "package",
                installedPackage = pkg,
                installedLabel = label,
                installerPackage = installer,
            ),
        )
    }

    companion object {
        /** The installing app, or the app that started the install when the installer is the system one. */
        fun installerOf(pm: PackageManager, pkg: String): String? = runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(pkg)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(pkg)
            }
        }.getOrNull()
    }
}

/**
 * SIG-07 and SIG-13: a payment app or a remote-access app coming to the foreground, read from
 * usage events. It polls quickly only while a case is active, to keep battery cost low (PRD 14.2).
 */
class UsageWatcher(private val context: Context, private val graph: AppGraph) {
    private var job: Job? = null
    private var lastQuery = System.currentTimeMillis()
    private var lastForeground: String? = null

    fun start() {
        if (job?.isActive == true) return
        job = graph.scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (granted(context)) poll()
                delay(if (activeCase()) FAST_MS else SLOW_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun poll() {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery - 1000, now)
        lastQuery = now
        val e = UsageEvents.Event()
        val payment = graph.pack.apps.payment.map { it.packageName }.toSet()
        val remote = graph.pack.apps.remoteAccess.map { it.packageName }.toSet()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = e.packageName
            if (pkg == lastForeground) continue
            lastForeground = pkg
            val type = when (pkg) {
                in payment -> EventType.PAYMENT_APP_OPENED
                in remote -> EventType.REMOTE_APP_OPENED
                else -> null
            } ?: continue
            graph.guardian.submit(Observation(type, pkg, e.timeStamp, "usage"))
        }
    }

    private fun activeCase(): Boolean = runCatching {
        val now = System.currentTimeMillis()
        graph.store.allCaseStates().any { it.isOpen && now - it.lastEventAt < 7 * 24 * 3600_000L }
    }.getOrDefault(false)

    companion object {
        const val FAST_MS = 1_000L
        const val SLOW_MS = 5_000L

        fun granted(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

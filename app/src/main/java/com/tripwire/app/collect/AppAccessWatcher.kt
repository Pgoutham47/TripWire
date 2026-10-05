package com.tripwire.app.collect

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.tripwire.app.AppGraph
import com.tripwire.app.engine.Guardian
import com.tripwire.core.guard.GuardAlert
import com.tripwire.core.guard.InstalledApp
import com.tripwire.core.guard.PhoneCheckup

/** Installed apps as the phone checkup and the access alarm see them. */
object InstalledApps {
    private const val ACCESSIBILITY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    private const val LISTENERS = "enabled_notification_listeners"

    fun accessibilityUri(): Uri = Settings.Secure.getUriFor(ACCESSIBILITY)
    fun listenersUri(): Uri = Settings.Secure.getUriFor(LISTENERS)

    /** Packages with an enabled accessibility service: they can read and control the screen. */
    fun withScreenControl(context: Context): Set<String> = packagesIn(context, ACCESSIBILITY)

    /** Packages that can read every notification, including one-time codes. */
    fun withNotificationAccess(context: Context): Set<String> = packagesIn(context, LISTENERS)

    private fun packagesIn(context: Context, key: String): Set<String> =
        Settings.Secure.getString(context.contentResolver, key).orEmpty()
            .split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()

    fun describe(context: Context, pkg: String, screen: Set<String>, listeners: Set<String>): InstalledApp? {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val granted = { perm: String -> pm.checkPermission(perm, pkg) == PackageManager.PERMISSION_GRANTED }
        return InstalledApp(
            packageName = pkg,
            label = pm.getApplicationLabel(info).toString(),
            installer = InstallWatcher.installerOf(pm, pkg),
            system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            readsSms = granted(Manifest.permission.READ_SMS) || granted(Manifest.permission.RECEIVE_SMS),
            controlsScreen = pkg in screen,
            readsNotifications = pkg in listeners,
        )
    }

    /** Every app a person installed. System apps are skipped before the slow lookups. */
    fun installedByUser(context: Context): List<InstalledApp> {
        val screen = withScreenControl(context)
        val listeners = withNotificationAccess(context)
        return context.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .mapNotNull { describe(context, it.packageName, screen, listeners) }
    }
}

/**
 * The banking-malware alarm: OTP-stealing apps ask for screen control (accessibility) or
 * notification access. When an app from outside an app store gets either, Tripwire alerts at once
 * and the alert opens the page where it can be turned off.
 */
class AppAccessWatcher(private val context: Context, private val graph: AppGraph) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val checkup = PhoneCheckup(graph.pack)
    private var screen = emptySet<String>()
    private var listeners = emptySet<String>()

    fun register() {
        screen = InstalledApps.withScreenControl(context)
        listeners = InstalledApps.withNotificationAccess(context)
        context.contentResolver.registerContentObserver(InstalledApps.accessibilityUri(), false, this)
        context.contentResolver.registerContentObserver(InstalledApps.listenersUri(), false, this)
    }

    fun unregister() = runCatching { context.contentResolver.unregisterContentObserver(this) }

    override fun onChange(selfChange: Boolean) {
        val nowScreen = InstalledApps.withScreenControl(context)
        val nowListeners = InstalledApps.withNotificationAccess(context)
        val newScreen = nowScreen - screen
        val newListeners = nowListeners - listeners
        screen = nowScreen
        listeners = nowListeners
        newScreen.forEach { check(it, screenAccess = true, nowScreen, nowListeners) }
        (newListeners - newScreen).forEach { check(it, screenAccess = false, nowScreen, nowListeners) }
    }

    private fun check(pkg: String, screenAccess: Boolean, nowScreen: Set<String>, nowListeners: Set<String>) {
        if (pkg == context.packageName) return
        val app = InstalledApps.describe(context, pkg, nowScreen, nowListeners) ?: return
        if (!checkup.isRiskyGrant(app)) return
        Guardian.debug { "risky access granted to an app from outside a store (screen=$screenAccess)" }
        val lang = graph.settings.current.language
        val access = graph.pipeline.explain.string(if (screenAccess) "guard.access.screen" else "guard.access.notifications", lang)
        val alert = graph.pipeline.guardAlert(GuardAlert.Kind.APP_ACCESS, null, mapOf("app" to app.label, "access" to access))
        val settings = Intent(if (screenAccess) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        graph.notifier.guardAlert(alert, settings)
    }
}

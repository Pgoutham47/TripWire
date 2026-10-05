package com.tripwire.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.tripwire.app.collect.InstallScreenWatcher
import com.tripwire.app.collect.UsageWatcher

/** One permission from PRD 15.2, with its reason and what stops working without it (ONB-03). */
enum class Perm(val key: String, val required: Boolean) {
    LISTENER("listener", true),
    POST("post", false),
    CONTACTS("contacts", false),
    OVERLAY("overlay", false),
    USAGE("usage", false),
    SCREEN("screen", false),
    BATTERY("battery", false);

    val titleKey get() = "perm.$key.title"
    val reasonKey get() = "perm.$key.reason"
    val withoutKey get() = "perm.$key.without"
}

object Permissions {
    fun granted(context: Context, p: Perm): Boolean = when (p) {
        Perm.LISTENER -> NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Perm.POST -> Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Perm.CONTACTS -> context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        Perm.OVERLAY -> Settings.canDrawOverlays(context)
        Perm.USAGE -> UsageWatcher.granted(context)
        Perm.SCREEN -> InstallScreenWatcher.enabled(context)
        Perm.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    fun missing(context: Context): List<Perm> = Perm.entries.filterNot { granted(context, it) }

    /** Runtime permissions are requested with a launcher; the rest open the right settings page. */
    fun runtimePermission(p: Perm): String? = when (p) {
        Perm.POST -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        Perm.CONTACTS -> Manifest.permission.READ_CONTACTS
        else -> null
    }

    @SuppressLint("BatteryLife")
    fun settingsIntent(context: Context, p: Perm): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (p) {
            Perm.LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Perm.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Perm.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            Perm.SCREEN -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            // ONB-09: phone makers add their own battery screens; the standard request covers most.
            Perm.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }
}

package com.tripwire.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.tripwire.app.collect.InstallWatcher
import com.tripwire.app.collect.UsageWatcher
import com.tripwire.app.graph
import com.tripwire.app.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The visible, always-on part of Tripwire (UI-04, PRD 11.5). It hosts the install and usage
 * watchers and keeps the permanent "Tripwire is protecting you" notification current.
 * Tripwire never runs hidden (privacy rule 6).
 */
class ProtectionService : LifecycleService() {
    private lateinit var installs: InstallWatcher
    private lateinit var usage: UsageWatcher

    override fun onCreate() {
        super.onCreate()
        val graph = applicationContext.graph
        installs = InstallWatcher(this, graph).also { it.register() }
        usage = UsageWatcher(this, graph).also { it.start() }
        goForeground(0)
        lifecycleScope.launch {
            while (true) {
                val active = withContext(Dispatchers.IO) { runCatching { graph.store.allCaseStates().count { it.isOpen } }.getOrDefault(0) }
                goForeground(active)
                delay(60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        installs.unregister()
        usage.stop()
        super.onDestroy()
    }

    private fun goForeground(activeCases: Int) {
        val s = applicationContext.graph.settings.current
        val n = applicationContext.graph.notifier.protectionNotification(s.language, activeCases, s.isPaused(System.currentTimeMillis()))
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.PROTECTION_ID, n, type)
    }

    companion object {
        @Volatile var running = false
            private set

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, ProtectionService::class.java))
                running = true
            }
        }
    }
}

/** Restarts protection after a reboot or an app update (PRD 14.5). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.graph.settings.current.onboarded) ProtectionService.start(context)
    }
}

package com.tripwire.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tripwire.core.guard.AppConcern
import com.tripwire.core.guard.AppFinding
import com.tripwire.core.guard.CheckupResult

/** Phone checkup: apps that could steal codes or let someone else control the phone, worst first. */
@Composable
fun CheckupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val lang = vm.settings.collectAsState().value.language
    var result by remember { mutableStateOf<CheckupResult?>(null) }
    var run by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Runs again on return, so an app removed from the system dialog disappears from the list.
    LaunchedEffect(run) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { result = vm.runCheckup() }
    }

    Page(Ui.t("checkup.title", lang), onBack, Ui.t("btn.back", lang)) {
        val r = result
        if (r == null) {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(Ui.t("checkup.running", lang), style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Page
        }
        val findings = r.findings
        val severe = findings.count { it.severe }
        val tone = when {
            severe > 0 -> Status.colors.danger
            findings.isNotEmpty() -> Status.colors.caution
            else -> Status.colors.safe
        }
        ToneCard(tone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    when {
                        severe > 0 -> Icons.Filled.GppBad
                        findings.isNotEmpty() -> Icons.Filled.GppMaybe
                        else -> Icons.Filled.VerifiedUser
                    },
                    tone.container, tone.main, 56.dp,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    when {
                        severe == 1 -> Ui.t("checkup.severe_one", lang)
                        severe > 1 -> Ui.t("checkup.severe", lang, "n" to severe.toString())
                        findings.size == 1 -> Ui.t("checkup.look_one", lang)
                        findings.isNotEmpty() -> Ui.t("checkup.look", lang, "n" to findings.size.toString())
                        else -> Ui.t("checkup.clean", lang)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(Ui.t("checkup.checked", lang, "n" to r.checked.toString()), style = MaterialTheme.typography.bodyMedium)
            if (r.outsideStoreOnly > 0) {
                Text(Ui.t("checkup.outside_only", lang, "n" to r.outsideStoreOnly.toString()), style = MaterialTheme.typography.bodyMedium)
            }
        }

        findings.forEach { f -> FindingCard(f, lang) }

        QuietButton(Ui.t("btn.check_again", lang), icon = Icons.Filled.Refresh) {
            result = null
            run++
        }
    }
}

@Composable
private fun FindingCard(f: AppFinding, lang: String) {
    val context = LocalContext.current
    val tone = if (f.severe) Status.colors.danger else Status.colors.caution
    val icon = remember(f.app.packageName) {
        runCatching { context.packageManager.getApplicationIcon(f.app.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                IconBubble(Icons.Filled.GppMaybe, tone.main, tone.container, 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Text(f.app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                Ui.t(if (f.severe) "checkup.badge_severe" else "checkup.badge_look", lang),
                style = MaterialTheme.typography.labelMedium,
                color = tone.onContainer,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(tone.container).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            f.concerns.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(concernIcon(c), contentDescription = null, tint = if (c == AppConcern.NOT_FROM_STORE) MaterialTheme.colorScheme.onSurfaceVariant else tone.main, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(Ui.t("concern.${c.name.lowercase()}", lang), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (f.app.controlsScreen || f.app.readsNotifications) {
            QuietButton(Ui.t("btn.turn_off_access", lang), color = tone.main) {
                val action = if (f.app.controlsScreen) Settings.ACTION_ACCESSIBILITY_SETTINGS else Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                runCatching { context.startActivity(Intent(action)) }
            }
        }
        QuietButton(Ui.t("btn.remove_app", lang), color = MaterialTheme.colorScheme.error, icon = Icons.Filled.DeleteOutline) {
            @Suppress("DEPRECATION")
            runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${f.app.packageName}"))) }
        }
    }
}

private fun concernIcon(c: AppConcern): ImageVector = when (c) {
    AppConcern.REMOTE_ACCESS -> Icons.Filled.ScreenShare
    AppConcern.NOT_FROM_STORE -> Icons.Filled.Storefront
    AppConcern.READS_SMS -> Icons.Filled.Sms
    AppConcern.CONTROLS_SCREEN -> Icons.Filled.TouchApp
    AppConcern.READS_NOTIFICATIONS -> Icons.Filled.Notifications
    AppConcern.LOOKALIKE -> Icons.Filled.GppBad
}

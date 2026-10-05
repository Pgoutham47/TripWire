package com.tripwire.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tripwire.app.ai.ModelState
import com.tripwire.app.intervene.appLabel
import com.tripwire.core.model.EventType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-01, ONB-07: status at a glance, any protection that is off, active cases, "I already paid". */
@Composable
fun HomeScreen(vm: AppViewModel, onCases: () -> Unit, onCase: (String) -> Unit, onPaid: () -> Unit, onSettings: () -> Unit, onLearn: () -> Unit, onCheckup: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val cases by vm.cases.collectAsState()
    val lang = settings.language
    var missing by remember { mutableStateOf(emptyList<Perm>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { missing = Permissions.missing(context) }
    }
    val now = System.currentTimeMillis()
    val paused = settings.isPaused(now)
    // After "proceed anyway", "I already paid" is pinned at the top in red (INT-08).
    val paidPinned = settings.paidShortcutUntil > now

    Page(Ui.t("app.name", lang)) {
        StatusCard(lang, paused, Perm.LISTENER in missing) { vm.updateSettings { it.copy(pausedUntil = 0) } }

        if (paidPinned) PaidCard(lang, pinned = true, onPaid)

        // ONB-07: each protection that is off, with a way to turn it on.
        missing.forEach { p ->
            ToneCard(Status.colors.caution) {
                Text(Ui.t(p.titleKey, lang), style = MaterialTheme.typography.titleMedium)
                Text(Ui.t("perm.without", lang, "what" to Ui.t(p.withoutKey, lang)), style = MaterialTheme.typography.bodyMedium)
                BigButton(Ui.t("btn.turn_on", lang), color = Status.colors.caution.main) {
                    Permissions.settingsIntent(context, p)?.let { runCatching { context.startActivity(it) } }
                }
            }
        }

        SectionHeader(Ui.t("home.cases", lang))
        if (cases.isEmpty()) {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Filled.CheckCircle, Status.colors.safe.main, Status.colors.safe.container, 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Ui.t("home.no_cases", lang), style = MaterialTheme.typography.titleMedium)
                        Text(Ui.t("home.no_cases_body", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            cases.take(3).forEach { c -> CaseCard(c, lang) { onCase(c.caseId) } }
            if (cases.size > 3) QuietButton(Ui.t("home.see_all", lang, "n" to cases.size.toString()), onClick = onCases)
        }

        if (!paidPinned) PaidCard(lang, pinned = false, onPaid)

        SectionHeader(Ui.t("home.more", lang))
        NavRow(Icons.Filled.GppMaybe, Ui.t("checkup.title", lang), Ui.t("checkup.detail", lang), onCheckup)
        NavRow(Icons.AutoMirrored.Filled.MenuBook, Ui.t("btn.learn", lang), Ui.t("learn.detail", lang), onLearn)
        NavRow(Icons.Filled.Settings, Ui.t("btn.settings", lang), Ui.t("settings.detail", lang), onSettings)
    }
}

@Composable
private fun StatusCard(lang: String, paused: Boolean, listenerOff: Boolean, onResume: () -> Unit) {
    val tone = if (paused || listenerOff) Status.colors.danger else Status.colors.safe
    val icon = when {
        listenerOff -> Icons.Filled.GppBad
        paused -> Icons.Filled.PauseCircle
        else -> Icons.Filled.Shield
    }
    val status = when {
        listenerOff -> Ui.t("home.missing", lang)
        paused -> Ui.t("status.paused", lang)
        else -> Ui.t("status.protecting", lang)
    }
    ToneCard(tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, tone.container, tone.main, 56.dp)
            Spacer(Modifier.width(16.dp))
            Text(status, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Text(Ui.t("home.offline", lang), style = MaterialTheme.typography.bodyMedium)
        if (paused) BigButton(Ui.t("settings.resume", lang), color = tone.main, onClick = onResume)
    }
}

@Composable
private fun PaidCard(lang: String, pinned: Boolean, onPaid: () -> Unit) {
    val danger = Status.colors.danger
    InfoCard(
        container = if (pinned) danger.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        content_ = if (pinned) danger.onContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.SupportAgent, danger.main, danger.container, 44.dp)
            Spacer(Modifier.width(16.dp))
            Text(Ui.t("home.paid_title", lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Text(Ui.t("home.paid_body", lang), style = MaterialTheme.typography.bodyMedium)
        if (pinned) BigButton(Ui.t("btn.already_paid", lang), color = MaterialTheme.colorScheme.error, onClick = onPaid)
        else QuietButton(Ui.t("btn.already_paid", lang), color = danger.main, onClick = onPaid)
    }
}

/** One suspicious chat: who, which scam, which apps, how far along, and the risk. */
@Composable
fun CaseCard(c: CaseItem, lang: String, onClick: () -> Unit) {
    val tone = riskTone(c.risk)
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Filled.Person, tone.main, tone.container, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${c.family.replaceFirstChar { it.uppercase() }} · ${c.apps.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RiskLabel(c.risk, lang)
        StageBar(c.stage, c.stageName, lang)
    }
}

fun modelText(m: ModelState, lang: String): String = when (m) {
    is ModelState.Ready -> Ui.t("model.ready", lang) + " (${m.backend})"
    is ModelState.Idle -> Ui.t("model.ready", lang)
    is ModelState.Loading -> Ui.t("model.loading", lang)
    is ModelState.Failed -> Ui.t("model.failed", lang)
    is ModelState.Downloading -> Ui.t("model.downloading", lang, "p" to m.percent.toString())
    ModelState.None, ModelState.RulesOnlyDevice -> Ui.t("model.rules", lang)
}

@Composable
fun CasesScreen(vm: AppViewModel, onCase: (String) -> Unit, onBack: () -> Unit) {
    val cases by vm.cases.collectAsState()
    val lang = vm.settings.collectAsState().value.language
    Page(Ui.t("cases.title", lang), onBack, Ui.t("btn.back", lang)) {
        if (cases.isEmpty()) Text(Ui.t("cases.empty", lang), style = MaterialTheme.typography.bodyLarge)
        cases.forEach { c -> CaseCard(c, lang) { onCase(c.caseId) } }
    }
}

/** UI-02: events in order with their app, tactics in plain words, the stage bar, trust and delete. */
@Composable
fun TimelineScreen(vm: AppViewModel, caseId: String, onBack: () -> Unit) {
    val lang = vm.settings.collectAsState().value.language
    val ui by remember(caseId) { vm.timeline(caseId) }.collectAsState(initial = null)
    var confirm by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    Page(Ui.t("timeline.title", lang), onBack, Ui.t("btn.back", lang)) {
        val t = ui ?: return@Page
        val tone = riskTone(t.item.risk)
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Filled.Person, tone.main, tone.container, 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.item.title, style = MaterialTheme.typography.titleLarge)
                    Text(t.item.apps.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            RiskLabel(t.item.risk, lang)
            Text(Ui.t("timeline.matches", lang, "family" to t.item.family), style = MaterialTheme.typography.titleMedium)
            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StageBar(t.item.stage, t.item.stageName, lang)
        }

        SectionHeader(Ui.t("timeline.what_happened", lang))
        Column {
            t.rows.forEachIndexed { i, r -> TimelineEntry(r, fmt.format(Date(r.time)), sender = t.item.title, last = i == t.rows.lastIndex) }
        }

        QuietButton(Ui.t("btn.mark_trusted", lang), icon = Icons.Filled.VerifiedUser) { confirm = "trusted" }
        QuietButton(Ui.t("btn.delete", lang), color = MaterialTheme.colorScheme.error, icon = Icons.Filled.DeleteOutline) { confirm = "delete" }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(Ui.t(if (what == "trusted") "confirm.trusted" else "confirm.delete", lang), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "trusted") vm.markTrusted(caseId) else vm.deleteCase(caseId)
                    confirm = null
                    onBack()
                }) { Text(Ui.t("btn.yes", lang)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(Ui.t("btn.cancel", lang)) } },
        )
    }
}

/** One event on a vertical line: an icon for its kind, the time and app, then the message and tactics. */
@Composable
private fun TimelineEntry(r: TimelineRow, time: String, sender: String, last: Boolean) {
    val flagged = r.tactics.isNotEmpty()
    val tint = if (flagged) Status.colors.danger.main else MaterialTheme.colorScheme.primary
    val bg = if (flagged) Status.colors.danger.container else MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().drawBehind {
            if (!last) {
                val x = 20.dp.toPx()
                drawLine(line, Offset(x, 40.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        },
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.TopCenter) {
            IconBubble(eventIcon(r.type), tint, bg, 36.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$time · ${appLabel(r.app)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            // A message's title is its sender, which the case card already names.
            if (r.type != EventType.MESSAGE || r.title != sender) Text(r.title, style = MaterialTheme.typography.titleSmall)
            r.text?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                )
            }
            if (flagged) TacticChips(r.tactics)
        }
    }
}

private fun eventIcon(type: EventType): ImageVector = when (type) {
    EventType.MESSAGE, EventType.USER_REPLY -> Icons.AutoMirrored.Filled.Chat
    EventType.GROUP_ADDED -> Icons.Filled.GroupAdd
    EventType.CALL_STARTED, EventType.CALL_ENDED -> Icons.Filled.Call
    EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED -> Icons.Filled.InstallMobile
    EventType.SCREEN_SHARE_STARTED, EventType.REMOTE_APP_OPENED -> Icons.Filled.ScreenShare
    EventType.UPI_LINK_OPENED, EventType.PAYMENT_APP_OPENED, EventType.PAYMENT_SMS, EventType.COLLECT_REQUEST -> Icons.Filled.Payments
    else -> Icons.Filled.Info
}

@Composable
fun LearnScreen(vm: AppViewModel, families: List<com.tripwire.core.script.FamilyDef>, onBack: () -> Unit) {
    val lang = vm.settings.collectAsState().value.language
    Page(Ui.t("learn.title", lang), onBack, Ui.t("btn.back", lang)) {
        families.forEachIndexed { i, f ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Filled.Shield, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        (f.names[lang] ?: f.names["en"].orEmpty()).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${i + 1}/${families.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(f.descriptions[lang] ?: f.descriptions["en"].orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

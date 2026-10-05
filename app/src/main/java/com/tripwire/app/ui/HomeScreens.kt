package com.tripwire.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tripwire.app.ai.ModelState
import com.tripwire.app.intervene.appLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-01, ONB-07: status at a glance, any protection that is off, active cases, "I already paid". */
@Composable
fun HomeScreen(vm: AppViewModel, onCases: () -> Unit, onCase: (String) -> Unit, onPaid: () -> Unit, onSettings: () -> Unit, onLearn: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val cases by vm.cases.collectAsState()
    val model by vm.modelState.collectAsState()
    val lang = settings.language
    var missing by remember { mutableStateOf(emptyList<Perm>()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { missing = Permissions.missing(context) }
    }
    val now = System.currentTimeMillis()
    val paused = settings.isPaused(now)

    Page(Ui.t("app.name", lang)) {
        InfoCard(container = if (paused || Perm.LISTENER in missing) MaterialTheme.colorScheme.errorContainer else null) {
            val status = when {
                Perm.LISTENER in missing -> Ui.t("home.missing", lang)
                paused -> Ui.t("status.paused", lang)
                else -> Ui.t("status.protecting", lang)
            }
            Text(status, style = MaterialTheme.typography.titleLarge)
            Text(Ui.t("home.offline", lang), style = MaterialTheme.typography.bodyMedium)
            if (paused) TextButton(onClick = { vm.updateSettings { it.copy(pausedUntil = 0) } }) { Text(Ui.t("settings.resume", lang)) }
        }

        // ONB-07: each protection that is off, with a way to turn it on.
        missing.forEach { p ->
            InfoCard {
                Text(Ui.t(p.titleKey, lang), style = MaterialTheme.typography.titleMedium)
                Text(Ui.t("perm.without", lang, "what" to Ui.t(p.withoutKey, lang)), style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { Permissions.settingsIntent(context, p)?.let { runCatching { context.startActivity(it) } } },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(Ui.t("btn.turn_on", lang)) }
            }
        }

        // "I already paid" is always reachable, and pinned prominently after "proceed anyway" (INT-08).
        BigButton(
            Ui.t("btn.already_paid", lang),
            color = if (settings.paidShortcutUntil > now) MaterialTheme.colorScheme.error else null,
            onClick = onPaid,
        )

        InfoCard(modifier = Modifier.clickable(onClick = onCases)) {
            Text(if (cases.isEmpty()) Ui.t("home.no_cases", lang) else Ui.t("home.cases", lang, "n" to cases.size.toString()), style = MaterialTheme.typography.titleMedium)
            cases.take(3).forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { onCase(c.caseId) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("${c.family} · ${c.apps.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                    }
                    RiskLabel(c.risk, lang)
                }
            }
        }

        Text(Ui.t("home.model", lang, "state" to modelText(model, lang)), style = MaterialTheme.typography.bodyMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(Ui.t("btn.learn", lang), Modifier.weight(1f), onLearn)
            QuietButton(Ui.t("btn.settings", lang), Modifier.weight(1f), onSettings)
        }
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
        cases.forEach { c ->
            InfoCard(modifier = Modifier.clickable { onCase(c.caseId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RiskLabel(c.risk, lang)
                }
                Text(Ui.t("timeline.matches", lang, "family" to c.family), style = MaterialTheme.typography.bodyMedium)
                Text(c.apps.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                StageBar(c.stage, c.stageName, lang)
            }
        }
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
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.item.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                RiskLabel(t.item.risk, lang)
            }
            Text(Ui.t("timeline.matches", lang, "family" to t.item.family), style = MaterialTheme.typography.bodyLarge)
            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyMedium)
            StageBar(t.item.stage, t.item.stageName, lang)
        }
        t.rows.forEach { r ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${fmt.format(Date(r.time))} · ${appLabel(r.app)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                r.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (r.tactics.isNotEmpty()) Text("⚑ " + r.tactics.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.size(8.dp))
        QuietButton(Ui.t("btn.mark_trusted", lang)) { confirm = "trusted" }
        QuietButton(Ui.t("btn.delete", lang)) { confirm = "delete" }
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

@Composable
fun LearnScreen(vm: AppViewModel, families: List<com.tripwire.core.script.FamilyDef>, onBack: () -> Unit) {
    val lang = vm.settings.collectAsState().value.language
    Page(Ui.t("learn.title", lang), onBack, Ui.t("btn.back", lang)) {
        families.forEach { f ->
            InfoCard {
                Text((f.names[lang] ?: f.names["en"].orEmpty()).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                Text(f.descriptions[lang] ?: f.descriptions["en"].orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

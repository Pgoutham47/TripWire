package com.tripwire.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tripwire.app.graph
import com.tripwire.app.intervene.WarningActivity
import com.tripwire.core.model.EventType
import java.util.Calendar

/** PRD 13.1 Settings: collectors, language, speech, retention, pause, delete all, ally, sharing. */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val allies by vm.allies.collectAsState()
    val model by vm.modelState.collectAsState()
    val lang = s.language
    var confirmDelete by remember { mutableStateOf(false) }
    var scenarioMenu by remember { mutableStateOf(false) }

    Page(Ui.t("settings.title", lang), onBack, Ui.t("btn.back", lang)) {
        Section(Ui.t("settings.language", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Ui.languages.forEach { (code, name) ->
                    if (code == lang) BigButton(name, Modifier.weight(1f)) {} else QuietButton(name, Modifier.weight(1f)) { vm.updateSettings { it.copy(language = code) } }
                }
            }
        }

        Toggle(Ui.t("settings.speech", lang), s.speechOn) { on -> vm.updateSettings { it.copy(speechOn = on) } }

        Section(Ui.t("settings.retention", lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d ->
                    val label = Ui.t("settings.days", lang, "n" to d.toString())
                    if (d == s.retentionDays) BigButton(label, Modifier.weight(1f)) {} else QuietButton(label, Modifier.weight(1f)) { vm.updateSettings { it.copy(retentionDays = d) } }
                }
            }
        }

        // SET-04: pause for an hour or until tomorrow, with resume.
        Section(Ui.t("settings.pause", lang)) {
            Text(Ui.t("settings.disable_warning", lang), style = MaterialTheme.typography.bodyMedium)
            if (s.isPaused(System.currentTimeMillis())) {
                BigButton(Ui.t("settings.resume", lang)) { vm.updateSettings { it.copy(pausedUntil = 0) } }
            } else {
                QuietButton(Ui.t("settings.pause_hour", lang)) { vm.updateSettings { it.copy(pausedUntil = System.currentTimeMillis() + 3_600_000) } }
                QuietButton(Ui.t("settings.pause_tomorrow", lang)) { vm.updateSettings { it.copy(pausedUntil = tomorrowMorning()) } }
            }
        }

        // SET-01: each collector on or off.
        Section(Ui.t("settings.collectors", lang)) {
            listOf(EventType.MESSAGE, EventType.APP_INSTALLED, EventType.PAYMENT_APP_OPENED, EventType.CALL_STARTED, EventType.REMOTE_APP_OPENED).forEach { type ->
                val on = type.wire !in s.disabledCollectors
                Toggle(Ui.t("collector.${type.wire}", lang), on) { enable ->
                    vm.updateSettings {
                        val related = relatedTypes(type).map { t -> t.wire }.toSet()
                        it.copy(disabledCollectors = if (enable) it.disabledCollectors - related else it.disabledCollectors + related)
                    }
                }
            }
        }

        Section(Ui.t("settings.allies", lang)) {
            val ally = allies.firstOrNull()
            if (ally == null) Text(Ui.t("settings.no_ally", lang), style = MaterialTheme.typography.bodyLarge)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ally.name} · ${ally.phone}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeAlly(ally.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(Ui.t("btn.remove", lang)) }
            }
        }

        Section(Ui.t("settings.model", lang)) {
            Text(modelText(model, lang), style = MaterialTheme.typography.bodyLarge)
            Text(Ui.t("settings.model_hint", lang, "path" to context.graph.models.pushDir.absolutePath), style = MaterialTheme.typography.bodyMedium)
            QuietButton(Ui.t("btn.reload_model", lang)) { vm.refreshModel() }
            if (context.graph.models.downloadConfigured) QuietButton(Ui.t("btn.download_model", lang)) { vm.downloadModel() }
        }

        Toggle(Ui.t("settings.share", lang), s.shareAnonymousPatterns, Ui.t("settings.share_body", lang)) { on -> vm.updateSettings { it.copy(shareAnonymousPatterns = on) } }

        // PRD 22 fallback: replay a recorded scam on this phone, in a private in-memory ledger.
        Section(Ui.t("settings.demo", lang)) {
            Text(Ui.t("settings.demo_body", lang), style = MaterialTheme.typography.bodyMedium)
            if (!scenarioMenu) {
                QuietButton(Ui.t("btn.test_warning", lang)) { scenarioMenu = true }
            } else {
                vm.scenarios.filter { it.family != null }.forEach { sc ->
                    QuietButton(sc.id) { vm.playScenario(sc.id) { req -> context.startActivity(WarningActivity.intent(context, req)) } }
                }
            }
        }

        BigButton(Ui.t("settings.delete_all", lang), color = MaterialTheme.colorScheme.error) { confirmDelete = true }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(Ui.t("confirm.delete_all", lang), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll(onDeleted)
                }) { Text(Ui.t("btn.delete", lang)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(Ui.t("btn.cancel", lang)) } },
        )
    }
}

private fun relatedTypes(t: EventType): List<EventType> = when (t) {
    EventType.MESSAGE -> listOf(EventType.MESSAGE, EventType.GROUP_ADDED, EventType.USER_REPLY)
    EventType.APP_INSTALLED -> listOf(EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED)
    EventType.PAYMENT_APP_OPENED -> listOf(EventType.PAYMENT_APP_OPENED, EventType.UPI_LINK_OPENED)
    EventType.CALL_STARTED -> listOf(EventType.CALL_STARTED, EventType.CALL_ENDED)
    EventType.REMOTE_APP_OPENED -> listOf(EventType.REMOTE_APP_OPENED, EventType.SCREEN_SHARE_STARTED)
    else -> listOf(t)
}

private fun tomorrowMorning(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
}.timeInMillis

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    InfoCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

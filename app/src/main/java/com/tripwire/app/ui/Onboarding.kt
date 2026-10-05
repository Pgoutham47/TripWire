package com.tripwire.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tripwire.app.intervene.WarningActivity
import com.tripwire.app.service.ProtectionService

private enum class Step { Welcome, Language, Disclosure, Declined, Permissions, Ally, Done }

/** Journey 1: under 90 seconds, excluding the model download. */
@Composable
fun Onboarding(vm: AppViewModel, onFinished: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val lang = settings.language
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    val context = LocalContext.current

    when (step) {
        Step.Welcome -> Page(Ui.t("app.name", lang)) {
            listOf("welcome.l1", "welcome.l2", "welcome.l3").forEach { Text(Ui.t(it, lang), style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.size(12.dp))
            BigButton(Ui.t("btn.start", lang)) { step = Step.Language }
        }

        Step.Language -> Page(Ui.t("lang.title", lang)) {
            Ui.languages.forEach { (code, name) ->
                if (code == lang) BigButton(name) { step = Step.Disclosure }
                else QuietButton(name) { vm.updateSettings { it.copy(language = code) } }
            }
            Spacer(Modifier.size(8.dp))
            BigButton(Ui.t("btn.next", lang)) { step = Step.Disclosure }
        }

        // ONB-01: every kind of data, why, and that it stays on the phone. Consent before any prompt.
        Step.Disclosure -> Page(Ui.t("disclosure.title", lang)) {
            listOf("disclosure.notifications", "disclosure.contacts", "disclosure.apps", "disclosure.payments", "disclosure.storage").forEach {
                InfoCard { Text(Ui.t(it, lang), style = MaterialTheme.typography.bodyLarge) }
            }
            Text(Ui.t("disclosure.consent_self", lang), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            BigButton(Ui.t("btn.agree", lang)) {
                vm.updateSettings { it.copy(consentGiven = true) }
                step = Step.Permissions
            }
            QuietButton(Ui.t("btn.no_thanks", lang)) { step = Step.Declined }
        }

        Step.Declined -> Page(null) {
            Text(Ui.t("disclosure.declined", lang), style = MaterialTheme.typography.titleLarge)
            BigButton(Ui.t("btn.back", lang)) { step = Step.Disclosure }
        }

        Step.Permissions -> PermissionSteps(lang, onDone = { step = Step.Ally })

        Step.Ally -> AllyStep(vm, lang, onDone = {
            ProtectionService.start(context)
            step = Step.Done
        })

        Step.Done -> Page(Ui.t("done.title", lang)) {
            Text(Ui.t("done.body", lang), style = MaterialTheme.typography.bodyLarge)
            // ONB-06: a sample of the real warning, produced by replaying a recorded scam.
            QuietButton(Ui.t("btn.test_warning", lang)) {
                vm.playScenario { req -> context.startActivity(WarningActivity.intent(context, req)) }
            }
            BigButton(Ui.t("btn.finish", lang)) {
                vm.updateSettings { it.copy(onboarded = true) }
                onFinished()
            }
        }
    }
}

/** ONB-02, ONB-03: one permission per step, its reason, and what stops working without it. */
@Composable
private fun PermissionSteps(lang: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val perms = Perm.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(false) }
    val p = perms[index]

    // Re-check whenever the user comes back from a settings page (ONB-02).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(index) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { granted = Permissions.granted(context, p) }
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = Permissions.granted(context, p) }

    fun next() {
        if (index < perms.lastIndex) {
            index++
            granted = Permissions.granted(context, perms[index])
        } else {
            onDone()
        }
    }

    Page(Ui.t(p.titleKey, lang)) {
        Text(Ui.t("perm.step", lang, "i" to (index + 1).toString(), "n" to perms.size.toString()), style = MaterialTheme.typography.bodyMedium)
        Text(Ui.t(p.reasonKey, lang), style = MaterialTheme.typography.titleLarge)
        Text(Ui.t("perm.without", lang, "what" to Ui.t(p.withoutKey, lang)), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(8.dp))
        if (granted) {
            Text("✓ " + Ui.t("perm.granted", lang), color = Status.colors.safe.main, style = MaterialTheme.typography.titleMedium)
            BigButton(Ui.t("btn.next", lang)) { next() }
        } else {
            BigButton(Ui.t("btn.allow", lang)) {
                val rp = Permissions.runtimePermission(p)
                if (rp != null) runtime.launch(rp)
                else Permissions.settingsIntent(context, p)?.let { runCatching { context.startActivity(it) } }
            }
            // Notification access is the one required permission (ONB-03).
            if (!p.required) QuietButton(Ui.t("btn.skip", lang)) { next() }
        }
    }
}

@Composable
private fun AllyStep(vm: AppViewModel, lang: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val allies by vm.allies.collectAsState()
    val settings by vm.settings.collectAsState()
    var owner by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) vm.addAlly(c.getString(0), c.getString(1))
        }
    }
    Page(Ui.t("ally.title", lang)) {
        Text(Ui.t("ally.body", lang), style = MaterialTheme.typography.bodyLarge)
        allies.firstOrNull()?.let { Text(Ui.t("ally.chosen", lang, "name" to it.name), style = MaterialTheme.typography.titleMedium) }
        QuietButton(Ui.t("btn.pick_contact", lang)) {
            picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        OutlinedTextField(
            value = owner, onValueChange = { owner = it },
            label = { Text(Ui.t("owner.name", lang)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Ui.t("btn.next", lang)) {
                vm.updateSettings { it.copy(ownerName = owner.trim()) }
                onDone()
            }
            if (allies.isEmpty()) QuietButton(Ui.t("btn.skip", lang)) { onDone() }
        }
    }
}

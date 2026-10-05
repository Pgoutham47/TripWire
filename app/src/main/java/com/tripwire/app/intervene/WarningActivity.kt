package com.tripwire.app.intervene

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tripwire.app.MainActivity
import com.tripwire.app.collect.UpiLinkActivity
import com.tripwire.app.graph
import com.tripwire.app.ui.Status
import com.tripwire.app.ui.TripwireTheme
import com.tripwire.app.ui.Ui
import com.tripwire.app.work.Workers
import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.evidence.AllyAlert
import com.tripwire.core.explain.WarningContent
import com.tripwire.core.ledger.Feedback
import com.tripwire.core.ledger.UserChoice
import com.tripwire.core.model.EventType
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.script.ScriptPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the warning screen needs, passed as JSON in the intent. */
@Serializable
data class WarningRequest(
    val interventionId: Long,
    val caseId: String,
    val warning: WarningContent,
    /** For UPI links: the original link, passed on unchanged if the user chooses to pay anyway. */
    val forwardUri: String? = null,
    /** True for the onboarding sample (ONB-06); nothing is recorded. */
    val sample: Boolean = false,
)

/**
 * The full-screen warning (PRD 13.2). It never blocks: "proceed anyway" is always there, behind a
 * three-second hold (INT-05). Every choice is logged, then the user is asked "Was this a scam?" (FBK-01).
 */
class WarningActivity : ComponentActivity() {
    private lateinit var req: WarningRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        req = ScriptPack.json.decodeFromString(WarningRequest.serializer(), intent.getStringExtra(EXTRA)!!)
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true) // no other app may cover the warning (PRD 15.4)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val graph = applicationContext.graph
        graph.notifier.cancelWarning()
        val lang = req.warning.language
        if (graph.settings.current.speechOn) graph.speaker.speak(req.warning.spoken, lang)

        setContent {
            TripwireTheme {
                var stage by remember { mutableStateOf(Phase.Warning) }
                BackHandler { choose(UserChoice.STOPPED) { stage = it } }
                when (stage) {
                    Phase.Warning -> WarningScreen(
                        w = req.warning,
                        allyPhone = allyPhone,
                        onPrimary = { choose(UserChoice.STOPPED) { stage = it } },
                        onAlly = { choose(UserChoice.CALLED_ALLY) { stage = it } },
                        onVerify = { choose(UserChoice.VERIFIED) { stage = it } },
                        onProceed = { choose(UserChoice.PROCEEDED) { stage = it } },
                        onTrusted = { choose(UserChoice.MARKED_TRUSTED) { stage = it } },
                        onPaid = {
                            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAID_CASE, req.caseId))
                            finish()
                        },
                        onReplay = { graph.speaker.speak(req.warning.spoken, lang) },
                    )
                    Phase.Feedback -> FeedbackScreen(lang) { fb -> feedback(fb) }
                    Phase.Done -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            allyPhone = graph.store.dao.allies().firstOrNull()?.phone
        }
    }

    /** Compose state, so the ally button appears as soon as the number has loaded. */
    private var allyPhone by mutableStateOf<String?>(null)

    override fun onDestroy() {
        applicationContext.graph.speaker.stop()
        super.onDestroy()
    }

    private enum class Phase { Warning, Feedback, Done }

    private fun choose(choice: UserChoice, next: (Phase) -> Unit) {
        val graph = applicationContext.graph
        graph.speaker.stop()
        if (req.sample) {
            next(Phase.Done)
            return
        }
        lifecycleScope.launch {
            val effect = withContext(Dispatchers.IO) { graph.pipeline.recordChoice(req.interventionId, choice) }
            when (choice) {
                UserChoice.STOPPED -> stopAction()
                UserChoice.CALLED_ALLY -> allyPhone?.let { startActivity(graph.allies.dialIntent(it)) }
                UserChoice.VERIFIED -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(graph.pack.sebiCheckUrl)))
                UserChoice.PROCEEDED -> {
                    if (effect.alertAlly) graph.guardian.alertAlly(req.caseId, AllyAlert.Kind.PROCEEDED)
                    effect.pinPaidShortcutUntil?.let { until -> graph.settings.update { it.copy(paidShortcutUntil = until) } }
                    effect.checkInAt?.let { Workers.scheduleCheckIn(this@WarningActivity, req.caseId, it) }
                    req.forwardUri?.let { UpiLinkActivity.forward(this@WarningActivity, Uri.parse(it)) }
                }
                else -> Unit
            }
            next(if (choice == UserChoice.MARKED_TRUSTED) Phase.Done else Phase.Feedback)
        }
    }

    /** What "Don't pay / Don't install / Stop" does for each moment. */
    private fun stopAction() {
        when (req.warning.moment) {
            TripwireMoment.INSTALL -> {
                // The install was seen after it finished (SIG-06), so "Don't install" offers removal (INT-09).
                lifecycleScope.launch(Dispatchers.IO) {
                    val graph = applicationContext.graph
                    val members = graph.pipeline.membersOf(req.caseId)
                    val installed = graph.store.eventsFor(members).lastOrNull { it.type == EventType.APP_INSTALLED }?.installedPackage
                    if (installed != null) {
                        withContext(Dispatchers.Main) {
                            @Suppress("DEPRECATION")
                            runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$installed"))) }
                        }
                    }
                }
            }
            TripwireMoment.PAYMENT, TripwireMoment.SCREEN_SHARE -> goHome()
            TripwireMoment.CALL -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930")))
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun feedback(fb: Feedback?) {
        val graph = applicationContext.graph
        if (fb != null) {
            graph.scope.launch(Dispatchers.IO) {
                val next = graph.pipeline.recordFeedback(req.interventionId, fb, graph.settings.current.offsets)
                graph.settings.update { it.copy(watchOffset = next.watch, warnOffset = next.warn) }
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA = "request"

        fun intent(context: Context, req: WarningRequest): Intent =
            Intent(context, WarningActivity::class.java)
                .putExtra(EXTRA, ScriptPack.json.encodeToString(WarningRequest.serializer(), req))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}

@Composable
private fun WarningScreen(
    w: WarningContent,
    allyPhone: String?,
    onPrimary: () -> Unit,
    onAlly: () -> Unit,
    onVerify: () -> Unit,
    onProceed: () -> Unit,
    onTrusted: () -> Unit,
    onPaid: () -> Unit,
    onReplay: () -> Unit,
) {
    val lang = w.language
    val danger = Status.colors.danger
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Brand mark the user saw during setup, so an imitation is easier to spot (PRD 15.4).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(Ui.t("warning.brand", lang), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReplay, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(Ui.t("warning.replay", lang), style = MaterialTheme.typography.labelMedium)
                }
            }

            // 1. Headline
            Surface(color = danger.container, contentColor = danger.onContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = danger.main, modifier = Modifier.size(40.dp))
                    Text(w.headline, style = MaterialTheme.typography.headlineMedium)
                }
            }

            // 2. Up to three reasons
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                w.reasons.forEach { r ->
                    Row {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = danger.main, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(r, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // 3. Timeline strip
            if (w.timeline.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(Ui.t("warning.what_happened", lang), style = MaterialTheme.typography.titleMedium)
                        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                        w.timeline.forEach { item ->
                            Row {
                                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(danger.main))
                                Spacer(Modifier.size(14.dp))
                                Column {
                                    Text(
                                        "${fmt.format(Date(item.time))} · ${appLabel(item.app)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(item.label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Grounded check, in a box
            w.check?.let { c ->
                val (tone, icon) = when (c.outcome) {
                    CheckOutcome.FAIL -> danger to Icons.Filled.Cancel
                    CheckOutcome.PASS -> Status.colors.safe to Icons.Filled.CheckCircle
                    CheckOutcome.UNKNOWN -> Status.colors.caution to Icons.Filled.Help
                }
                Surface(
                    color = tone.container,
                    contentColor = tone.onContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(2.dp, tone.main),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = tone.main, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(c.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text(w.closing, style = MaterialTheme.typography.titleMedium)

            // 5. Primary button
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(w.primaryLabel, style = MaterialTheme.typography.titleLarge) }

            // 6. Call the ally
            val allyLabel = w.allyLabel
            if (allyLabel != null && allyPhone != null) {
                OutlinedButton(onClick = onAlly, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(allyLabel, style = MaterialTheme.typography.labelLarge)
                }
            }

            // 7. Verify on SEBI Check
            w.verifyLabel?.let { label ->
                OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = onTrusted, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(Ui.t("warning.trusted", lang), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
                if (w.moment == TripwireMoment.PAYMENT || w.moment == TripwireMoment.CALL) {
                    TextButton(onClick = onPaid, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(Ui.t("warning.already_paid", lang), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }

            // 8. Proceed, small, press and hold for three seconds
            HoldToProceed(w.proceedLabel, Ui.t("warning.hold", lang), onProceed)
        }
    }
}

/** INT-05: proceeding is always possible, but needs a deliberate three-second hold. */
@Composable
private fun HoldToProceed(label: String, holdingLabel: String, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    val job = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(3000, easing = LinearEasing))
                        onDone()
                    }
                    waitForUpOrCancellation()
                    holding = false
                    if (progress.value < 1f) {
                        job.cancel()
                        scope.launch { progress.animateTo(0f, tween(200)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(progress.value.coerceIn(0f, 1f)).heightIn(min = 56.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
        )
        Text(if (holding) holdingLabel else label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackScreen(lang: String, onAnswer: (Feedback?) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(Ui.t("warning.feedback", lang), style = MaterialTheme.typography.headlineMedium)
            listOf(Feedback.SCAM to "feedback.yes", Feedback.GENUINE to "feedback.no", Feedback.NOT_SURE to "feedback.not_sure").forEach { (fb, key) ->
                OutlinedButton(onClick = { onAnswer(fb) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Text(Ui.t(key, lang), style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = { onAnswer(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(Ui.t("btn.skip", lang)) }
        }
    }
}

private val knownApps = mapOf(
    "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "org.telegram.messenger" to "Telegram",
    "com.google.android.apps.messaging" to "SMS", "com.android.mms" to "SMS", "com.tripwire.app" to "UPI link",
    "com.phonepe.app" to "PhonePe", "net.one97.paytm" to "Paytm", "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.google.android.packageinstaller" to "App installer", "com.android.packageinstaller" to "App installer",
    "com.android.chrome" to "Chrome", "com.android.dialer" to "Phone", "com.google.android.dialer" to "Phone",
)

fun appLabel(pkg: String): String = knownApps[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

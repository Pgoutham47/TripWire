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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tripwire.app.MainActivity
import com.tripwire.app.collect.UpiLinkActivity
import com.tripwire.app.graph
import com.tripwire.app.ui.TripwireTheme
import com.tripwire.app.ui.TwColors
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
    val dark = isSystemInDarkTheme()
    Surface(color = if (dark) TwColors.WarnSurfaceDark else TwColors.WarnSurface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Brand mark the user saw during setup, so an imitation is easier to spot (PRD 15.4).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.size(8.dp))
                Text(Ui.t("warning.brand", lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onReplay, modifier = Modifier.heightIn(min = 48.dp)) { Text("🔊 " + Ui.t("warning.replay", lang)) }
            }

            // 1. Headline
            Text(w.headline, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)

            // 2. Up to three reasons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                w.reasons.forEach { r ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyLarge)
                        Text(r, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // 3. Timeline strip
            if (w.timeline.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(Ui.t("warning.what_happened", lang), style = MaterialTheme.typography.titleMedium)
                    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                    w.timeline.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(fmt.format(Date(item.time)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 10.dp).heightIn(min = 22.dp))
                            Column {
                                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                                Text(appLabel(item.app), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }

            // 4. Grounded check, in a box
            w.check?.let { c ->
                val color = when (c.outcome) {
                    CheckOutcome.FAIL -> MaterialTheme.colorScheme.error
                    CheckOutcome.PASS -> TwColors.Ok
                    CheckOutcome.UNKNOWN -> TwColors.Caution
                }
                Text(
                    c.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().border(2.dp, color, RoundedCornerShape(12.dp)).padding(14.dp),
                )
            }

            Text(w.closing, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

            // 5. Primary button
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(w.primaryLabel, style = MaterialTheme.typography.titleLarge) }

            // 6. Call the ally
            val allyLabel = w.allyLabel
            if (allyLabel != null && allyPhone != null) {
                OutlinedButton(onClick = onAlly, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(allyLabel, style = MaterialTheme.typography.labelLarge)
                }
            }

            // 7. Verify on SEBI Check
            w.verifyLabel?.let { label ->
                TextButton(onClick = onVerify, modifier = Modifier.heightIn(min = 48.dp)) { Text(label, style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onTrusted, modifier = Modifier.heightIn(min = 48.dp)) { Text(Ui.t("warning.trusted", lang)) }
                if (w.moment == TripwireMoment.PAYMENT || w.moment == TripwireMoment.CALL) {
                    TextButton(onClick = onPaid, modifier = Modifier.heightIn(min = 48.dp)) { Text(Ui.t("warning.already_paid", lang)) }
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
            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(28.dp))
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
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        )
        Text(if (holding) holdingLabel else label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun FeedbackScreen(lang: String, onAnswer: (Feedback?) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(Ui.t("warning.feedback", lang), style = MaterialTheme.typography.headlineMedium)
            listOf(Feedback.SCAM to "feedback.yes", Feedback.GENUINE to "feedback.no", Feedback.NOT_SURE to "feedback.not_sure").forEach { (fb, key) ->
                OutlinedButton(onClick = { onAnswer(fb) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
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

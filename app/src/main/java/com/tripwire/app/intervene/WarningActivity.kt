package com.tripwire.app.intervene

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tripwire.app.MainActivity
import com.tripwire.app.collect.InstallScreenWatcher
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** The warning on screen. Compose state, so a newer warning can replace it (see [onNewIntent]). */
    private var req by mutableStateOf<WarningRequest?>(null)
    private var stage by mutableStateOf(Phase.Warning)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) window.setHideOverlayWindows(true) // no other app may cover the warning (PRD 15.4)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val graph = applicationContext.graph
        present(decode(intent) ?: return finish())

        setContent {
            TripwireTheme {
                val r = req ?: return@TripwireTheme
                val lang = r.warning.language
                BackHandler { choose(r, UserChoice.STOPPED) }
                // A new warning restarts the screen from the top, not mid-scroll in the old one.
                key(r.interventionId) {
                    when (stage) {
                        Phase.Warning -> WarningScreen(
                            w = r.warning,
                            allyPhone = allyPhone,
                            onPrimary = { choose(r, UserChoice.STOPPED) },
                            onAlly = { choose(r, UserChoice.CALLED_ALLY) },
                            onVerify = { choose(r, UserChoice.VERIFIED) },
                            onProceed = { choose(r, UserChoice.PROCEEDED) },
                            onTrusted = { choose(r, UserChoice.MARKED_TRUSTED) },
                            onPaid = {
                                startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PAID_CASE, r.caseId))
                                finish()
                            },
                            onReplay = { graph.speaker.speak(r.warning.spoken, lang) },
                        )
                        Phase.Feedback -> FeedbackScreen(lang) { fb -> feedback(r, fb) }
                        Phase.Done -> LaunchedEffect(Unit) { finish() }
                    }
                }
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            allyPhone = graph.store.dao.allies().firstOrNull()?.phone
        }
    }

    /** Compose state, so the ally button appears as soon as the number has loaded. */
    private var allyPhone by mutableStateOf<String?>(null)

    /**
     * A second warning while one is on screen (the activity is singleTop): the newest is what the
     * person is about to do, so it replaces the old one. A warning left unanswered is recorded as
     * dismissed, so the audit trail shows it was seen but not chosen on (ENG-04).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val next = decode(intent) ?: return
        val old = req
        if (old != null && old.interventionId == next.interventionId && !old.sample) return
        setIntent(intent)
        com.tripwire.app.engine.Guardian.debug { "warning ${old?.warning?.moment} replaced by ${next.warning.moment} (old answered=${stage != Phase.Warning})" }
        if (old != null && !old.sample && stage == Phase.Warning) {
            val graph = applicationContext.graph
            graph.scope.launch(Dispatchers.IO) {
                graph.pipeline.recordChoice(old.interventionId, UserChoice.DISMISSED)
                com.tripwire.app.engine.Guardian.debug { "choice DISMISSED on ${old.warning.moment} warning ${old.interventionId}" }
            }
        }
        present(next)
    }

    private fun decode(intent: Intent?): WarningRequest? =
        intent?.getStringExtra(EXTRA)?.let { runCatching { ScriptPack.json.decodeFromString(WarningRequest.serializer(), it) }.getOrNull() }

    /** Shows [r] from the top: clears its notification and reads it aloud, unless a screen reader is on. */
    private fun present(r: WarningRequest) {
        val graph = applicationContext.graph
        req = r
        stage = Phase.Warning
        // Screen readers announce the window by its title: "Tripwire warning", not just "Tripwire".
        title = Ui.t("warning.brand", r.warning.language)
        graph.notifier.cancelWarning()
        graph.speaker.stop()
        // TalkBack already reads the screen; a second voice would talk over it (PRD 13.4).
        // "Read aloud again" still works.
        val screenReader = getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
        if (graph.settings.current.speechOn && !screenReader) graph.speaker.speak(r.warning.spoken, r.warning.language)
    }

    override fun onDestroy() {
        applicationContext.graph.speaker.stop()
        super.onDestroy()
    }

    private enum class Phase { Warning, Feedback, Done }

    /** Records [choice] against [r], the warning it was made on, even if a newer one has since arrived. */
    private fun choose(r: WarningRequest, choice: UserChoice) {
        val graph = applicationContext.graph
        graph.speaker.stop()
        if (r.sample) {
            if (req === r) stage = Phase.Done
            return
        }
        lifecycleScope.launch {
            val effect = withContext(Dispatchers.IO) { graph.pipeline.recordChoice(r.interventionId, choice) }
            com.tripwire.app.engine.Guardian.debug { "choice $choice on ${r.warning.moment} warning ${r.interventionId}" }
            when (choice) {
                UserChoice.STOPPED -> stopAction(r)
                UserChoice.CALLED_ALLY -> allyPhone?.let { startActivity(graph.allies.dialIntent(it)) }
                UserChoice.VERIFIED -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(graph.pack.sebiCheckUrl)))
                UserChoice.PROCEEDED -> {
                    if (effect.alertAlly) graph.guardian.alertAlly(r.caseId, AllyAlert.Kind.PROCEEDED)
                    effect.pinPaidShortcutUntil?.let { until -> graph.settings.update { it.copy(paidShortcutUntil = until) } }
                    effect.checkInAt?.let { Workers.scheduleCheckIn(this@WarningActivity, r.caseId, it) }
                    r.forwardUri?.let { UpiLinkActivity.forward(this@WarningActivity, Uri.parse(it)) }
                }
                else -> Unit
            }
            // Only move on if this warning is still the one on screen.
            if (req === r) stage = if (choice == UserChoice.MARKED_TRUSTED) Phase.Done else Phase.Feedback
        }
    }

    /** What "Don't pay / Don't install / Stop" does for each moment. */
    private fun stopAction(req: WarningRequest) {
        when (req.warning.moment) {
            TripwireMoment.INSTALL -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    val graph = applicationContext.graph
                    val members = graph.pipeline.membersOf(req.caseId)
                    val last = graph.store.eventsFor(members)
                        .lastOrNull { it.type == EventType.APP_INSTALLED || it.type == EventType.INSTALL_SCREEN_OPENED }
                    com.tripwire.app.engine.Guardian.debug { "don't install: last install event ${last?.type}" }
                    // Caught on the install screen (SIG-11): cancel it, so nothing is installed.
                    if (last?.type == EventType.INSTALL_SCREEN_OPENED && InstallScreenWatcher.cancelInstall()) return@launch
                    // Seen after the install finished (SIG-06), so "Don't install" offers removal (INT-09).
                    val installed = last?.takeIf { it.type == EventType.APP_INSTALLED }?.installedPackage
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

    private fun feedback(r: WarningRequest, fb: Feedback?) {
        val graph = applicationContext.graph
        if (fb != null) {
            graph.scope.launch(Dispatchers.IO) {
                val next = graph.pipeline.recordFeedback(r.interventionId, fb, graph.settings.current.offsets)
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
                Text(Ui.t("warning.brand", lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onReplay, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(Ui.t("warning.replay", lang), style = MaterialTheme.typography.labelLarge)
                }
            }

            // 1. Headline
            Surface(color = danger.container, contentColor = danger.onContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.ReportProblem, contentDescription = null, tint = danger.main, modifier = Modifier.size(40.dp))
                    Text(w.headline, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
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
                        Text(Ui.t("warning.what_happened", lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        val fmt = warningDateFormat(lang)
                        w.timeline.forEach { item ->
                            val time = fmt.format(Date(item.time))
                            val app = appLabel(item.app)
                            // One stop per event for a screen reader, the event first, then when and where.
                            Row(Modifier.clearAndSetSemantics { contentDescription = "${item.label}. $time, $app" }) {
                                Box(Modifier.padding(top = 8.dp).size(10.dp).clip(CircleShape).background(danger.main))
                                Spacer(Modifier.size(14.dp))
                                Column {
                                    Text("$time · $app", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
                TextButton(onClick = onTrusted, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(Ui.t("warning.trusted", lang), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
                if (w.moment == TripwireMoment.PAYMENT || w.moment == TripwireMoment.CALL) {
                    TextButton(onClick = onPaid, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(Ui.t("warning.already_paid", lang), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                    }
                }
            }

            // 8. Proceed, small, press and hold for three seconds
            HoldToProceed(w.proceedLabel, lang, onProceed)
        }
    }
}

/**
 * INT-05: proceeding is always possible, but needs a deliberate three-second hold. The hold is
 * timed by the clock, not by the fill animation, which ends at once when the system "Remove
 * animations" setting is on. Screen reader and switch users cannot hold a point on screen, so the
 * control also has a click action that waits the same three seconds, then asks once more (PRD 13.4).
 */
@Composable
private fun HoldToProceed(label: String, lang: String, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(HoldStep.Idle) }
    var stepJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    /** Fills the bar over three seconds; [then] runs once the three seconds are up. */
    fun countdown(then: suspend () -> Unit): Job = scope.launch {
        launch { progress.snapTo(0f); progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing)) }
        delay(HOLD_MS.toLong())
        then()
    }

    fun reset() {
        stepJob?.cancel()
        step = HoldStep.Idle
        scope.launch { progress.animateTo(0f, tween(200)) }
    }

    val shown = when {
        holding -> Ui.t("warning.hold", lang)
        step == HoldStep.Waiting -> Ui.t("warning.hold_wait", lang)
        step == HoldStep.Ready -> Ui.t("warning.hold_ready", lang)
        else -> label
    }
    val action = when (step) {
        HoldStep.Idle -> Ui.t("warning.hold_start", lang)
        HoldStep.Waiting -> Ui.t("warning.hold_cancel", lang)
        HoldStep.Ready -> Ui.t("warning.hold_go", lang)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = shown
                // The label change is read out when the wait ends, without moving focus.
                liveRegion = LiveRegionMode.Polite
                onClick(label = action) {
                    when (step) {
                        HoldStep.Idle -> {
                            step = HoldStep.Waiting
                            stepJob = countdown {
                                step = HoldStep.Ready
                                // Not confirmed in time: back to the start, so a later stray tap does nothing.
                                delay(READY_MS)
                                reset()
                            }
                        }
                        HoldStep.Waiting -> reset()
                        HoldStep.Ready -> {
                            reset()
                            onDone()
                        }
                    }
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    stepJob?.cancel()
                    step = HoldStep.Idle
                    holding = true
                    var passed = false
                    val job = countdown {
                        passed = true
                        onDone()
                    }
                    waitForUpOrCancellation()
                    holding = false
                    if (!passed) {
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
        Text(
            shown,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** The screen reader path through [HoldToProceed]: start the wait, wait, then confirm. */
private enum class HoldStep { Idle, Waiting, Ready }

private const val HOLD_MS = 3000
private const val READY_MS = 10_000L

@Composable
private fun FeedbackScreen(lang: String, onAnswer: (Feedback?) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(Ui.t("warning.feedback", lang), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            listOf(Feedback.SCAM to "feedback.yes", Feedback.GENUINE to "feedback.no", Feedback.NOT_SURE to "feedback.not_sure").forEach { (fb, key) ->
                OutlinedButton(onClick = { onAnswer(fb) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
                    Text(Ui.t(key, lang), style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = { onAnswer(null) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(Ui.t("btn.skip", lang), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/**
 * Timeline dates in the warning's own language, so a Hindi warning on an English phone has no
 * English month names (PRD 13.4). The phone's own format is kept when its language matches.
 * Android's Hindi format writes "am"/"pm" in Latin letters, so Hindi gets its own words.
 */
internal fun warningDateFormat(lang: String, device: Locale = Locale.getDefault()): SimpleDateFormat {
    val base = lang.substringBefore('-')
    val locale = if (device.language == base) device else Locale.forLanguageTag(if ('-' in lang) lang else "$base-IN")
    val fmt = SimpleDateFormat("d MMM, h:mm a", locale)
    if (locale.language == "hi") {
        fmt.dateFormatSymbols = fmt.dateFormatSymbols.apply { amPmStrings = arrayOf("पूर्वाह्न", "अपराह्न") }
    }
    return fmt
}

private val knownApps = mapOf(
    "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "org.telegram.messenger" to "Telegram",
    "com.google.android.apps.messaging" to "SMS", "com.android.mms" to "SMS", "com.tripwire.app" to "UPI link",
    "com.phonepe.app" to "PhonePe", "net.one97.paytm" to "Paytm", "com.google.android.apps.nbu.paisa.user" to "Google Pay",
    "com.google.android.packageinstaller" to "App installer", "com.android.packageinstaller" to "App installer",
    "com.android.chrome" to "Chrome", "com.android.dialer" to "Phone", "com.google.android.dialer" to "Phone",
)

fun appLabel(pkg: String): String = knownApps[pkg] ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

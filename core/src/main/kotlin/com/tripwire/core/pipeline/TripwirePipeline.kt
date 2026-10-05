package com.tripwire.core.pipeline

import com.tripwire.core.checks.CheckIds
import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.checks.CheckResult
import com.tripwire.core.checks.GroundedChecks
import com.tripwire.core.engine.CaseState
import com.tripwire.core.engine.ProgressionEngine
import com.tripwire.core.engine.SignalContext
import com.tripwire.core.engine.SignalHit
import com.tripwire.core.engine.Signals
import com.tripwire.core.engine.ThresholdOffsets
import com.tripwire.core.entity.EntityExtractor
import com.tripwire.core.explain.ExplanationBuilder
import com.tripwire.core.explain.WarningContent
import com.tripwire.core.ledger.Feedback
import com.tripwire.core.ledger.InterventionLevel
import com.tripwire.core.ledger.InterventionRecord
import com.tripwire.core.ledger.LedgerStore
import com.tripwire.core.ledger.UserChoice
import com.tripwire.core.model.CaseStatus
import com.tripwire.core.model.Counterparty
import com.tripwire.core.model.CounterpartyLink
import com.tripwire.core.model.CounterpartyType
import com.tripwire.core.model.Entities
import com.tripwire.core.model.Event
import com.tripwire.core.model.EventType
import com.tripwire.core.model.LinkReason
import com.tripwire.core.model.Observation
import com.tripwire.core.model.Stage
import com.tripwire.core.model.TacticTag
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.script.ScriptPack
import com.tripwire.core.tactic.KeywordTagger
import com.tripwire.core.tactic.ResilientTacticReader
import com.tripwire.core.tactic.TacticInput
import com.tripwire.core.tactic.TacticReader
import com.tripwire.core.tactic.TransactionalFilter

/** Settings the pipeline reads on every call, so changes apply at once. */
data class PipelineConfig(
    val language: String = "en",
    val offsets: ThresholdOffsets = ThresholdOffsets(),
    val allyName: String? = null,
    /** Collectors the user switched off (SET-01), by event type. */
    val disabledTypes: Set<EventType> = emptySet(),
    val paused: Boolean = false,
)

sealed interface IngestResult {
    data class Stored(val eventId: Long, val counterpartyId: String, val caseId: String) : IngestResult
    data class Ignored(val reason: String) : IngestResult
}

data class QuietNotice(
    val caseId: String,
    val interventionId: Long,
    val title: String,
    val body: String,
    val stage: Stage,
)

data class ProcessResult(
    val caseId: String,
    val state: CaseState,
    val tags: List<TacticTag>,
    val notice: QuietNotice?,
    val elapsedMs: Long,
)

/** The broker's verdict at a tripwire moment (PRD 11.2). */
data class MomentDecision(
    val moment: TripwireMoment,
    val caseId: String?,
    val show: Boolean,
    val interventionId: Long?,
    val warning: WarningContent?,
    val checks: List<CheckResult>,
    val hardRule: String?,
    val risk: Int,
    val elapsedMs: Long,
) {
    companion object {
        fun none(moment: TripwireMoment, elapsed: Long, checks: List<CheckResult> = emptyList()) =
            MomentDecision(moment, null, false, null, null, checks, null, 0, elapsed)
    }
}

data class ChoiceEffect(
    val alertAlly: Boolean,
    /** Pin the "I already paid" shortcut until this time (INT-08), or null. */
    val pinPaidShortcutUntil: Long?,
    /** Check in with the user at this time (INT-08), or null. */
    val checkInAt: Long?,
)

/**
 * The on-device pipeline: normaliser, ledger, tactic reader, progression engine, grounded checks
 * and intervention broker (PRD 11). Android collectors feed it [Observation]s; it returns what
 * to show. It has no Android dependency, so the replay harness runs exactly this code.
 */
class TripwirePipeline(
    val pack: ScriptPack,
    val store: LedgerStore,
    model: TacticReader?,
    private val config: () -> PipelineConfig = { PipelineConfig() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val engine = ProgressionEngine(pack)
    val checks = GroundedChecks(pack)
    val explain = ExplanationBuilder(pack)
    private val rules = KeywordTagger(pack.keywordRules)
    @Volatile private var reader: TacticReader = ResilientTacticReader(model, rules)

    /** Guards every read-modify-write of case state and links. */
    private val caseLock = Any()

    private val messagingLabels = pack.apps.messaging.associate { it.packageName to it.label }
    private val commonDomains = setOf("whatsapp.com", "wa.me", "google.com", "youtube.com", "youtu.be", "play.google.com", "t.me", "telegram.me")

    /** Swaps the tactic reader, e.g. once the model has loaded or after it was released. */
    fun setModel(model: TacticReader?) {
        reader = ResilientTacticReader(model, rules)
    }

    // ---------------------------------------------------------------------------------------
    // Messages, group adds, calls, payment SMS: journey 2 and 3
    // ---------------------------------------------------------------------------------------

    /** Writes the observation to the ledger before any tagging, so nothing is lost (PRD 14.5). */
    fun ingest(obs: Observation): IngestResult {
        val cfg = config()
        if (cfg.paused) return IngestResult.Ignored("paused")
        if (obs.type in cfg.disabledTypes) return IngestResult.Ignored("collector off")
        if (obs.fromSavedContact) return IngestResult.Ignored("saved contact") // SIG-03: text discarded here
        if (obs.type.isMoment()) return IngestResult.Ignored("moment: use onMoment")

        if (obs.type == EventType.PAYMENT_SMS) return ingestPaymentSms(obs)

        val entities = EntityExtractor.extract(obs.text) +
            Entities(phoneNumbers = listOfNotNull(obs.senderPhone?.let { EntityExtractor.normalizePhone(it) }))
        val cp = resolveCounterparty(obs, entities) ?: return IngestResult.Ignored("trusted")
        // Messaging apps repeat earlier messages in each new notification, and an app restart
        // forgets what was seen. The ledger is the memory: identical text from the same
        // counterparty with the same send time is the same message. The same text sent again
        // later is a new message, since scammers repeat their demands.
        if (obs.type == EventType.MESSAGE && obs.text != null &&
            store.eventsFor(listOf(cp.id)).any { it.type == EventType.MESSAGE && it.text == obs.text && kotlin.math.abs(it.timestamp - obs.timestamp) < SAME_MESSAGE_MS }
        ) {
            return IngestResult.Ignored("duplicate")
        }
        val event = Event(
            id = 0,
            counterpartyId = cp.id,
            app = obs.app,
            type = obs.type,
            text = obs.text,
            entities = entities,
            timestamp = obs.timestamp,
            source = obs.source,
            senderName = obs.senderName,
            groupName = obs.groupName,
            isVideoCall = obs.isVideoCall,
        )
        return synchronized(caseLock) {
            val id = store.insertEvent(event)
            linkCounterparty(cp, entities, obs)
            IngestResult.Stored(id, cp.id, rootOf(cp.id))
        }
    }

    /** Tags the stored event and updates the case. May run seconds after [ingest] (PRD 11.1). */
    fun process(eventId: Long): ProcessResult? {
        val started = System.nanoTime()
        val e = store.event(eventId) ?: return null
        val cp = store.counterparty(e.counterpartyId) ?: return null
        if (cp.trusted) return null

        val transactional = e.text != null && TransactionalFilter.isTransactional(e.text)
        val tags = if (e.type == EventType.MESSAGE && e.text != null && !transactional) {
            val context = store.eventsFor(listOf(cp.id))
                .filter { it.type == EventType.MESSAGE && it.id != e.id && it.timestamp <= e.timestamp && it.text != null }
                .takeLast(6).map { it.text!! }
            reader.read(
                TacticInput(
                    message = e.text,
                    context = context,
                    appName = messagingLabels[e.app] ?: e.app,
                    isGroup = cp.type == CounterpartyType.GROUP,
                ),
            )?.tags.orEmpty()
        } else {
            emptyList()
        }
        store.saveTags(e.id, tags)

        // Tagging may take hundreds of milliseconds; only the state update holds the lock, so a
        // payment moment on another thread never waits for the model (INT-04).
        return synchronized(caseLock) { applyTagged(e, cp, tags, transactional, started) }
    }

    private fun applyTagged(e: Event, cp: Counterparty, tags: List<TacticTag>, transactional: Boolean, started: Long): ProcessResult {
        val caseId = rootOf(cp.id)
        val members = membersOf(caseId)
        val calls = callContext(members, e.timestamp)
        val ctx = SignalContext(
            // A private chat that joined a group's case, as a member or by a hand-off (LED-02, SIG-15).
            privateAfterGroup = cp.type == CounterpartyType.PERSON && caseId != cp.id &&
                store.counterparty(caseId)?.type == CounterpartyType.GROUP,
            duringCall = calls.active,
            recentCall = calls.recent,
            longCall = e.type == EventType.CALL_ENDED && calls.lastDurationMs >= LONG_CALL_MS,
            crossAppHandoff = members.flatMap { store.counterparty(it)?.apps.orEmpty() }.toSet().size > 1,
        )
        val hits = Signals.fromEvent(e, tags, ctx).toMutableList()
        if (transactional) hits += SignalHit("ctx:transactional", 1.0)

        val before = store.caseState(caseId) ?: engine.newCase(caseId, e.timestamp)
        val upd = engine.apply(before, hits, e.id, e.timestamp, config().offsets)
        var state = upd.state
        store.addEvidence(upd.evidence)

        var notice: QuietNotice? = null
        val watch = engine.watchThreshold(config().offsets)
        if (state.isOpen && state.risk >= watch && state.stage !in state.noticedStages) {
            val lang = config().language
            val title = explain.noticeTitle(lang)
            val body = explain.noticeBody(state, lang)
            val iid = store.saveIntervention(
                InterventionRecord(
                    caseId = caseId, level = InterventionLevel.QUIET_NOTICE, moment = null,
                    subject = state.stage.name, reasons = listOf(body), time = e.timestamp,
                ),
            )
            state = state.copy(noticedStages = state.noticedStages + state.stage)
            notice = QuietNotice(caseId, iid, title, body, state.stage)
        }
        store.saveCaseState(state)
        return ProcessResult(caseId, state, tags, notice, (System.nanoTime() - started) / 1_000_000)
    }

    /** Convenience for collectors and the replay harness: ingest, then process or evaluate. */
    fun onObservation(obs: Observation): PipelineOutput {
        if (obs.type.isMoment()) return PipelineOutput(moment = onMoment(obs))
        return when (val r = ingest(obs)) {
            is IngestResult.Ignored -> PipelineOutput(ignored = r.reason)
            is IngestResult.Stored -> {
                val p = process(r.eventId)
                val callMoment = if (obs.type == EventType.CALL_STARTED) inCallNoticeFor(r.caseId, obs) else null
                PipelineOutput(eventId = r.eventId, caseId = r.caseId, processed = p, moment = callMoment)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tripwire moments: journeys 4, 5, 6 and 7
    // ---------------------------------------------------------------------------------------

    /**
     * Evaluates an install, payment or screen-share moment. Uses stored case state only and never
     * calls the language model, so it stays well under the 300 ms budget (INT-04).
     */
    fun onMoment(obs: Observation): MomentDecision = synchronized(caseLock) { onMomentLocked(obs) }

    private fun onMomentLocked(obs: Observation): MomentDecision {
        val started = System.nanoTime()
        val moment = obs.type.moment() ?: return MomentDecision.none(TripwireMoment.PAYMENT, 0)
        val cfg = config()
        if (cfg.paused || obs.type in cfg.disabledTypes) return MomentDecision.none(moment, elapsed(started))
        val now = obs.timestamp

        val handle = obs.upi?.payeeHandle
        val momentEntities = Entities(
            upiHandles = listOfNotNull(handle),
            appNames = listOfNotNull(obs.installedLabel),
        )
        var caseId = linkMomentToCase(obs, now)

        // A known-bad payee or link warns even without a tracked conversation (CHK-05).
        val knownBad = checks.knownBad(listOfNotNull(handle), emptyList(), emptyList())
        if (caseId == null && knownBad == null) return MomentDecision.none(moment, elapsed(started))
        if (caseId == null) {
            caseId = "payee:${handle ?: obs.installedPackage ?: obs.app}"
            if (store.counterparty(caseId) == null) {
                store.upsertCounterparty(
                    Counterparty(caseId, handle ?: obs.installedLabel ?: obs.app, CounterpartyType.PERSON, setOfNotNull(handle), setOf(obs.app), now, now),
                )
            }
        }

        val event = Event(
            id = 0, counterpartyId = caseId, app = obs.app, type = obs.type, text = null,
            entities = momentEntities, timestamp = now, source = obs.source,
            installedPackage = obs.installedPackage, installedLabel = obs.installedLabel,
            installerPackage = obs.installerPackage, upi = obs.upi,
        )
        val eventId = store.insertEvent(event)
        val members = membersOf(caseId)
        val stateBefore = store.caseState(caseId) ?: engine.newCase(caseId, now)

        val results = runChecks(moment, obs, stateBefore, members) + listOfNotNull(knownBad)
        results.forEach { store.saveCheck(caseId, it, now) }
        val momentSignals = results.mapNotNull { it.signal }.toSet()

        val calls = callContext(members, now)
        val hits = Signals.fromEvent(
            store.event(eventId)!!, emptyList(),
            SignalContext(duringCall = calls.active, recentCall = calls.recent),
        ) + momentSignals.map { SignalHit(it, 1.0) }
        val upd = engine.apply(stateBefore, hits, eventId, now, cfg.offsets)
        store.addEvidence(upd.evidence)
        var state = upd.state

        val risk = engine.riskAt(state, now)
        val warn = engine.warnThreshold(cfg.offsets)
        // A hard rule is recorded only when it is what allowed the warning (ENG-04 audit trail).
        val rule = if (risk >= warn) null else engine.hardRule(state, moment, momentSignals)?.second?.id
            ?: if (knownBad != null) "known_bad" else null
        // The install screen shows only the app's name, so installs are matched by name: the warning
        // on the install screen and the one after the install are the same warning (SIG-11, INT-09).
        val subject = handle ?: obs.installedLabel ?: obs.installedPackage ?: obs.app
        val trusted = members.any { store.counterparty(it)?.trusted == true }
        val duplicate = store.interventions(caseId).any {
            it.level == InterventionLevel.FULL_SCREEN && it.moment == moment && it.subject == subject && now - it.time < dedupeWindow(moment)
        }
        val show = !trusted && !duplicate && (risk >= warn || rule != null)

        var interventionId: Long? = null
        var warning: WarningContent? = null
        if (show) {
            val events = store.eventsFor(members)
            val tagsByEvent = events.associate { it.id to store.tags(it.id) }
            val primaryCheck = results.firstOrNull { it.outcome == CheckOutcome.FAIL } ?: results.firstOrNull()
            warning = explain.warning(moment, state, events, tagsByEvent, primaryCheck, cfg.allyName, cfg.language, now)
            interventionId = store.saveIntervention(
                InterventionRecord(
                    caseId = caseId, level = InterventionLevel.FULL_SCREEN, moment = moment,
                    subject = subject, reasons = warning.reasons, time = now,
                ),
            )
            state = state.copy(status = CaseStatus.WARNED, openedAt = state.openedAt ?: now)
        }
        store.saveCaseState(state)
        return MomentDecision(moment, caseId, show, interventionId, warning, results, rule, risk, elapsed(started))
    }

    /** INT-10: during a call that matches the digital-arrest pattern, a notice over the call screen. */
    fun inCallNoticeFor(caseId: String, obs: Observation): MomentDecision? = synchronized(caseLock) { inCallNotice(caseId, obs) }

    private fun inCallNotice(caseId: String, obs: Observation): MomentDecision? {
        val state = store.caseState(caseId) ?: return null
        val cfg = config()
        val family = state.topFamily?.let { pack.family(it) } ?: return null
        if (family.templates[TripwireMoment.CALL] == null) return null
        val now = obs.timestamp
        if (state.stage.number < Stage.HOOK.number || engine.riskAt(state, now) < engine.watchThreshold(cfg.offsets)) return null
        val duplicate = store.interventions(caseId).any { it.moment == TripwireMoment.CALL && now - it.time < DEDUPE_MS }
        if (duplicate) return null
        val members = membersOf(caseId)
        val events = store.eventsFor(members)
        val warning = explain.warning(
            TripwireMoment.CALL, state, events, events.associate { it.id to store.tags(it.id) },
            null, cfg.allyName, cfg.language, now,
        )
        val id = store.saveIntervention(
            InterventionRecord(
                caseId = caseId, level = InterventionLevel.FULL_SCREEN, moment = TripwireMoment.CALL,
                subject = obs.senderPhone ?: obs.senderName, reasons = warning.reasons, time = now,
            ),
        )
        return MomentDecision(TripwireMoment.CALL, caseId, true, id, warning, emptyList(), null, engine.riskAt(state, now), 0)
    }

    private fun runChecks(moment: TripwireMoment, obs: Observation, state: CaseState, members: List<String>): List<CheckResult> {
        val out = ArrayList<CheckResult>()
        when (moment) {
            TripwireMoment.PAYMENT -> {
                val handle = obs.upi?.payeeHandle
                if (handle != null) {
                    // CHK-01 applies to investment cases and to anyone claiming to be a broker.
                    val investment = state.topFamily == INVESTMENT || state.has("ctx:claims_broker")
                    checks.validHandle(handle, applies = investment)?.let { out += it }
                    // Who the payee claims to be: named contacts and groups, plus any bank or broker
                    // the chat invoked ("your SBI account"), so a payment to a person fails (CHK-06).
                    val claimed = members.mapNotNull { store.counterparty(it) }
                        .filter { it.type == CounterpartyType.GROUP || !NotificationLike.isRawNumber(it.displayName) }
                        .map { it.displayName } + brandsMentioned(members)
                    checks.nameMismatch(obs.upi.payeeName, claimed)?.takeIf { it.outcome == CheckOutcome.FAIL }?.let { out += it }
                }
            }
            TripwireMoment.INSTALL -> {
                val pkg = obs.installedPackage ?: obs.app
                val source = checks.installSource(pkg, obs.installedLabel, obs.installerPackage)
                out += source
                // Only an app from a link can be a lookalike; store apps from the brand's own developer are not.
                if (source.outcome == CheckOutcome.FAIL) {
                    obs.installedLabel?.let { label -> checks.lookalikeApp(pkg, label)?.takeIf { it.outcome == CheckOutcome.FAIL }?.let { out += it } }
                }
            }
            TripwireMoment.SCREEN_SHARE, TripwireMoment.CALL -> Unit
        }
        return out
    }

    private fun brandsMentioned(members: List<String>): List<String> {
        val text = store.eventsFor(members).mapNotNull { it.text }.joinToString(" ").lowercase()
        if (text.isEmpty()) return emptyList()
        return pack.brands.filter { b ->
            (listOf(b.name) + b.aliases).any { alias ->
                Regex("\\b${Regex.escape(alias.lowercase())}\\b").containsMatchIn(text)
            }
        }.map { it.name }
    }

    /**
     * ENG-07: entity match first (the handle, link or app appears in the ledger), then time match
     * (an event from a counterparty above the watch threshold within the last 30 minutes).
     */
    fun linkMomentToCase(obs: Observation, now: Long): String? {
        val cfg = config()
        val live = store.allCaseStates().filter { it.status != CaseStatus.TRUSTED && it.status != CaseStatus.CLOSED }
        val handle = obs.upi?.payeeHandle?.lowercase()
        val label = obs.installedLabel?.let { EntityExtractor.normalizeName(it) }?.takeIf { it.length >= 3 }
        val pkg = obs.installedPackage?.lowercase()

        val byEntity = live.filter { case ->
            store.eventsFor(membersOf(case.caseId)).any { e ->
                (handle != null && handle in e.entities.upiHandles) ||
                    (label != null && (e.entities.appNames.any { n -> EntityExtractor.normalizeName(n).let { it.contains(label) || label.contains(it) } } ||
                        e.entities.apkLinks.any { EntityExtractor.normalizeName(it).contains(label) })) ||
                    (pkg != null && e.entities.apkLinks.any { it.lowercase().contains(pkg) })
            }
        }.maxByOrNull { engine.riskAt(it, now) }
        if (byEntity != null) return byEntity.caseId

        val window = pack.thresholds.timeLinkMinutes * 60_000L
        return live.filter {
            now - it.lastEventAt in 0..window && engine.riskAt(it, now) >= engine.watchThreshold(cfg.offsets)
        }.maxByOrNull { engine.riskAt(it, now) }?.caseId
    }

    // ---------------------------------------------------------------------------------------
    // User responses
    // ---------------------------------------------------------------------------------------

    /** Logs the user's choice on a warning and applies its effect (PRD 7, "What happens on each choice"). */
    fun recordChoice(interventionId: Long, choice: UserChoice): ChoiceEffect = synchronized(caseLock) { recordChoiceLocked(interventionId, choice) }

    private fun recordChoiceLocked(interventionId: Long, choice: UserChoice): ChoiceEffect {
        val rec = store.intervention(interventionId) ?: return ChoiceEffect(false, null, null)
        store.updateIntervention(rec.copy(choice = choice))
        val now = clock()
        return when (choice) {
            UserChoice.PROCEEDED -> {
                store.caseState(rec.caseId)?.let { s ->
                    val upd = engine.apply(s, listOf(SignalHit("ctx:proceeded", 1.0)), null, now, config().offsets)
                    store.addEvidence(upd.evidence)
                    store.saveCaseState(upd.state)
                }
                ChoiceEffect(alertAlly = true, pinPaidShortcutUntil = now + DAY_MS, checkInAt = now + HOUR_MS)
            }
            UserChoice.MARKED_TRUSTED -> {
                markTrusted(rec.caseId)
                ChoiceEffect(false, null, null)
            }
            else -> ChoiceEffect(alertAlly = false, pinPaidShortcutUntil = null, checkInAt = null)
        }
    }

    /** FBK-01: "Was this a scam?" Returns the new per-user offsets (FBK-02, ENG-10). */
    fun recordFeedback(interventionId: Long, feedback: Feedback, current: ThresholdOffsets): ThresholdOffsets {
        val rec = store.intervention(interventionId) ?: return current
        store.updateIntervention(rec.copy(feedback = feedback))
        return ThresholdTuner.adjust(current, rec.level, feedback, pack.thresholds.tuneMin, pack.thresholds.tuneMax)
    }

    /** LED-05: stop tracking the counterparty and everything linked to it, and delete stored text. */
    fun markTrusted(caseOrCounterpartyId: String) {
        val root = rootOf(caseOrCounterpartyId)
        val members = membersOf(root)
        for (id in members) {
            store.counterparty(id)?.let { store.upsertCounterparty(it.copy(trusted = true)) }
            store.eventsFor(listOf(id)).forEach { e ->
                if (e.text != null) store.updateEvent(e.copy(text = null, textDeleted = true))
            }
        }
        store.caseState(root)?.let { store.saveCaseState(it.copy(status = CaseStatus.TRUSTED)) }
    }

    /** LED-04: delete one ledger and its case. */
    fun deleteCase(caseId: String) {
        val root = rootOf(caseId)
        membersOf(root).forEach { store.deleteCounterparty(it) }
        store.deleteCaseState(root)
        store.setHold(root, false)
    }

    /**
     * Retention (LED-03, LED-06, PRD 12.3): raw text after [retentionDays]; counterparties that
     * never reached the watch threshold go with their text; cases close 90 days after their last
     * event. Cases on hold for an evidence pack are untouched (LED-07).
     */
    fun purge(retentionDays: Int, now: Long = clock()): PurgeReport {
        val held = store.heldCases()
        val cutoff = now - retentionDays * DAY_MS
        var texts = 0
        var counterparties = 0
        var cases = 0
        val roots = store.allCounterparties().groupBy { rootOf(it.id) }
        for ((root, cps) in roots) {
            if (root in held) continue
            val state = store.caseState(root)
            val events = store.eventsFor(cps.map { it.id })
            val last = events.maxOfOrNull { it.timestamp } ?: cps.maxOf { it.lastSeen }
            if (state?.openedAt == null && last < cutoff) {
                cps.forEach { store.deleteCounterparty(it.id) }
                store.deleteCaseState(root)
                counterparties += cps.size
                continue
            }
            if (state != null && now - last > CASE_LIFETIME_MS) {
                cps.forEach { store.deleteCounterparty(it.id) }
                store.deleteCaseState(root)
                cases++
                continue
            }
            events.filter { it.timestamp < cutoff && it.text != null }.forEach {
                store.updateEvent(it.copy(text = null, textDeleted = true))
                texts++
            }
        }
        return PurgeReport(texts, counterparties, cases)
    }

    // ---------------------------------------------------------------------------------------
    // Counterparties and linking
    // ---------------------------------------------------------------------------------------

    /** Groups are counterparties; senders inside them are tracked on each event (LED-02). */
    private fun resolveCounterparty(obs: Observation, entities: Entities): Counterparty? {
        val phone = obs.senderPhone?.let { EntityExtractor.normalizePhone(it) }
        val (id, type, display) = when {
            obs.groupName != null -> Triple("group:${obs.app}:${EntityExtractor.normalizeName(obs.groupName)}", CounterpartyType.GROUP, obs.groupName)
            phone != null -> Triple("tel:$phone", CounterpartyType.PERSON, obs.senderName ?: phone)
            else -> Triple("name:${obs.app}:${EntityExtractor.normalizeName(obs.senderName ?: "unknown")}", CounterpartyType.PERSON, obs.senderName ?: "Unknown")
        }
        val existing = store.counterparty(id)
        if (existing?.trusted == true || store.counterparty(rootOf(id))?.trusted == true) return null

        val identifiers = HashSet<String>()
        phone?.let { identifiers += "tel:$it" }
        // A number the counterparty gives out ("WhatsApp me at ...") joins later chats from it (SIG-15).
        entities.phoneNumbers.forEach { identifiers += "tel:$it" }
        entities.upiHandles.forEach { identifiers += "upi:$it" }
        entities.telegramHandles.forEach { identifiers += "tg:$it" }
        entities.apkLinks.forEach { identifiers += "apk:${EntityExtractor.domainOf(it)}" }
        entities.urls.map { EntityExtractor.domainOf(it) }.filter { it !in commonDomains }.forEach { identifiers += "web:$it" }
        if (type == CounterpartyType.GROUP) {
            obs.senderName?.let { identifiers += "member:${EntityExtractor.normalizeName(it.removePrefix("~"))}" }
            phone?.let { identifiers += "member:$it" }
        }
        val updated = Counterparty(
            id = id,
            displayName = existing?.displayName ?: display,
            type = type,
            identifiers = (existing?.identifiers.orEmpty() + identifiers),
            apps = existing?.apps.orEmpty() + obs.app,
            firstSeen = existing?.firstSeen ?: obs.timestamp,
            lastSeen = maxOf(existing?.lastSeen ?: 0, obs.timestamp),
            trusted = false,
        )
        store.upsertCounterparty(updated)
        return updated
    }

    /** SIG-15 and LED-02: tie a counterparty to others that share a number, handle or link, or a group it belongs to. */
    private fun linkCounterparty(cp: Counterparty, entities: Entities, obs: Observation) {
        if (cp.type == CounterpartyType.PERSON) {
            val keys = buildList {
                obs.senderName?.let { add("member:${EntityExtractor.normalizeName(it.removePrefix("~"))}") }
                obs.senderPhone?.let { EntityExtractor.normalizePhone(it) }?.let { add("member:$it") }
            }
            keys.flatMap { store.counterpartiesWithIdentifier(it) }
                .filter { it.type == CounterpartyType.GROUP && it.id != cp.id }
                .distinctBy { it.id }
                .forEach { addLinkIfNoCycle(cp.id, it.id, LinkReason.GROUP_MEMBER, 0.8) }
        }
        val shared = buildList {
            entities.upiHandles.forEach { add("upi:$it" to LinkReason.SAME_HANDLE) }
            entities.telegramHandles.forEach { add("tg:$it" to LinkReason.HANDOFF_MESSAGE) }
            entities.apkLinks.forEach { add("apk:${EntityExtractor.domainOf(it)}" to LinkReason.SAME_LINK) }
            entities.phoneNumbers.forEach { add("tel:$it" to LinkReason.SAME_NUMBER) }
        }
        for ((key, reason) in shared) {
            for (other in store.counterpartiesWithIdentifier(key)) {
                if (other.id == cp.id || rootOf(other.id) == rootOf(cp.id)) continue
                // Newer joins older, so the case keeps its history.
                val (from, to) = if (other.firstSeen <= cp.firstSeen) cp.id to other.id else other.id to cp.id
                addLinkIfNoCycle(from, to, reason, 0.9)
            }
        }
    }

    private fun addLinkIfNoCycle(from: String, to: String, reason: LinkReason, confidence: Double) {
        val rootFrom = rootOf(from)
        val rootTo = rootOf(to)
        if (rootFrom == rootTo) return
        // Link the root of the joining side, so chains stay short and the state merges once.
        store.addLink(CounterpartyLink(rootFrom, rootTo, reason, confidence))
        mergeCase(rootFrom, rootTo)
    }

    /** Folds the joining case's evidence into the case it joined. */
    private fun mergeCase(fromRoot: String, toRoot: String) {
        val from = store.caseState(fromRoot) ?: return
        val to = store.caseState(toRoot) ?: engine.newCase(toRoot, from.lastEventAt)
        val hits = from.signals.map { (s, v) -> SignalHit(s, v) }
        val upd = engine.apply(to, hits, null, maxOf(to.lastEventAt, from.lastEventAt), config().offsets)
        store.saveCaseState(
            upd.state.copy(
                openedAt = listOfNotNull(to.openedAt, from.openedAt, upd.state.openedAt).minOrNull(),
                noticedStages = to.noticedStages + from.noticedStages,
            ),
        )
        store.addEvidence(upd.evidence)
        store.deleteCaseState(fromRoot)
    }

    fun rootOf(id: String): String {
        var cur = id
        val seen = HashSet<String>()
        while (seen.add(cur)) {
            val next = store.linksFrom(cur).firstOrNull()?.toId ?: return cur
            cur = next
        }
        return cur
    }

    fun membersOf(caseId: String): List<String> {
        val root = rootOf(caseId)
        val out = LinkedHashSet<String>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!out.add(id)) continue
            store.linksTo(id).forEach { queue.addLast(it.fromId) }
        }
        return out.toList()
    }

    private fun ingestPaymentSms(obs: Observation): IngestResult {
        val text = obs.text ?: return IngestResult.Ignored("empty")
        val parsed = com.tripwire.core.parse.PaymentSmsParser.parse(text, obs.timestamp)
            ?: return IngestResult.Ignored("not a payment")
        val probe = obs.copy(upi = com.tripwire.core.model.UpiPayment(parsed.payeeHandle ?: "", parsed.payeeName, parsed.amount, utr = parsed.utr))
        val caseId = linkMomentToCase(probe, obs.timestamp) ?: return IngestResult.Ignored("no linked case")
        val event = Event(
            id = 0, counterpartyId = caseId, app = obs.app, type = EventType.PAYMENT_SMS, text = text,
            entities = EntityExtractor.extract(text), timestamp = obs.timestamp, source = obs.source,
            upi = probe.upi,
        )
        val id = store.insertEvent(event)
        return IngestResult.Stored(id, caseId, caseId)
    }

    private data class CallState(val active: Boolean, val recent: Boolean, val lastDurationMs: Long)

    private fun callContext(members: List<String>, now: Long): CallState {
        val calls = store.eventsFor(members).filter { (it.type == EventType.CALL_STARTED || it.type == EventType.CALL_ENDED) && it.timestamp <= now }
        val lastStart = calls.lastOrNull { it.type == EventType.CALL_STARTED }
        val lastEnd = calls.lastOrNull { it.type == EventType.CALL_ENDED }
        val active = lastStart != null && (lastEnd == null || lastEnd.timestamp < lastStart.timestamp) && now - lastStart.timestamp < 3 * HOUR_MS
        val window = pack.thresholds.timeLinkMinutes * 60_000L
        val recent = lastEnd != null && now - lastEnd.timestamp <= window
        val duration = if (lastStart != null && lastEnd != null && lastEnd.timestamp >= lastStart.timestamp) lastEnd.timestamp - lastStart.timestamp else 0
        return CallState(active, recent, duration)
    }

    /**
     * INT-06: one warning per tripwire moment. A payment app can come to the foreground many times
     * in one sitting, so payments share a 10-minute window; each install is its own moment, so a
     * reinstall after removal warns again, while a duplicate broadcast within a minute does not.
     */
    private fun dedupeWindow(moment: TripwireMoment): Long = when (moment) {
        TripwireMoment.INSTALL -> INSTALL_DEDUPE_MS
        else -> DEDUPE_MS
    }

    private fun elapsed(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        const val INVESTMENT = "fake_investment"
        const val DIGITAL_ARREST = "digital_arrest"
        const val HOUR_MS = 60L * 60 * 1000
        const val DAY_MS = 24 * HOUR_MS
        const val DEDUPE_MS = 10 * 60 * 1000L
        const val SAME_MESSAGE_MS = 2_000L
        const val INSTALL_DEDUPE_MS = 60 * 1000L
        const val LONG_CALL_MS = 15 * 60 * 1000L
        const val CASE_LIFETIME_MS = 90 * DAY_MS
    }
}

data class PipelineOutput(
    val eventId: Long? = null,
    val caseId: String? = null,
    val processed: ProcessResult? = null,
    val moment: MomentDecision? = null,
    val ignored: String? = null,
)

data class PurgeReport(val textsDeleted: Int, val counterpartiesDeleted: Int, val casesClosed: Int)

/** Per-user tuning within fixed limits (ENG-10, FBK-02). */
object ThresholdTuner {
    fun adjust(current: ThresholdOffsets, level: InterventionLevel, feedback: Feedback, min: Int, max: Int): ThresholdOffsets {
        val (dWatch, dWarn) = when (feedback) {
            Feedback.GENUINE -> if (level == InterventionLevel.FULL_SCREEN) 2 to 3 else 2 to 1
            Feedback.SCAM -> -1 to -2
            Feedback.NOT_SURE -> 0 to 0
        }
        return ThresholdOffsets(
            watch = (current.watch + dWatch).coerceIn(min, max),
            warn = (current.warn + dWarn).coerceIn(min, max),
        )
    }
}

internal object NotificationLike {
    fun isRawNumber(s: String) = s.filter { it.isDigit() }.length >= 10 && s.none { it.isLetter() }
}

fun EventType.isMoment(): Boolean = moment() != null

fun EventType.moment(): TripwireMoment? = when (this) {
    EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED -> TripwireMoment.INSTALL
    EventType.PAYMENT_APP_OPENED, EventType.UPI_LINK_OPENED -> TripwireMoment.PAYMENT
    EventType.SCREEN_SHARE_STARTED, EventType.REMOTE_APP_OPENED -> TripwireMoment.SCREEN_SHARE
    else -> null
}

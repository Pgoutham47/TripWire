package com.tripwire.core.engine

import com.tripwire.core.model.CaseStatus
import com.tripwire.core.model.Stage
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.script.FamilyDef
import com.tripwire.core.script.HardRule
import com.tripwire.core.script.ScriptPack
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

const val BENIGN = "benign"

/** One signal seen on one event, with its strength in 0..1 (a tag's confidence, or 1 for facts). */
@Serializable
data class SignalHit(val signal: String, val strength: Double)

/**
 * The engine's view of one case (table `case_state`). A case is keyed by the root counterparty,
 * so a group and the private chats linked to it share one state (LED-02, SIG-15).
 */
@Serializable
data class CaseState(
    val caseId: String,
    /** Accumulated strength per signal, combined across events as a noisy-OR. */
    val signals: Map<String, Double> = emptyMap(),
    val familyLogits: Map<String, Double> = emptyMap(),
    /** Probability of each family and of [BENIGN]; sums to 1 (ENG-01). */
    val familyProbs: Map<String, Double> = emptyMap(),
    /** Furthest stage reached per family. Stages never move backward (ENG-09). */
    val familyStages: Map<String, Stage> = emptyMap(),
    val topFamily: String? = null,
    val stage: Stage = Stage.CONTACT,
    /** Risk 0..100 as of [lastEventAt]; use [ProgressionEngine.riskAt] for the decayed value. */
    val risk: Int = 0,
    val openedAt: Long? = null,
    val lastChanged: Long = 0,
    val lastEventAt: Long = 0,
    val status: CaseStatus = CaseStatus.WATCHING,
    /** Stages for which a quiet notification was already posted (INT-06). */
    val noticedStages: Set<Stage> = emptySet(),
    /** Distinct-event counts per signal, used for repetition signals. */
    val counts: Map<String, Int> = emptyMap(),
) {
    val isOpen: Boolean get() = openedAt != null && status != CaseStatus.TRUSTED && status != CaseStatus.CLOSED
    fun has(signal: String): Boolean = (signals[signal] ?: 0.0) > 0.0
}

/** Why the case changed: one row per signal that moved a family's log-odds (ENG-04, table `case_evidence`). */
@Serializable
data class EvidenceRecord(
    val caseId: String,
    val eventId: Long?,
    val signal: String,
    val family: String,
    val deltaLogit: Double,
    val stageBefore: Stage,
    val stageAfter: Stage,
    val riskBefore: Int,
    val riskAfter: Int,
    val time: Long,
)

data class EngineUpdate(
    val state: CaseState,
    val evidence: List<EvidenceRecord>,
    /** True the first time this case's risk reaches the watch threshold: a case opens. */
    val opened: Boolean,
    /** Non-null when the leading stage moved forward on this update. */
    val advancedTo: Stage?,
)

/** Per-user threshold offsets from feedback tuning (ENG-10). */
@Serializable
data class ThresholdOffsets(val watch: Int = 0, val warn: Int = 0)

/**
 * The progression engine (PRD 10.3, ENG-01..10). Deterministic and auditable: every family's
 * log-odds is a weighted sum of signal strengths, so each change in risk traces to evidence.
 * It is a few hundred lines of arithmetic, not a neural network, and runs in well under 50 ms.
 */
class ProgressionEngine(private val pack: ScriptPack) {

    private val families: List<FamilyDef> = pack.families

    fun watchThreshold(offsets: ThresholdOffsets = ThresholdOffsets()): Int =
        (pack.thresholds.watch + offsets.watch.coerceIn(pack.thresholds.tuneMin, pack.thresholds.tuneMax)).coerceIn(1, 99)

    fun warnThreshold(offsets: ThresholdOffsets = ThresholdOffsets()): Int =
        (pack.thresholds.warn + offsets.warn.coerceIn(pack.thresholds.tuneMin, pack.thresholds.tuneMax)).coerceIn(watchThreshold(offsets), 100)

    fun newCase(caseId: String, time: Long): CaseState = recompute(
        CaseState(caseId = caseId, lastChanged = time, lastEventAt = time),
    )

    /**
     * Applies the signals from one event. [eventId] is stored with the evidence. Signals from the
     * same event are counted once each; across events they combine as a noisy-OR.
     */
    fun apply(
        state: CaseState,
        hits: List<SignalHit>,
        eventId: Long?,
        time: Long,
        offsets: ThresholdOffsets = ThresholdOffsets(),
    ): EngineUpdate {
        val folded = hits.filter { it.strength > 0.0 }
            .groupBy { it.signal }
            .mapValues { (_, v) -> v.maxOf { it.strength }.coerceIn(0.0, 1.0) }

        val newSignals = state.signals.toMutableMap()
        val newCounts = state.counts.toMutableMap()
        for ((signal, s) in folded) {
            val old = newSignals[signal] ?: 0.0
            newSignals[signal] = 1.0 - (1.0 - old) * (1.0 - s)
            newCounts[signal] = (newCounts[signal] ?: 0) + 1
        }
        // Repeated demands for money are the mark of lock-in (PRD 9.3, 9.4).
        if ((newCounts["tag:payment_request"] ?: 0) >= 3 || (newCounts["event:payment_sms"] ?: 0) >= 2) {
            newSignals["ctx:repeat_payment"] = 1.0
        }

        val next = recompute(
            state.copy(
                signals = newSignals,
                counts = newCounts,
                lastEventAt = max(state.lastEventAt, time),
                lastChanged = time,
            ),
        )

        val evidence = ArrayList<EvidenceRecord>()
        for (family in families) {
            for ((signal, _) in folded) {
                val w = family.signals[signal] ?: continue
                val delta = w * ((newSignals[signal] ?: 0.0) - (state.signals[signal] ?: 0.0))
                if (delta == 0.0) continue
                evidence += EvidenceRecord(
                    caseId = state.caseId,
                    eventId = eventId,
                    signal = signal,
                    family = family.id,
                    deltaLogit = delta,
                    stageBefore = state.familyStages[family.id] ?: Stage.CONTACT,
                    stageAfter = next.familyStages[family.id] ?: Stage.CONTACT,
                    riskBefore = state.risk,
                    riskAfter = next.risk,
                    time = time,
                )
            }
        }

        val watch = watchThreshold(offsets)
        val opens = state.openedAt == null && next.risk >= watch
        val finalState = if (opens) next.copy(openedAt = time) else next
        val advanced = if (finalState.stage.number > state.stage.number) finalState.stage else null
        return EngineUpdate(finalState, evidence, opens, advanced)
    }

    /** Risk now, after slow decay since the last event (ENG-09). The stage never decays. */
    fun riskAt(state: CaseState, now: Long): Int {
        val days = ((now - state.lastEventAt).coerceAtLeast(0)).toDouble() / DAY_MS
        val factor = 0.5.pow(days / pack.thresholds.riskHalfLifeDays)
        return (state.risk * factor).roundToInt()
    }

    /**
     * ENG-06: a hard rule allows a warning at [moment] whatever the score. [momentSignals] are
     * signals true only for this moment, typically failed grounded checks.
     */
    fun hardRule(state: CaseState, moment: TripwireMoment, momentSignals: Set<String>): Pair<FamilyDef, HardRule>? {
        val present = state.signals.filterValues { it > 0.0 }.keys + momentSignals
        // The leading family's rules first, so the warning is credited to the right script.
        for (family in families.sortedByDescending { state.familyProbs[it.id] ?: 0.0 }) {
            val logit = state.familyLogits[family.id] ?: family.priorLogit
            for (rule in family.hardRules) {
                if (rule.moment != moment) continue
                if (logit < rule.minFamilyLogit) continue
                if (!present.containsAll(rule.requireAll)) continue
                if (rule.requireAny.isNotEmpty() && rule.requireAny.none { it in present }) continue
                if (!rule.failedChecks.all { "check:$it:fail" in present }) continue
                return family to rule
            }
        }
        return null
    }

    /** Family probabilities, stages and risk from the accumulated signals. Pure function of [s]. */
    private fun recompute(s: CaseState): CaseState {
        val logits = families.associate { f ->
            f.id to f.priorLogit + f.signals.entries.sumOf { (sig, w) -> w * (s.signals[sig] ?: 0.0) }
        }
        val maxLogit = max(0.0, logits.values.maxOrNull() ?: 0.0)
        val expBenign = exp(0.0 - maxLogit)
        val exps = logits.mapValues { (_, l) -> exp(l - maxLogit) }
        val z = expBenign + exps.values.sum()
        val probs = exps.mapValues { it.value / z } + (BENIGN to expBenign / z)

        val stages = families.associate { f ->
            val reached = f.stages.filter { def ->
                def.evidence.sumOf { sig -> max(0.0, f.signals[sig] ?: 1.0) * (s.signals[sig] ?: 0.0) } >= def.minEvidence
            }.maxOfOrNull { it.stage } ?: Stage.CONTACT
            val previous = s.familyStages[f.id] ?: Stage.CONTACT
            f.id to if (reached.number > previous.number) reached else previous
        }

        // The leading family is the most probable one; its stage and probability set the risk.
        // Choosing by probability, not by stage, stops a family that shares one strong tactic
        // from taking over just because its stage thresholds are lower.
        val bestFamily = families.maxByOrNull { probs.getValue(it.id) }?.id
        val stage = bestFamily?.let { stages.getValue(it) } ?: Stage.CONTACT
        val bestRisk = bestFamily?.let { familyRisk(stage, probs.getValue(it)) } ?: 0.0
        return s.copy(
            familyLogits = logits,
            familyProbs = probs,
            familyStages = stages,
            topFamily = bestFamily,
            stage = stage,
            risk = bestRisk.roundToInt().coerceIn(0, 100),
        )
    }

    /**
     * Base risk of the stage (PRD 9.1), scaled down while the family is less likely than not, plus
     * up to 15 points of evidence strength once it is more likely than not.
     */
    private fun familyRisk(stage: Stage, p: Double): Double {
        val g = (p / 0.5).coerceIn(0.0, 1.0)
        val h = ((p - 0.5) / 0.5).coerceIn(0.0, 1.0)
        return stage.baseRisk * g + 15.0 * h
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

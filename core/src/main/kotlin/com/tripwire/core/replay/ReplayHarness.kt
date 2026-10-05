package com.tripwire.core.replay

import com.tripwire.core.ledger.InMemoryLedgerStore
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.model.Stage
import com.tripwire.core.model.Tactic
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.pipeline.PipelineConfig
import com.tripwire.core.pipeline.TripwirePipeline
import com.tripwire.core.script.ScriptPack
import com.tripwire.core.tactic.TacticReader
import kotlinx.serialization.Serializable

/**
 * A whole conversation, replayed through the real pipeline (PRD 16.2 replay tests, PRD 22 demo
 * fallback). Steps carry minutes from the start so multi-day scripts replay in milliseconds.
 */
@Serializable
data class Scenario(
    val id: String,
    val description: String,
    /** The family the script represents, or null for a benign conversation. */
    val family: String? = null,
    val language: String = "en",
    val steps: List<Step>,
) {
    val benign: Boolean get() = family == null

    @Serializable
    data class Step(
        /** Minutes after the scenario starts. */
        val t: Long,
        val obs: Observation,
        /** Optional expectations checked by the harness. */
        val expectWarning: Boolean? = null,
        val expectNotice: Boolean? = null,
        val expectStageAtLeast: Stage? = null,
    )

    companion object {
        fun parse(text: String): Scenario = ScriptPack.json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class StepOutcome(
    val index: Int,
    val type: EventType,
    val caseId: String?,
    val stage: Stage?,
    val family: String?,
    val risk: Int?,
    val tags: List<Tactic>,
    val notice: Boolean,
    val warning: TripwireMoment?,
    val hardRule: String?,
    val headline: String?,
    val elapsedMs: Long,
    val failures: List<String>,
)

@Serializable
data class ScenarioReport(
    val id: String,
    val family: String?,
    val steps: List<StepOutcome>,
    /** Stage of the leading family when the first notice or warning appeared. */
    val stageAtFirstDetection: Stage?,
    /** True when detection happened before the first payment request or payment moment (PRD 4). */
    val detectedBeforePaymentRequest: Boolean,
    val warningsAt: List<TripwireMoment>,
    val noticeCount: Int,
    val finalFamily: String?,
    val finalStage: Stage?,
    val failures: List<String>,
) {
    val passed: Boolean get() = failures.isEmpty()
}

/** Aggregate numbers matching the acceptance criteria in PRD 16.3. */
@Serializable
data class SuiteReport(
    val scams: Int,
    val scamsDetectedBeforePayment: Int,
    val scamsCorrectFamily: Int,
    val benign: Int,
    val benignWithNotice: Int,
    val benignWithWarning: Int,
    val maxMomentMs: Long,
    val reports: List<ScenarioReport>,
) {
    val earlyDetectionRate: Double get() = if (scams == 0) 0.0 else scamsDetectedBeforePayment.toDouble() / scams
    val benignNoticeRate: Double get() = if (benign == 0) 0.0 else benignWithNotice.toDouble() / benign
}

class ReplayHarness(
    private val pack: ScriptPack,
    private val model: TacticReader? = null,
    private val config: PipelineConfig = PipelineConfig(),
) {
    /** Replays one scenario against a fresh in-memory ledger. */
    fun run(scenario: Scenario, start: Long = DEFAULT_START): ScenarioReport {
        var now = start
        val store = InMemoryLedgerStore()
        val pipeline = TripwirePipeline(pack, store, model, { config.copy(language = scenario.language.substringBefore('-')) }, { now })
        val outcomes = ArrayList<StepOutcome>()
        var firstDetectionStage: Stage? = null
        var firstDetectionIndex: Int? = null
        var firstPaymentIndex: Int? = null

        scenario.steps.forEachIndexed { i, step ->
            now = start + step.t * 60_000
            val obs = step.obs.copy(timestamp = now)
            val out = pipeline.onObservation(obs)
            val caseId = out.caseId ?: out.moment?.caseId
            val state = caseId?.let { store.caseState(pipeline.rootOf(it)) }
            val tags = out.processed?.tags.orEmpty().filter { it.confidence >= 0.5 }.map { it.tactic }
            val notice = out.processed?.notice != null
            val warning = out.moment?.takeIf { it.show }?.moment

            if (firstPaymentIndex == null && (Tactic.PAYMENT_REQUEST in tags || obs.type == EventType.UPI_LINK_OPENED || obs.type == EventType.PAYMENT_APP_OPENED)) {
                firstPaymentIndex = i
            }
            if (firstDetectionIndex == null && (notice || warning != null)) {
                firstDetectionIndex = i
                firstDetectionStage = state?.stage
            }

            val failures = ArrayList<String>()
            step.expectWarning?.let { if ((warning != null) != it) failures += "step $i: expected warning=$it, got ${warning ?: "none"} (risk ${out.moment?.risk ?: state?.risk})" }
            step.expectNotice?.let { if (notice != it) failures += "step $i: expected notice=$it, got $notice (risk ${state?.risk}, stage ${state?.stage})" }
            step.expectStageAtLeast?.let { want ->
                val got = state?.stage
                if (got == null || got.number < want.number) failures += "step $i: expected stage >= $want, got $got (risk ${state?.risk})"
            }
            outcomes += StepOutcome(
                index = i, type = obs.type, caseId = caseId, stage = state?.stage, family = state?.topFamily,
                risk = out.moment?.risk ?: state?.risk, tags = tags, notice = notice, warning = warning,
                hardRule = out.moment?.hardRule, headline = out.moment?.warning?.headline,
                elapsedMs = out.moment?.elapsedMs ?: out.processed?.elapsedMs ?: 0, failures = failures,
            )
        }

        val warnings = outcomes.mapNotNull { it.warning }
        val finalCase = store.allCaseStates().maxByOrNull { it.risk }
        val failures = outcomes.flatMap { it.failures }.toMutableList()
        if (scenario.benign && warnings.isNotEmpty()) failures += "benign scenario produced a full-screen warning"
        if (!scenario.benign && finalCase?.topFamily != scenario.family) failures += "expected family ${scenario.family}, got ${finalCase?.topFamily}"
        val before = firstDetectionIndex != null && (firstPaymentIndex == null || firstDetectionIndex!! <= firstPaymentIndex!!)
        return ScenarioReport(
            id = scenario.id,
            family = scenario.family,
            steps = outcomes,
            stageAtFirstDetection = firstDetectionStage,
            detectedBeforePaymentRequest = before,
            warningsAt = warnings,
            noticeCount = outcomes.count { it.notice },
            finalFamily = finalCase?.topFamily,
            finalStage = finalCase?.stage,
            failures = failures,
        )
    }

    fun runAll(scenarios: List<Scenario>): SuiteReport {
        val reports = scenarios.map { run(it) }
        val scams = reports.filter { it.family != null }
        val benign = reports.filter { it.family == null }
        return SuiteReport(
            scams = scams.size,
            scamsDetectedBeforePayment = scams.count { it.detectedBeforePaymentRequest },
            scamsCorrectFamily = scams.count { it.finalFamily == it.family },
            benign = benign.size,
            benignWithNotice = benign.count { it.noticeCount > 0 },
            benignWithWarning = benign.count { it.warningsAt.isNotEmpty() },
            maxMomentMs = reports.flatMap { r -> r.steps.filter { it.warning != null }.map { it.elapsedMs } }.maxOrNull() ?: 0,
            reports = reports,
        )
    }

    companion object {
        /** 1 Oct 2026, 10:00 IST: a fixed start so replays are deterministic. */
        const val DEFAULT_START = 1_790_830_800_000L

        /** Scenario files bundled under `/scenarios/` in resources. */
        fun bundledScenarios(): List<Scenario> {
            val index = ReplayHarness::class.java.getResourceAsStream("/scenarios/index.txt")
                ?.bufferedReader()?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }
                ?: return emptyList()
            return index.map { name ->
                val text = ReplayHarness::class.java.getResourceAsStream("/scenarios/$name")!!.bufferedReader(Charsets.UTF_8).readText()
                Scenario.parse(text)
            }
        }
    }
}

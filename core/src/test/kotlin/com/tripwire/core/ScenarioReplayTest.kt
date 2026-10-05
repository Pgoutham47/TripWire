package com.tripwire.core

import com.tripwire.core.replay.ReplayHarness
import com.tripwire.core.replay.ScenarioReport
import com.tripwire.core.script.ScriptPack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays every bundled scenario through the real pipeline with keyword tagging only, the path
 * that must work before the model does (PRD 18, build order step 3). Checks the PRD 16.3 criteria.
 */
class ScenarioReplayTest {
    private val pack = ScriptPack.bundled()
    private val suite = ReplayHarness(pack).runAll(ReplayHarness.bundledScenarios())

    @Test
    fun `every scenario meets its expectations`() {
        suite.reports.forEach { print(describe(it)) }
        println(
            "\nSUITE: scams=${suite.scams} earlyDetection=${suite.scamsDetectedBeforePayment}/${suite.scams} " +
                "correctFamily=${suite.scamsCorrectFamily}/${suite.scams} benign=${suite.benign} " +
                "benignNotices=${suite.benignWithNotice} benignWarnings=${suite.benignWithWarning} maxMomentMs=${suite.maxMomentMs}",
        )
        val failed = suite.reports.filterNot { it.passed }
        if (failed.isNotEmpty()) fail(failed.joinToString("\n") { "${it.id}: ${it.failures.joinToString("; ")}" })
    }

    @Test
    fun `scams are detected before the payment request in at least 80 percent`() {
        assertTrue(suite.earlyDetectionRate >= 0.8, "early detection ${suite.earlyDetectionRate}")
    }

    @Test
    fun `no benign conversation gets a full-screen warning`() {
        assertEquals(0, suite.benignWithWarning)
    }

    @Test
    fun `fewer than 2 percent of benign conversations get a quiet notice`() {
        assertTrue(suite.benignNoticeRate < 0.02, "benign notice rate ${suite.benignNoticeRate}")
    }

    @Test
    fun `moment decisions stay well inside the 300 ms budget`() {
        assertTrue(suite.maxMomentMs < 300, "slowest moment ${suite.maxMomentMs} ms")
    }

    private fun describe(r: ScenarioReport) = buildString {
        appendLine("\n== ${r.id} (${r.family ?: "benign"}) ${if (r.passed) "PASS" else "FAIL"}")
        r.steps.forEach { s ->
            append("  ${s.index} ${s.type.wire.padEnd(18)} stage=${s.stage?.name ?: "-"} risk=${s.risk ?: "-"} fam=${s.family ?: "-"}")
            if (s.tags.isNotEmpty()) append(" tags=${s.tags.joinToString(",") { it.wire }}")
            if (s.notice) append(" NOTICE")
            s.warning?.let { append(" WARN[$it${s.hardRule?.let { h -> " rule=$h" } ?: ""}]") }
            appendLine()
            s.failures.forEach { appendLine("     ! $it") }
        }
        appendLine("  first detection at ${r.stageAtFirstDetection}, before payment=${r.detectedBeforePaymentRequest}, final ${r.finalFamily}/${r.finalStage}")
    }
}

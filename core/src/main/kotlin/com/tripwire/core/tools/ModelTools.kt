package com.tripwire.core.tools

import com.tripwire.core.model.Tactic
import com.tripwire.core.script.ScriptPack
import com.tripwire.core.tactic.KeywordTagger
import com.tripwire.core.tactic.TacticInput
import com.tripwire.core.tactic.TacticOutputParser
import com.tripwire.core.tactic.TacticPrompt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** One labeled message from tools/model/data/build/<split>.jsonl. */
@Serializable
data class LabeledMessage(
    val id: String,
    val split: String,
    val group: String,
    val kind: String,
    val family: String,
    val lang: String,
    val app: String,
    val is_group: Boolean,
    val context: List<String>,
    val text: String,
    val tags: List<String>,
    val aug: String? = null,
)

/** A model's raw answer for one message, as written by tools/model/predict.py. */
@Serializable
data class RawPrediction(val id: String, val raw: String, val ms: Double? = null)

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
private val appLabels = mapOf(
    "com.whatsapp" to "WhatsApp", "org.telegram.messenger" to "Telegram", "com.google.android.apps.messaging" to "Messages",
)

fun readLabeled(file: File): List<LabeledMessage> =
    file.readLines().filter { it.isNotBlank() }.map { json.decodeFromString(LabeledMessage.serializer(), it) }

fun LabeledMessage.toInput() = TacticInput(
    message = text, context = context, appName = appLabels[app] ?: app, isGroup = is_group,
)

/** The exact answer the model must learn: the same JSON format the app validates (PRD Appendix B). */
fun LabeledMessage.targetJson(): String = buildJsonObject {
    put("language", lang)
    put("tags", buildJsonArray {
        // Sorted, confidence fixed: the model learns which tags, the app reads confidences as-is.
        tags.sorted().forEach { t -> add(buildJsonObject { put("tag", t); put("confidence", 0.9) }) }
    })
}.toString()

/**
 * Writes chat-format training data from labeled messages, using the app's own prompt builder,
 * so training and inference see the same text (PRD 10.6).
 *
 *     ExportTraining <in.jsonl> <out.jsonl>
 */
object ExportTraining {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "usage: ExportTraining <in.jsonl> <out.jsonl>" }
        val rows = readLabeled(File(args[0]))
        File(args[1]).bufferedWriter().use { out ->
            for (r in rows) {
                val line = buildJsonObject {
                    put("id", r.id)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "system"); put("content", TacticPrompt.systemInstruction) })
                        add(buildJsonObject { put("role", "user"); put("content", TacticPrompt.user(r.toInput())) })
                        add(buildJsonObject { put("role", "assistant"); put("content", r.targetJson()) })
                    })
                }
                out.write(line.toString())
                out.newLine()
            }
        }
        // The constrained-decoding schema the app uses, for evaluating converted models the same way.
        File(File(args[1]).parentFile, "tactic_schema.json").writeText(com.tripwire.core.tactic.TacticSchema.schema)
        println("wrote ${rows.size} examples to ${args[1]}")
    }
}

/**
 * Scores a tagger on a labeled split (PRD 10.7): per-tag precision, recall and F1, macro F1, the
 * rate of benign messages given any high-risk tag, format validity, and results per language.
 *
 *     TacticEval <test.jsonl> rules <report.json>
 *     TacticEval <test.jsonl> <predictions.jsonl> <report.json>
 */
object TacticEval {
    val HIGH_RISK = setOf(
        // authority_claim is not high-risk on its own: genuine banks and brokers make the same claim.
        "guaranteed_returns", "legal_threat", "secrecy",
        "remote_access_request", "credential_request", "fee_to_withdraw",
    )
    private const val THRESHOLD = 0.5
    private val idOf = HashMap<TacticInput, String>()

    @Serializable
    data class TagScore(val tag: String, val support: Int, val precision: Double, val recall: Double, val f1: Double)

    @Serializable
    data class Report(
        val source: String,
        val n: Int,
        val macroF1: Double,
        val microF1: Double,
        val exactMatch: Double,
        val validFormat: Double,
        val benignHighRiskRate: Double,
        val benignAnyTagRate: Double,
        val injectionRecall: Double,
        val perTag: List<TagScore>,
        val macroF1ByLang: Map<String, Double>,
        val medianMs: Double?,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: TacticEval <data.jsonl> <rules|predictions.jsonl> <report.json>" }
        val all = readLabeled(File(args[0]))
        // A predictions file may cover a subset (a quick run with --limit); score only those rows.
        val predIds = if (args[1] == "rules") null else File(args[1]).readLines().filter { it.isNotBlank() }
            .map { json.decodeFromString(RawPrediction.serializer(), it).id }.toSet()
        val data = if (predIds == null) all else all.filter { it.id in predIds }
        val (predicted, valid, ms) = if (args[1] == "rules") {
            val tagger = KeywordTagger(ScriptPack.bundled().keywordRules)
            Triple(data.associate { it.id to tagger.read(it.toInput()).tags.filter { t -> t.confidence >= THRESHOLD }.map { t -> t.tactic.wire }.toSet() }, data.size, null)
        } else {
            val preds = File(args[1]).readLines().filter { it.isNotBlank() }
                .map { json.decodeFromString(RawPrediction.serializer(), it) }.associateBy { it.id }
            var ok = 0
            val out = data.associate { r ->
                val parsed = preds[r.id]?.raw?.let { TacticOutputParser.parse(it) }
                if (parsed != null) ok++
                r.id to parsed?.tags.orEmpty().filter { it.confidence >= THRESHOLD }.map { it.tactic.wire }.toSet()
            }
            val times = preds.values.mapNotNull { it.ms }.sorted()
            Triple(out, ok, times.getOrNull(times.size / 2))
        }
        val report = score(args[1], data, predicted, valid, ms)
        File(args[2]).writeText(Json { prettyPrint = true }.encodeToString(Report.serializer(), report))
        print(render(report))

        // As deployed: the app merges the model's tags with the always-on keyword rules, and falls
        // back to all keyword rules when the model's answer is invalid (ResilientTacticReader).
        if (args[1] != "rules") {
            val rules = KeywordTagger(ScriptPack.bundled().keywordRules)
            val preds = File(args[1]).readLines().filter { it.isNotBlank() }
                .map { json.decodeFromString(RawPrediction.serializer(), it) }.associateBy { it.id }
            val reader = com.tripwire.core.tactic.ResilientTacticReader(
                com.tripwire.core.tactic.TacticReader { input -> preds[idOf.getValue(input)]?.raw?.let { TacticOutputParser.parse(it) } },
                rules,
            )
            val deployed = data.associate { r ->
                val input = r.toInput()
                idOf[input] = r.id
                r.id to reader.read(input).tags.filter { it.confidence >= THRESHOLD }.map { it.tactic.wire }.toSet()
            }
            val dep = score("${args[1]} (as deployed, with keyword rules)", data, deployed, data.size, ms)
            File(args[2].removeSuffix(".json") + "-deployed.json").writeText(Json { prettyPrint = true }.encodeToString(Report.serializer(), dep))
            println()
            print(render(dep))
        }
    }

    fun score(source: String, data: List<LabeledMessage>, predicted: Map<String, Set<String>>, valid: Int, ms: Double?): Report {
        fun f1(p: Double, r: Double) = if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        fun perTag(rows: List<LabeledMessage>): List<TagScore> = Tactic.entries.map { t ->
            var tp = 0; var fp = 0; var fn = 0
            for (r in rows) {
                val gold = t.wire in r.tags
                val pred = t.wire in predicted.getValue(r.id)
                if (gold && pred) tp++ else if (pred) fp++ else if (gold) fn++
            }
            val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val rc = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
            TagScore(t.wire, tp + fn, p, rc, f1(p, rc))
        }
        fun macro(scores: List<TagScore>) = scores.filter { it.support > 0 }.map { it.f1 }.average()

        val tags = perTag(data)
        val tp = data.sumOf { r -> (r.tags.toSet() intersect predicted.getValue(r.id)).size }
        val nPred = data.sumOf { predicted.getValue(it.id).size }
        val nGold = data.sumOf { it.tags.size }
        val microP = if (nPred == 0) 0.0 else tp.toDouble() / nPred
        val microR = if (nGold == 0) 0.0 else tp.toDouble() / nGold
        val benign = data.filter { it.kind == "benign" }
        val injected = data.filter { it.aug == "injection" && it.tags.isNotEmpty() }
        return Report(
            source = source,
            n = data.size,
            macroF1 = macro(tags),
            microF1 = f1(microP, microR),
            exactMatch = data.count { predicted.getValue(it.id) == it.tags.toSet() }.toDouble() / data.size,
            validFormat = valid.toDouble() / data.size,
            benignHighRiskRate = benign.count { (predicted.getValue(it.id) intersect HIGH_RISK).isNotEmpty() }.toDouble() / benign.size.coerceAtLeast(1),
            benignAnyTagRate = benign.count { (predicted.getValue(it.id) - it.tags.toSet()).isNotEmpty() }.toDouble() / benign.size.coerceAtLeast(1),
            injectionRecall = if (injected.isEmpty()) 1.0 else injected.count { it.tags.toSet().all { t -> t in predicted.getValue(it.id) } }.toDouble() / injected.size,
            perTag = tags,
            macroF1ByLang = data.groupBy { it.lang }.mapValues { (_, rows) -> macro(perTag(rows)) },
            medianMs = ms,
        )
    }

    fun render(r: Report): String = buildString {
        fun pct(d: Double) = "%.1f%%".format(d * 100)
        fun f(d: Double) = "%.3f".format(d)
        appendLine("source: ${r.source}   n=${r.n}")
        appendLine("macro F1 ${f(r.macroF1)}   micro F1 ${f(r.microF1)}   exact match ${pct(r.exactMatch)}   valid format ${pct(r.validFormat)}")
        appendLine("benign with a high-risk tag ${pct(r.benignHighRiskRate)}   benign with any wrong tag ${pct(r.benignAnyTagRate)}   injected scams fully tagged ${pct(r.injectionRecall)}")
        appendLine("macro F1 by language: " + r.macroF1ByLang.entries.joinToString("  ") { "${it.key} ${f(it.value)}" })
        r.medianMs?.let { appendLine("median latency ${"%.0f".format(it)} ms") }
        appendLine("tag                      support  precision  recall   F1")
        r.perTag.forEach { appendLine("%-24s %7d  %9s  %6s  %5s".format(it.tag, it.support, f(it.precision), f(it.recall), f(it.f1))) }
    }
}

/** Stable id for a tactic-reader input, so prompts and model answers can be matched across tools. */
fun TacticInput.key(): String {
    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.update(listOf(message, context.joinToString("\u0001"), appName, isGroup.toString()).joinToString("\u0002").toByteArray())
    return md.digest().take(8).joinToString("") { "%02x".format(it) }
}

/**
 * Product-level evaluation of a model: replays every scenario with the model as the tactic reader
 * and checks the PRD 16.3 criteria (detection before payment, no warnings on benign chats).
 *
 *     ScenarioModelEval prompts <out.chat.jsonl>          records every prompt the pipeline sends
 *     ScenarioModelEval replay <predictions.jsonl>        replays with those answers (as deployed)
 */
object ScenarioModelEval {
    @JvmStatic
    fun main(args: Array<String>) {
        val pack = ScriptPack.bundled()
        val scenarios = com.tripwire.core.replay.ReplayHarness.bundledScenarios()
        when (args.getOrNull(0)) {
            "prompts" -> {
                val seen = LinkedHashMap<String, TacticInput>()
                val recorder = com.tripwire.core.tactic.TacticReader { input -> seen[input.key()] = input; null }
                com.tripwire.core.replay.ReplayHarness(pack, recorder).runAll(scenarios)
                File(args[1]).bufferedWriter().use { out ->
                    for ((id, input) in seen) {
                        val line = buildJsonObject {
                            put("id", id)
                            put("messages", buildJsonArray {
                                add(buildJsonObject { put("role", "system"); put("content", TacticPrompt.systemInstruction) })
                                add(buildJsonObject { put("role", "user"); put("content", TacticPrompt.user(input)) })
                                add(buildJsonObject { put("role", "assistant"); put("content", "") })
                            })
                        }
                        out.write(line.toString()); out.newLine()
                    }
                }
                println("wrote ${seen.size} scenario prompts to ${args[1]}")
            }
            "replay" -> {
                val preds = File(args[1]).readLines().filter { it.isNotBlank() }
                    .map { json.decodeFromString(RawPrediction.serializer(), it) }.associateBy { it.id }
                val model = com.tripwire.core.tactic.TacticReader { input -> preds[input.key()]?.raw?.let { TacticOutputParser.parse(it) } }
                val suite = com.tripwire.core.replay.ReplayHarness(pack, model).runAll(scenarios)
                for (r in suite.reports) {
                    println("${if (r.passed) "PASS" else "FAIL"} ${r.id.padEnd(26)} first detection ${r.stageAtFirstDetection ?: "-"}, warnings ${r.warningsAt}, notices ${r.noticeCount}")
                    r.failures.forEach { println("     ! $it") }
                }
                println("SUITE with model: scams=${suite.scams} earlyDetection=${suite.scamsDetectedBeforePayment}/${suite.scams} " +
                    "correctFamily=${suite.scamsCorrectFamily}/${suite.scams} benign=${suite.benign} benignNotices=${suite.benignWithNotice} benignWarnings=${suite.benignWithWarning}")
            }
            else -> error("usage: ScenarioModelEval prompts <out> | replay <predictions>")
        }
    }
}

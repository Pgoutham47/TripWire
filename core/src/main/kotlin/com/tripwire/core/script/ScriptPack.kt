package com.tripwire.core.script

import com.tripwire.core.model.Stage
import com.tripwire.core.model.Tactic
import com.tripwire.core.model.TripwireMoment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A script pack: everything about scams that can change without an app update (PRD 9.6, 14.7).
 * Families, thresholds, keyword rules, app lists, known-bad lists and warning templates all live here.
 *
 * Signal names used throughout the pack:
 * - `tag:<tactic>`         a tactic tag from the reader, weighted by its confidence
 * - `event:<type>`         an event type from [com.tripwire.core.model.EventType]
 * - `entity:<kind>`        an extracted entity: upi_handle, apk_link, url, amount, phone, telegram
 * - `check:<id>:fail|pass` a grounded-check result
 * - `ctx:<name>`           a derived context signal computed by the engine (see [com.tripwire.core.engine.Signals])
 */
@Serializable
data class ScriptPack(
    val version: Int,
    val createdAt: String,
    val thresholds: Thresholds = Thresholds(),
    val families: List<FamilyDef>,
    val apps: AppLists,
    val keywordRules: List<KeywordRule>,
    val knownBad: KnownBad = KnownBad(),
    val brands: List<BrandDef> = emptyList(),
    val validHandlePattern: String,
    val sebiCheckUrl: String,
    /** Reason sentences keyed by signal, per language. Placeholders in braces, see [com.tripwire.core.explain.ExplanationBuilder]. */
    val reasons: Map<String, Map<String, String>>,
    /**
     * Signals that tell the same story; only the first one present becomes a reason, so a warning
     * never spends two of its three reasons on one fact (EXP-03).
     */
    val reasonGroups: List<List<String>> = emptyList(),
    /** Signals that open the story ("A stranger added you to a group 6 days ago"); one leads when present. */
    val storyOpeners: List<String> = emptyList(),
    /** Fixed UI and warning strings keyed by id, per language. */
    val strings: Map<String, Map<String, String>>,
) {
    fun family(id: String): FamilyDef? = families.firstOrNull { it.id == id }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        fun parse(text: String): ScriptPack = json.decodeFromString(serializer(), text)

        /** The pack bundled with the app, used until a signed update replaces it. */
        fun bundled(): ScriptPack {
            val text = ScriptPack::class.java.getResourceAsStream("/script_pack.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("bundled script_pack.json missing from resources")
            return parse(text)
        }
    }
}

/** Watch: a case opens. Warn: a full-screen warning is allowed (ENG-05). */
@Serializable
data class Thresholds(
    val watch: Int = 40,
    val warn: Int = 60,
    /** Minutes within which a tripwire moment is time-linked to an active case (ENG-07). */
    val timeLinkMinutes: Int = 30,
    /** Days for risk to halve when no new events arrive (ENG-09). */
    val riskHalfLifeDays: Double = 14.0,
    /** Limits on per-user threshold tuning (ENG-10). */
    val tuneMin: Int = -10,
    val tuneMax: Int = 15,
)

@Serializable
data class FamilyDef(
    val id: String,
    /** Prior log-odds of this family against benign for a fresh stranger. */
    val priorLogit: Double,
    val names: Map<String, String>,
    val descriptions: Map<String, String>,
    /** Weight of each signal on this family's log-odds. Negative weights point towards benign. */
    val signals: Map<String, Double>,
    val stages: List<StageDef>,
    val hardRules: List<HardRule> = emptyList(),
    /** Grounded checks that apply to this family, by check id. */
    val checks: List<String> = emptyList(),
    /** Warning headline and closing line per tripwire moment, per language. */
    val templates: Map<TripwireMoment, MomentTemplate> = emptyMap(),
)

/**
 * A stage is reached when the weighted strength of its evidence signals reaches [minEvidence].
 * Stages can be skipped (a scammer may open with an install link) but never move backward.
 */
@Serializable
data class StageDef(
    val stage: Stage,
    val evidence: List<String>,
    val minEvidence: Double,
)

/**
 * A rule that allows a full-screen warning whatever the score (ENG-06).
 * It fires at [moment] when the case already shows at least [minFamilyLogit] for the family,
 * the case has every signal in [requireAll] and at least one in [requireAny] (if given),
 * and every check in [failedChecks] failed.
 */
@Serializable
data class HardRule(
    val id: String,
    val moment: TripwireMoment,
    val minFamilyLogit: Double = Double.NEGATIVE_INFINITY,
    val requireAll: List<String> = emptyList(),
    val requireAny: List<String> = emptyList(),
    val failedChecks: List<String> = emptyList(),
)

@Serializable
data class MomentTemplate(
    val headline: Map<String, String>,
    val closing: Map<String, String> = emptyMap(),
)

@Serializable
data class AppLists(
    val messaging: List<AppDef>,
    val payment: List<AppDef>,
    val remoteAccess: List<AppDef>,
    /** Installer packages that count as an app store (CHK-03). */
    val stores: List<String>,
    /** Installer packages that mean "came from a browser or chat", i.e. a link. */
    val linkInstallers: List<String>,
)

@Serializable
data class AppDef(val packageName: String, val label: String)

/** Fixed keyword rules: the fallback tagger and a parallel check for the riskiest tactics (TAC-04, 10.8). */
@Serializable
data class KeywordRule(
    val tactic: Tactic,
    /** Case-insensitive regular expressions. Any match raises the tag. */
    val patterns: List<String>,
    val confidence: Double = 0.7,
    /** True for rules that also run alongside the model, so either can raise the tag. */
    val alwaysOn: Boolean = false,
)

@Serializable
data class KnownBad(
    val handles: List<String> = emptyList(),
    val numbers: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

/** A real broker or bank that scammers imitate (CHK-07). */
@Serializable
data class BrandDef(
    val name: String,
    val aliases: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
)

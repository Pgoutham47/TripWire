package com.tripwire.core.explain

import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.checks.CheckResult
import com.tripwire.core.engine.CaseState
import com.tripwire.core.engine.ProgressionEngine
import com.tripwire.core.model.Event
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Stage
import com.tripwire.core.model.Tactic
import com.tripwire.core.model.TacticTag
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.script.ScriptPack
import kotlinx.serialization.Serializable

@Serializable
data class TimelineItem(
    val time: Long,
    val app: String,
    val label: String,
)

@Serializable
data class CheckLine(val text: String, val outcome: CheckOutcome)

/** Everything the full-screen warning shows, top to bottom (PRD 13.2), already in the user's language. */
@Serializable
data class WarningContent(
    val moment: TripwireMoment,
    val familyId: String?,
    val familyName: String,
    val language: String,
    val headline: String,
    val reasons: List<String>,
    val timeline: List<TimelineItem>,
    val check: CheckLine?,
    val closing: String,
    val primaryLabel: String,
    val allyLabel: String?,
    val verifyLabel: String?,
    val proceedLabel: String,
    /** The text read aloud: headline, reasons, check, closing (EXP-02). */
    val spoken: String,
)

/**
 * Builds warnings and notices from fixed templates filled with case evidence (EXP-01, EXP-03).
 * It never claims certainty (INT-07), uses at most three reasons, and every sentence ties to
 * something that happened. Wording rules are in PRD 13.3.
 */
class ExplanationBuilder(private val pack: ScriptPack) {

    fun string(key: String, lang: String, params: Map<String, String> = emptyMap()): String {
        val table = pack.strings[key] ?: return key
        val template = table[lang] ?: table[baseLang(lang)] ?: table["en"] ?: return key
        return fill(template, params)
    }

    fun familyName(familyId: String?, lang: String): String {
        val f = familyId?.let { pack.family(it) } ?: return string("family.unknown", lang)
        return f.names[lang] ?: f.names[baseLang(lang)] ?: f.names["en"] ?: f.id
    }

    fun tacticWords(tactic: Tactic, lang: String): String = string("tactic.${tactic.wire}", lang)

    fun checkText(check: CheckResult, lang: String): String = string(check.messageKey, lang, check.params)

    /** Journey 3: one quiet line for the early notice. */
    fun noticeTitle(lang: String) = string("notice.title", lang)
    fun noticeBody(state: CaseState, lang: String) =
        string("notice.body", lang, mapOf("family" to familyName(state.topFamily, lang)))

    fun warning(
        moment: TripwireMoment,
        state: CaseState,
        events: List<Event>,
        tagsByEvent: Map<Long, List<TacticTag>>,
        check: CheckResult?,
        allyName: String?,
        lang: String,
        now: Long,
    ): WarningContent {
        val familyId = state.topFamily
        val family = familyId?.let { pack.family(it) }
        val template = family?.templates?.get(moment)
        val familyParams = mapOf("family" to familyName(familyId, lang))
        val headline = template?.headline?.let { it[lang] ?: it[baseLang(lang)] ?: it["en"] }
            ?: string("headline.generic.${moment.wire}", lang, familyParams)
        val closing = template?.closing?.let { it[lang] ?: it[baseLang(lang)] ?: it["en"] }
            ?: string("closing.generic.${moment.wire}", lang, familyParams)

        val reasons = reasons(state, events, check, lang, now)
        val timeline = timeline(events, tagsByEvent, lang)
        val checkLine = check?.takeIf { it.outcome != CheckOutcome.UNKNOWN || it.checkId == "install_source" }
            ?.let { CheckLine(checkText(it, lang), it.outcome) }

        val primary = when (moment) {
            TripwireMoment.PAYMENT -> string("btn.dont_pay", lang)
            TripwireMoment.INSTALL -> string("btn.dont_install", lang)
            TripwireMoment.SCREEN_SHARE -> string("btn.stop_sharing", lang)
            TripwireMoment.CALL -> string("btn.hang_up", lang)
        }
        val proceed = when (moment) {
            TripwireMoment.PAYMENT -> string("btn.pay_anyway", lang)
            TripwireMoment.INSTALL -> string("btn.install_anyway", lang)
            TripwireMoment.SCREEN_SHARE -> string("btn.continue_anyway", lang)
            TripwireMoment.CALL -> string("btn.dismiss", lang)
        }
        val ally = allyName?.let { string("btn.call_ally", lang, mapOf("ally" to it)) }
        val verify = if (moment == TripwireMoment.PAYMENT && check?.checkId == "valid_handle") string("btn.verify_sebi", lang) else null

        val spoken = buildList {
            add(headline)
            addAll(reasons)
            checkLine?.let { add(it.text) }
            add(closing)
        }.joinToString(" ")

        return WarningContent(
            moment = moment,
            familyId = familyId,
            familyName = familyName(familyId, lang),
            language = lang,
            headline = headline,
            reasons = reasons,
            timeline = timeline,
            check = checkLine,
            closing = closing,
            primaryLabel = primary,
            allyLabel = ally,
            verifyLabel = verify,
            proceedLabel = proceed,
            spoken = spoken,
        )
    }

    /**
     * Up to three reasons, chosen by how much each signal moved the leading family, then told in
     * script order so they read as a story. The grounded check has its own box, so it is not repeated.
     */
    fun reasons(state: CaseState, events: List<Event>, check: CheckResult?, lang: String, now: Long): List<String> {
        val family = state.topFamily?.let { pack.family(it) } ?: return emptyList()
        val shownCheckSignal = check?.signal
        val stageOrder: (String) -> Int = { sig ->
            family.stages.firstOrNull { sig in it.evidence }?.stage?.number ?: Stage.CONTACT.number
        }
        val present = state.signals
            .filter { (sig, strength) -> strength > 0.0 && sig != shownCheckSignal && pack.reasons.containsKey(sig) }
            .mapNotNull { (sig, strength) ->
                val w = family.signals[sig] ?: return@mapNotNull null
                if (w <= 0.0) null else Triple(sig, w * strength, stageOrder(sig))
            }
            .associateBy { it.first }

        // Collapse each redundancy group to its first member present.
        val suppressed = HashSet<String>()
        for (group in pack.reasonGroups) {
            val keep = group.firstOrNull { it in present } ?: continue
            group.filter { it != keep }.forEach { suppressed += it }
        }
        val pool = present.values.filter { it.first !in suppressed }
        val opener = pack.storyOpeners.firstNotNullOfOrNull { o -> pool.firstOrNull { it.first == o } }
        val rest = pool.filter { it != opener }.sortedByDescending { it.second }
            .take(MAX_REASONS - if (opener != null) 1 else 0)
        val candidates = (listOfNotNull(opener) + rest).sortedBy { it.third }

        val params = storyParams(events, lang, now)
        return candidates.map { (sig, _, _) ->
            val table = pack.reasons.getValue(sig)
            fill(table[lang] ?: table[baseLang(lang)] ?: table.getValue("en"), params)
        }
    }

    /**
     * Key events only, oldest first, at most five (PRD 13.2 item 3). Every system event (group add,
     * call, install, payment, screen share) is kept; messages fill the remaining places, one per tactic.
     */
    fun timeline(events: List<Event>, tagsByEvent: Map<Long, List<TacticTag>>, lang: String): List<TimelineItem> {
        data class Item(val item: TimelineItem, val system: Boolean)
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        for (e in events.sortedBy { it.timestamp }) {
            val (label, system) = when (e.type) {
                EventType.MESSAGE, EventType.USER_REPLY -> {
                    val top = tagsByEvent[e.id].orEmpty().maxByOrNull { it.confidence }
                    (if (top == null || top.confidence < 0.5) null else tacticWords(top.tactic, lang)) to false
                }
                EventType.GROUP_ADDED -> string("timeline.group_added", lang, mapOf("group" to (e.groupName ?: ""))) to true
                EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED ->
                    string(if (e.type == EventType.APP_INSTALLED) "timeline.app_installed" else "timeline.install_screen", lang, mapOf("app" to (e.installedLabel ?: e.installedPackage ?: ""))) to true
                EventType.UPI_LINK_OPENED, EventType.PAYMENT_APP_OPENED -> {
                    // A payment app opening has no payee yet; the row already names the app.
                    val handle = e.upi?.payeeHandle?.takeIf { it.isNotBlank() }
                    (if (handle != null) string("timeline.payment", lang, mapOf("handle" to handle)) else string("timeline.payment_started", lang)) to true
                }
                EventType.CALL_STARTED -> string(if (e.isVideoCall) "timeline.video_call" else "timeline.call", lang) to true
                EventType.SCREEN_SHARE_STARTED, EventType.REMOTE_APP_OPENED -> string("timeline.screen_share", lang) to true
                EventType.PAYMENT_SMS -> string("timeline.paid", lang) to true
                EventType.COLLECT_REQUEST -> string("timeline.collect_request", lang) to true
                EventType.CALL_ENDED -> null to true
            }
            if (label != null && seen.add(label)) items += Item(TimelineItem(e.timestamp, e.app, label), system)
        }
        if (items.size <= MAX_TIMELINE) return items.map { it.item }
        val system = items.filter { it.system }.takeLast(MAX_TIMELINE)
        val messages = items.filter { !it.system }.take((MAX_TIMELINE - system.size).coerceAtLeast(0))
        val keep = (system + messages).toSet()
        return items.filter { it in keep }.map { it.item }
    }

    /** Placeholders shared by reason templates: {when}, {group}, {app}, {sender}, {handle}. */
    private fun storyParams(events: List<Event>, lang: String, now: Long): Map<String, String> {
        val first = events.minByOrNull { it.timestamp }
        val days = first?.let { ((now - it.timestamp) / ProgressionEngine.DAY_MS).toInt() } ?: 0
        val whenText = when (days) {
            0 -> string("time.today", lang)
            1 -> string("time.yesterday", lang)
            else -> string("time.days_ago", lang, mapOf("n" to days.toString()))
        }
        return mapOf(
            "when" to whenText,
            "group" to (events.firstNotNullOfOrNull { it.groupName } ?: ""),
            "app" to (events.firstNotNullOfOrNull { it.installedLabel } ?: events.flatMap { it.entities.appNames }.firstOrNull() ?: ""),
            "sender" to (events.firstNotNullOfOrNull { it.senderName } ?: ""),
            "handle" to (events.firstNotNullOfOrNull { it.upi?.payeeHandle } ?: events.flatMap { it.entities.upiHandles }.firstOrNull() ?: ""),
        )
    }

    companion object {
        const val MAX_REASONS = 3
        const val MAX_TIMELINE = 5

        fun baseLang(lang: String) = lang.substringBefore('-')

        fun fill(template: String, params: Map<String, String>): String {
            var out = template
            for ((k, v) in params) out = out.replace("{$k}", v)
            return out.replace(Regex("\\s{2,}"), " ").trim()
        }
    }
}

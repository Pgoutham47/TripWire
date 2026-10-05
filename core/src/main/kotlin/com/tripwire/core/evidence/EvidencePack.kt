package com.tripwire.core.evidence

import com.tripwire.core.explain.ExplanationBuilder
import com.tripwire.core.model.Amount
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Stage
import com.tripwire.core.pipeline.TripwirePipeline
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Typed by the user (Appendix E item 1). */
@Serializable
data class Complainant(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
)

/** Amount and time typed or confirmed by the user, pre-filled from payment SMS where possible (EVD-05). */
@Serializable
data class TransactionDetails(
    val amount: Amount?,
    val time: Long,
    val utr: String? = null,
    val payeeHandle: String? = null,
    val payeeName: String? = null,
    val bank: String? = null,
)

@Serializable
data class PackTimelineRow(val time: Long, val app: String, val description: String)

@Serializable
data class QuotedMessage(val time: Long, val app: String, val sender: String?, val text: String)

@Serializable
data class InstalledAppDetails(val name: String, val packageName: String, val installSource: String)

/**
 * Everything needed to file a cyber-fraud complaint (EVD-01, Appendix E). Built offline (EVD-04).
 * The Android app renders it to PDF; the fields follow the national portal's order (EVD-08).
 */
@Serializable
data class EvidencePack(
    val caseId: String,
    val createdAt: Long,
    val complainant: Complainant,
    val scamType: String,
    val stage: String,
    val firstContact: Long?,
    val totalAmount: Amount?,
    val timeline: List<PackTimelineRow>,
    val phoneNumbers: List<String>,
    val usernames: List<String>,
    val groupNames: List<String>,
    val upiHandles: List<String>,
    val links: List<String>,
    val apps: List<InstalledAppDetails>,
    val transactions: List<TransactionDetails>,
    val quotedMessages: List<QuotedMessage>,
    val failedChecks: List<String>,
) {
    /** Golden-hour deadline: one hour after the earliest payment (EVD-03). */
    val goldenHourEndsAt: Long? get() = transactions.minOfOrNull { it.time }?.plus(TripwirePipeline.HOUR_MS)
}

class EvidencePackBuilder(private val pipeline: TripwirePipeline) {
    private val store get() = pipeline.store
    private val explain get() = pipeline.explain

    fun build(
        caseId: String,
        complainant: Complainant,
        transactions: List<TransactionDetails>,
        lang: String,
        now: Long,
        appLabel: (String) -> String = { it },
    ): EvidencePack {
        val root = pipeline.rootOf(caseId)
        val members = pipeline.membersOf(root)
        val cps = members.mapNotNull { store.counterparty(it) }
        val events = store.eventsFor(members)
        val state = store.caseState(root)

        val timeline = events.mapNotNull { e ->
            val desc = when (e.type) {
                EventType.MESSAGE -> e.text?.let { "${e.senderName ?: ""}: ${it.take(140)}" } ?: explain.string("pack.text_deleted", lang)
                EventType.GROUP_ADDED -> explain.string("timeline.group_added", lang, mapOf("group" to (e.groupName ?: "")))
                EventType.CALL_STARTED -> explain.string(if (e.isVideoCall) "timeline.video_call" else "timeline.call", lang)
                EventType.CALL_ENDED -> null
                EventType.APP_INSTALLED, EventType.INSTALL_SCREEN_OPENED ->
                    explain.string("timeline.app_installed", lang, mapOf("app" to (e.installedLabel ?: e.installedPackage ?: "")))
                EventType.PAYMENT_APP_OPENED -> explain.string("pack.payment_app_opened", lang, mapOf("app" to appLabel(e.app)))
                EventType.UPI_LINK_OPENED -> explain.string("timeline.payment", lang, mapOf("handle" to (e.upi?.payeeHandle ?: "")))
                EventType.SCREEN_SHARE_STARTED, EventType.REMOTE_APP_OPENED -> explain.string("timeline.screen_share", lang)
                EventType.PAYMENT_SMS -> explain.string("timeline.paid", lang)
                EventType.USER_REPLY -> e.text?.let { explain.string("pack.user_reply", lang) + ": " + it.take(140) }
            } ?: return@mapNotNull null
            PackTimelineRow(e.timestamp, appLabel(e.app), desc)
        }

        val allEntities = events.map { it.entities }
        val phones = (cps.flatMap { c -> c.identifiers.filter { it.startsWith("tel:") }.map { it.removePrefix("tel:") } } +
            allEntities.flatMap { it.phoneNumbers }).distinct()
        val handles = (allEntities.flatMap { it.upiHandles } + events.mapNotNull { it.upi?.payeeHandle } +
            transactions.mapNotNull { it.payeeHandle }).filter { it.isNotBlank() }.distinct()
        val usernames = (allEntities.flatMap { it.telegramHandles }.map { "@$it" } +
            cps.filter { it.type == com.tripwire.core.model.CounterpartyType.PERSON }.map { it.displayName }).distinct()
        val groups = cps.filter { it.type == com.tripwire.core.model.CounterpartyType.GROUP }.map { it.displayName }
        val links = allEntities.flatMap { it.urls + it.apkLinks }.distinct()
        val apps = events.filter { it.type == EventType.APP_INSTALLED && it.installedPackage != null }.map {
            InstalledAppDetails(
                name = it.installedLabel ?: it.installedPackage!!,
                packageName = it.installedPackage!!,
                installSource = it.installerPackage?.let(appLabel) ?: explain.string("pack.unknown", lang),
            )
        }.distinctBy { it.packageName }
        val quoted = events.filter { it.type == EventType.MESSAGE && it.text != null }
            .map { QuotedMessage(it.timestamp, appLabel(it.app), it.senderName, it.text!!) }
        val failed = store.checks(root).map { it.first }
            .filter { it.outcome == com.tripwire.core.checks.CheckOutcome.FAIL }
            .distinctBy { it.checkId to it.subject }
            .map { explain.checkText(it, lang) }

        val total = transactions.mapNotNull { it.amount?.paise }.takeIf { it.isNotEmpty() }?.sum()?.let { Amount(it) }
        return EvidencePack(
            caseId = root,
            createdAt = now,
            complainant = complainant,
            scamType = explain.familyName(state?.topFamily, lang),
            stage = explain.string("stage.${(state?.stage ?: Stage.CONTACT).name.lowercase()}", lang),
            firstContact = events.minOfOrNull { it.timestamp },
            totalAmount = total,
            timeline = timeline,
            phoneNumbers = phones,
            usernames = usernames,
            groupNames = groups,
            upiHandles = handles,
            links = links,
            apps = apps,
            transactions = transactions,
            quotedMessages = quoted,
            failedChecks = failed,
        )
    }

    /** Payment details from the latest payment SMS linked to the case, for pre-filling (EVD-05). */
    fun latestPayment(caseId: String): TransactionDetails? {
        val members = pipeline.membersOf(pipeline.rootOf(caseId))
        val sms = store.eventsFor(members).lastOrNull { it.type == EventType.PAYMENT_SMS && it.upi != null } ?: return null
        val upi = sms.upi!!
        return TransactionDetails(upi.amount, sms.timestamp, upi.utr, upi.payeeHandle.ifBlank { null }, upi.payeeName)
    }

    /** EVD-06: a short script to read out on the 1930 call. */
    fun callScript(pack: EvidencePack, lang: String): String {
        val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault())
        val tx = pack.transactions.firstOrNull()
        return explain.string(
            "pack.call_script", lang,
            mapOf(
                "name" to pack.complainant.name.ifBlank { "—" },
                "type" to pack.scamType,
                "amount" to (pack.totalAmount?.format() ?: "—"),
                "time" to (tx?.let { fmt.format(Instant.ofEpochMilli(it.time)) } ?: "—"),
                "utr" to (tx?.utr ?: "—"),
                "handle" to (tx?.payeeHandle ?: pack.upiHandles.firstOrNull() ?: "—"),
                "numbers" to pack.phoneNumbers.joinToString(", ").ifBlank { "—" },
            ),
        )
    }
}

/** What an ally is told: the scam type, stage and time, never message content (ALY-03, privacy rule 3). */
@Serializable
data class AllyAlert(
    val kind: Kind,
    val familyName: String,
    val stageName: String,
    val time: Long,
) {
    @Serializable
    enum class Kind { WARNING_SHOWN, PROCEEDED, ALREADY_PAID, PROTECTION_OFF }

    fun text(explain: ExplanationBuilder, protectedName: String, lang: String): String = explain.string(
        "ally.${kind.name.lowercase()}", lang,
        mapOf("name" to protectedName, "family" to familyName, "stage" to stageName),
    )
}

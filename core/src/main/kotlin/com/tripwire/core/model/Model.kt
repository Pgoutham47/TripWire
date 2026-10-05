package com.tripwire.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The 14 persuasion tactics a message can carry (PRD Appendix A). [wire] is the JSON name. */
@Serializable
enum class Tactic(val wire: String) {
    @SerialName("guaranteed_returns") GUARANTEED_RETURNS("guaranteed_returns"),
    @SerialName("fake_social_proof") FAKE_SOCIAL_PROOF("fake_social_proof"),
    @SerialName("exclusivity") EXCLUSIVITY("exclusivity"),
    @SerialName("urgency") URGENCY("urgency"),
    /**
     * The sender identifies as, or speaks for, police, government, a court, a regulator, a bank, a
     * payment app, a broker, a courier or a telecom company. A claim, not a verdict: whether it is
     * genuine is decided by the engine and grounded checks, never by the tagger. Replaces
     * `authority_impersonation` (Appendix A), which asked text alone to judge genuineness.
     */
    @SerialName("authority_claim") AUTHORITY_CLAIM("authority_claim"),
    @SerialName("legal_threat") LEGAL_THREAT("legal_threat"),
    @SerialName("secrecy") SECRECY("secrecy"),
    @SerialName("channel_move") CHANNEL_MOVE("channel_move"),
    @SerialName("install_request") INSTALL_REQUEST("install_request"),
    @SerialName("remote_access_request") REMOTE_ACCESS_REQUEST("remote_access_request"),
    @SerialName("credential_request") CREDENTIAL_REQUEST("credential_request"),
    @SerialName("payment_request") PAYMENT_REQUEST("payment_request"),
    @SerialName("fee_to_withdraw") FEE_TO_WITHDRAW("fee_to_withdraw"),
    @SerialName("small_win_bait") SMALL_WIN_BAIT("small_win_bait");

    /** The engine signal name for this tactic, e.g. `tag:urgency`. */
    val signal: String get() = "tag:$wire"

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** Wire names from earlier versions, so tags already stored on a phone still load. */
        private val legacy = mapOf("authority_impersonation" to AUTHORITY_CLAIM)

        fun fromWire(wire: String): Tactic? = byWire[wire] ?: legacy[wire]
    }
}

/** The six stages every scam family moves through (PRD 9.1). */
@Serializable
enum class Stage(val number: Int, val baseRisk: Int) {
    CONTACT(1, 5),
    HOOK(2, 20),
    GROOMING(3, 40),
    COMMITMENT(4, 60),
    EXTRACTION(5, 80),
    LOCK_IN(6, 90);
}

/** Observation types written to the ledger (PRD 12.2). */
@Serializable
enum class EventType(val wire: String) {
    @SerialName("message") MESSAGE("message"),
    @SerialName("group_added") GROUP_ADDED("group_added"),
    @SerialName("call_started") CALL_STARTED("call_started"),
    @SerialName("call_ended") CALL_ENDED("call_ended"),
    @SerialName("app_installed") APP_INSTALLED("app_installed"),
    @SerialName("install_screen_opened") INSTALL_SCREEN_OPENED("install_screen_opened"),
    @SerialName("payment_app_opened") PAYMENT_APP_OPENED("payment_app_opened"),
    @SerialName("upi_link_opened") UPI_LINK_OPENED("upi_link_opened"),
    @SerialName("screen_share_started") SCREEN_SHARE_STARTED("screen_share_started"),
    @SerialName("remote_app_opened") REMOTE_APP_OPENED("remote_app_opened"),
    @SerialName("payment_sms") PAYMENT_SMS("payment_sms"),
    @SerialName("user_reply") USER_REPLY("user_reply");

    val signal: String get() = "event:$wire"

    companion object {
        private val byWire = entries.associateBy { it.wire }
        fun fromWire(wire: String): EventType? = byWire[wire]
    }
}

/** Irreversible or high-stakes user actions where a full-screen warning may appear (PRD glossary). */
@Serializable
enum class TripwireMoment(val wire: String) {
    @SerialName("install") INSTALL("install"),
    @SerialName("payment") PAYMENT("payment"),
    @SerialName("screen_share") SCREEN_SHARE("screen_share"),
    /** Not irreversible, but digital-arrest calls get an in-call notice (INT-10). */
    @SerialName("call") CALL("call");
}

@Serializable
enum class CounterpartyType { PERSON, GROUP }

@Serializable
enum class TagSource { MODEL, RULE }

@Serializable
data class TacticTag(
    val tactic: Tactic,
    val confidence: Double,
    val source: TagSource,
)

/** An amount in paise, so arithmetic stays exact. */
@Serializable
data class Amount(val paise: Long) {
    val rupees: Double get() = paise / 100.0

    /** Indian digit grouping: 2,00,000. */
    fun format(): String {
        val whole = paise / 100
        val s = whole.toString()
        if (s.length <= 3) return "₹$s"
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "₹$rest,$last3"
    }
}

/** Entities pulled from text by fixed rules, never by the model (SIG-05). */
@Serializable
data class Entities(
    val urls: List<String> = emptyList(),
    val upiHandles: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val amounts: List<Amount> = emptyList(),
    val apkLinks: List<String> = emptyList(),
    val appNames: List<String> = emptyList(),
    val telegramHandles: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = urls.isEmpty() && upiHandles.isEmpty() && phoneNumbers.isEmpty() &&
            amounts.isEmpty() && apkLinks.isEmpty() && appNames.isEmpty() && telegramHandles.isEmpty()

    operator fun plus(other: Entities) = Entities(
        urls = (urls + other.urls).distinct(),
        upiHandles = (upiHandles + other.upiHandles).distinct(),
        phoneNumbers = (phoneNumbers + other.phoneNumbers).distinct(),
        amounts = (amounts + other.amounts).distinct(),
        apkLinks = (apkLinks + other.apkLinks).distinct(),
        appNames = (appNames + other.appNames).distinct(),
        telegramHandles = (telegramHandles + other.telegramHandles).distinct(),
    )
}

/**
 * One raw thing a signal collector saw, before it is tied to a counterparty.
 * Collectors produce these; the pipeline normalises them into [Event]s (SIG-09).
 */
@Serializable
data class Observation(
    val type: EventType,
    /** Package name of the app the observation came from, e.g. `com.whatsapp`. */
    val app: String,
    val timestamp: Long,
    /** Which collector produced it, e.g. `notification`, `package`, `usage`, `upi_link`. */
    val source: String,
    val text: String? = null,
    /** Display name of the sender as the app shows it. */
    val senderName: String? = null,
    /** Phone number of the sender, when the app exposes one. */
    val senderPhone: String? = null,
    /** Group or channel name, when the observation came from a group. */
    val groupName: String? = null,
    /** True when the sender (or the call peer) is a saved contact. Contact text is discarded (SIG-03). */
    val fromSavedContact: Boolean = false,
    /** For installs: package of the installed app, its label and the installer package. */
    val installedPackage: String? = null,
    val installedLabel: String? = null,
    val installerPackage: String? = null,
    /** For UPI links and payment SMS. */
    val upi: UpiPayment? = null,
    /** For calls: whether it is a video call. */
    val isVideoCall: Boolean = false,
)

/** What a UPI link or QR carries (PRD Appendix D), or what a payment SMS reported. */
@Serializable
data class UpiPayment(
    val payeeHandle: String,
    val payeeName: String? = null,
    val amount: Amount? = null,
    val note: String? = null,
    /** Unique Transaction Reference, only known after payment. */
    val utr: String? = null,
    val rawUri: String? = null,
)

/** One normalised, stored observation belonging to one counterparty (SIG-09, table `event`). */
@Serializable
data class Event(
    val id: Long,
    val counterpartyId: String,
    val app: String,
    val type: EventType,
    val text: String?,
    val entities: Entities,
    val timestamp: Long,
    val source: String,
    val senderName: String? = null,
    val groupName: String? = null,
    val installedPackage: String? = null,
    val installedLabel: String? = null,
    val installerPackage: String? = null,
    val upi: UpiPayment? = null,
    val isVideoCall: Boolean = false,
    val textDeleted: Boolean = false,
)

/** A stranger or group (table `counterparty`). */
@Serializable
data class Counterparty(
    val id: String,
    val displayName: String,
    val type: CounterpartyType,
    val identifiers: Set<String>,
    val apps: Set<String>,
    val firstSeen: Long,
    val lastSeen: Long,
    val trusted: Boolean = false,
)

@Serializable
enum class LinkReason { SAME_NUMBER, SAME_HANDLE, SAME_LINK, GROUP_MEMBER, HANDOFF_MESSAGE }

@Serializable
data class CounterpartyLink(
    val fromId: String,
    val toId: String,
    val reason: LinkReason,
    val confidence: Double,
)

@Serializable
enum class CaseStatus { WATCHING, WARNED, CLOSED, TRUSTED }

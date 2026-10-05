package com.tripwire.core.guard

import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.checks.GroundedChecks
import com.tripwire.core.entity.EntityExtractor
import com.tripwire.core.model.Amount
import com.tripwire.core.script.BankDef
import com.tripwire.core.script.ScriptPack

/**
 * Guards: instant, single-event protections that sit beside the progression engine. Each one
 * recognises a moment that is risky on its own (a code arriving mid-call, a fake "credited" SMS,
 * a collect request, an offer to recover lost money, an app from a link taking over the screen)
 * and produces a plain-language alert. Text is matched here and never stored by the guards.
 */
data class GuardAlert(
    val kind: Kind,
    /** The case the alert belongs to, when there is one. */
    val caseId: String?,
    val title: String,
    val body: String,
) {
    enum class Kind { OTP, FAKE_CREDIT, COLLECT_REQUEST, RECOVERY, APP_ACCESS }

    /** Recorded as the intervention subject, so each guard fires once per case. */
    val subject: String get() = "guard:${kind.name.lowercase()}"
}

/** The SMS apps: fake "credited" messages and one-time codes are only judged there. */
object SmsApps {
    val packages = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.truecaller")
}

/** One-time codes, in English and Hindi. Android may hide the text; the collector handles that. */
object OtpDetector {
    private val words = Regex("""(?i)\b(otp|one[- ]time password|verification code|security code|login code|passcode|code is|is your code)\b|ओटीपी|सत्यापन कोड""")
    private val code = Regex("""(?<![\d₹])\d{4,8}(?!\d)""")

    fun looksLikeOtp(text: String?): Boolean = text != null && words.containsMatchIn(text) && code.containsMatchIn(text)
}

/**
 * Banks, shops and services send SMS from registered sender IDs: a 2-letter route, 6 letters or
 * digits, and since 2025 a category suffix (VM-SBIINB-S). People send from phone numbers.
 */
object SenderIds {
    private val id = Regex("""^(?:[A-Z]{2}-)?[A-Z0-9]{6}(?:-[STPG])?$""", RegexOption.IGNORE_CASE)

    fun isBusiness(sender: String?): Boolean {
        val s = sender?.trim() ?: return false
        return id.matches(s) && s.any { it.isLetter() }
    }
}

/**
 * A "money credited" SMS sent from a mobile number. Banks send alerts from registered sender IDs
 * such as VM-SBIINB, never from a 10-digit number, so this is the "sent you money by mistake,
 * please return it" scam.
 */
object FakeCreditDetector {
    private val credit = Regex("""(?i)\b(credited|received|deposited|added to your (a/c|account))\b|जमा (किए|हुए|हो)""")
    private val account = Regex("""(?i)\b(a/c|acct|account|bank|upi)\b|खाते""")

    fun isCreditClaim(text: String?): Boolean {
        if (text == null || !credit.containsMatchIn(text) || !account.containsMatchIn(text)) return false
        return EntityExtractor.extract(text).amounts.isNotEmpty()
    }

    /** True when the SMS claims money arrived but did not come from a bank sender ID. */
    fun matches(text: String?, senderName: String?): Boolean = isCreditClaim(text) && !SenderIds.isBusiness(senderName)
}

/** "X has requested ₹2,000" from a UPI app: approving it sends money (UPI collect request). */
object CollectRequestDetector {
    data class Request(val requester: String?, val handle: String?, val amount: Amount?)

    private val request = Regex(
        """(?i)(has requested|requested (₹|rs\.?|inr|money|payment)|is requesting|payment request|collect request|money request|request(ed)? for (₹|rs)|ने .*?(अनुरोध|माँगे|मांगे))""",
    )
    private val requesterBefore = Regex("""(?i)^\s*(.{2,40}?)\s+(has requested|is requesting|requested)\b""")
    private val requesterFrom = Regex("""(?i)\bfrom\s+([A-Za-z][A-Za-z .]{1,39}?)(?:\s+for\b|\s*[(·|:]|[.,]|$)""")

    fun parse(title: String?, text: String?): Request? {
        val all = listOfNotNull(title, text).joinToString(" · ")
        if (!request.containsMatchIn(all)) return null
        val entities = EntityExtractor.extract(all)
        val requester = listOfNotNull(text, title).firstNotNullOfOrNull { requesterBefore.find(it)?.groupValues?.get(1)?.trim() }
            ?: listOfNotNull(title, text).firstNotNullOfOrNull { requesterFrom.find(it)?.groupValues?.get(1)?.trim() }
        return Request(requester, entities.upiHandles.firstOrNull(), entities.amounts.firstOrNull())
    }
}

/** "We can recover your lost money": after a scam, offers to get money back are a second scam. */
object RecoveryDetector {
    private val recover = Regex("""(?i)\b(recover(y|ed)?|refund|get (your |the )?money back|retrieve|reclaim)\b|पैसा वापस|पैसे वापस|रिकवरी""")
    private val loss = Regex("""(?i)\b(lost|scam(med)?|fraud|cheated|stolen|cyber ?(crime|cell)|complaint)\b|ठगी|धोखा|फ्रॉड""")

    fun matches(text: String?): Boolean = text != null && recover.containsMatchIn(text) && loss.containsMatchIn(text)
}

/** One installed app, as the phone checkup sees it. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    /** The store or app that installed it; null when unknown (for example a file or USB install). */
    val installer: String?,
    val system: Boolean,
    val readsSms: Boolean = false,
    val controlsScreen: Boolean = false,
    val readsNotifications: Boolean = false,
)

enum class AppConcern { REMOTE_ACCESS, NOT_FROM_STORE, READS_SMS, CONTROLS_SCREEN, READS_NOTIFICATIONS, LOOKALIKE }

data class AppFinding(val app: InstalledApp, val concerns: List<AppConcern>, val severe: Boolean)

/** What the checkup found, and how many apps came from outside a store without any risky access. */
data class CheckupResult(val checked: Int, val findings: List<AppFinding>, val outsideStoreOnly: Int)

/**
 * The phone checkup: which installed apps deserve a look. An app from outside a store that can
 * read SMS, read notifications or control the screen is how OTP-stealing malware works, so those
 * are severe. Remote-access apps have honest uses, so they are listed for a look, not alarm. An
 * app that is only from outside a store is counted, not listed: alone it is not a danger, and a
 * long list of harmless apps teaches people to ignore the screen.
 */
class PhoneCheckup(private val pack: ScriptPack) {
    private val checks = GroundedChecks(pack)
    private val remote = pack.apps.remoteAccess.map { it.packageName }.toSet()

    fun assess(apps: List<InstalledApp>, self: String): CheckupResult {
        val mine = apps.filter { it.packageName != self && !it.system }
        val all = findingsFor(mine)
        val listed = all.filter { f -> f.concerns.any { it != AppConcern.NOT_FROM_STORE } }
        return CheckupResult(mine.size, listed, all.size - listed.size)
    }

    private fun findingsFor(apps: List<InstalledApp>): List<AppFinding> = apps
        .mapNotNull { app ->
            val outside = app.installer == null || app.installer !in pack.apps.stores
            val concerns = buildList {
                if (app.packageName in remote) add(AppConcern.REMOTE_ACCESS)
                if (outside) add(AppConcern.NOT_FROM_STORE)
                if (outside && app.readsSms) add(AppConcern.READS_SMS)
                if (outside && app.controlsScreen) add(AppConcern.CONTROLS_SCREEN)
                if (outside && app.readsNotifications) add(AppConcern.READS_NOTIFICATIONS)
                if (outside && checks.lookalikeApp(app.packageName, app.label)?.outcome == CheckOutcome.FAIL) add(AppConcern.LOOKALIKE)
            }
            if (concerns.isEmpty()) return@mapNotNull null
            val severe = concerns.any { it in SEVERE }
            AppFinding(app, concerns, severe)
        }
        .sortedWith(compareByDescending<AppFinding> { it.severe }.thenByDescending { it.concerns.size }.thenBy { it.app.label.lowercase() })

    /** True when an app gaining screen or notification access should raise the alarm at once. */
    fun isRiskyGrant(app: InstalledApp): Boolean = !app.system && (app.installer == null || app.installer !in pack.apps.stores)

    companion object {
        private val SEVERE = setOf(AppConcern.READS_SMS, AppConcern.CONTROLS_SCREEN, AppConcern.READS_NOTIFICATIONS, AppConcern.LOOKALIKE)
    }
}

/**
 * Which bank a payment SMS came from, so "I already paid" can offer that bank's own fraud line.
 * The sender ID decides first (VM-SBIINB-S: a 2-letter route prefix, the bank's 6 characters,
 * and since 2025 a category suffix); otherwise the bank named earliest in the text.
 */
class BankDirectory(private val pack: ScriptPack) {
    val banks get() = pack.banks

    fun identify(sender: String?, text: String?): BankDef? {
        val header = sender?.uppercase()?.split('-', ' ')?.firstOrNull { it.length >= 5 && it.all(Char::isLetterOrDigit) }
        if (header != null) {
            pack.banks.firstOrNull { b -> b.smsHeaders.any { header.startsWith(it) } }?.let { return it }
        }
        if (text.isNullOrBlank()) return null
        return pack.banks.mapNotNull { b ->
            b.aliases.mapNotNull { a -> Regex("""\b${Regex.escape(a)}\b""", RegexOption.IGNORE_CASE).find(text)?.range?.first }.minOrNull()?.let { b to it }
        }.minByOrNull { it.second }?.first
    }
}

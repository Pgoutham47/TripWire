package com.tripwire.core.parse

import com.tripwire.core.entity.EntityExtractor
import com.tripwire.core.model.Amount
import com.tripwire.core.model.UpiPayment
import java.net.URLDecoder

/** Parses `upi://pay?pa=...&pn=...&am=...&tn=...` links and QR payloads (SIG-08, SIG-16, Appendix D). */
object UpiUri {
    fun parse(uri: String): UpiPayment? {
        val trimmed = uri.trim()
        if (!trimmed.lowercase().startsWith("upi://")) return null
        val query = trimmed.substringAfter('?', "")
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull { part ->
            val k = part.substringBefore('=', "").lowercase()
            if (k.isEmpty()) return@mapNotNull null
            val v = part.substringAfter('=', "")
            k to decode(v)
        }.toMap()
        val pa = params["pa"]?.trim()?.lowercase()?.takeIf { it.contains('@') } ?: return null
        val amount = params["am"]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Amount(Math.round(it * 100)) }
        return UpiPayment(
            payeeHandle = pa,
            payeeName = params["pn"]?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
            note = params["tn"]?.trim()?.takeIf { it.isNotEmpty() },
            rawUri = trimmed,
        )
    }

    private fun decode(v: String): String = try {
        URLDecoder.decode(v, Charsets.UTF_8.name())
    } catch (e: IllegalArgumentException) {
        v
    }
}

/** Reads payment-confirmation SMS for amount, payee and transaction reference (SIG-14, EVD-05). */
object PaymentSmsParser {
    private val debitWords = Regex("""(?i)\b(debited|paid|sent|transferred|withdrawn|deducted)\b""")
    private val utrRegex = Regex("""(?i)(?:utr|upi ref(?:erence)?|ref(?:erence)?|rrn|txn(?: id)?)[\s.:#no]*?(\d{12})\b""")
    private val anyTwelve = Regex("""\b(\d{12})\b""")
    private val vpaRegex = Regex("""(?i)(?:to|vpa|upi id)[\s:]*([a-z0-9._-]+@[a-z][a-z0-9]+)""")
    private val payeeNameRegex = Regex("""(?i)\bto\s+([A-Z][A-Za-z .&]{2,40}?)(?:\s+on\b|\s+via\b|\s+ref\b|\.|,|\s+upi\b)""")

    fun parse(text: String, timestamp: Long): ParsedPayment? {
        if (!debitWords.containsMatchIn(text)) return null
        val amount = EntityExtractor.extract(text).amounts.firstOrNull() ?: return null
        val utr = utrRegex.find(text)?.groupValues?.get(1) ?: anyTwelve.find(text)?.groupValues?.get(1)
        val vpa = vpaRegex.find(text)?.groupValues?.get(1)?.lowercase()
            ?: EntityExtractor.extract(text).upiHandles.firstOrNull()
        val name = payeeNameRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { vpa == null || !it.contains('@') }
        return ParsedPayment(amount = amount, payeeHandle = vpa, payeeName = name, utr = utr, timestamp = timestamp)
    }
}

data class ParsedPayment(
    val amount: Amount,
    val payeeHandle: String?,
    val payeeName: String?,
    val utr: String?,
    val timestamp: Long,
)

/**
 * Turns the fields of a messaging-app notification into sender, group and text (SIG-01, SIG-04).
 * Each app formats notifications differently; this is the one place that knows the formats.
 */
object NotificationParser {

    data class Raw(
        val packageName: String,
        /** `android.title` */
        val title: String?,
        /** `android.text` (or the last line of a MessagingStyle) */
        val text: String?,
        /** `android.conversationTitle` */
        val conversationTitle: String?,
        /** `android.isGroupConversation` */
        val isGroupConversation: Boolean,
        /** Sender of the latest MessagingStyle message, when present. */
        val messagingSender: String?,
        /** True for group-summary notifications ("5 messages from 3 chats"). */
        val isSummary: Boolean,
    )

    data class Parsed(
        val senderName: String,
        val senderPhone: String?,
        val groupName: String?,
        val text: String,
        val isGroupAdd: Boolean,
    )

    private val countSuffix = Regex("""\s*\((\d+) (messages|new messages|संदेश)\)\s*$""", RegexOption.IGNORE_CASE)
    private val groupAddPatterns = listOf(
        Regex("""(?i)\badded you\b"""),
        Regex("""(?i)\byou were added\b"""),
        Regex("""(?i)\binvited you to (join )?(the )?group\b"""),
        Regex("""ने आपको (जोड़ा|ऐड किया)"""),
        Regex("""आपको .* में जोड़ा गया"""),
    )
    private val summaryText = Regex("""(?i)^\d+ (new )?messages( from \d+ chats)?$""")

    fun parse(raw: Raw): Parsed? {
        if (raw.isSummary) return null
        val text = raw.text?.trim().orEmpty()
        if (text.isEmpty() || summaryText.matches(text)) return null
        val title = raw.title?.trim().orEmpty()
        val convTitle = raw.conversationTitle?.trim()?.replace(countSuffix, "")

        var group: String? = null
        var sender: String
        var body = text

        if (raw.isGroupConversation || !convTitle.isNullOrEmpty()) {
            group = convTitle?.takeIf { it.isNotEmpty() }
            sender = raw.messagingSender?.trim().takeUnless { it.isNullOrEmpty() } ?: title
            // WhatsApp group titles without MessagingStyle: "Group Name: Sender"
            if (group == null && title.contains(": ")) {
                group = title.substringBefore(": ").replace(countSuffix, "")
                sender = title.substringAfter(": ")
            }
        } else if (title.contains(": ") && raw.packageName.startsWith("com.whatsapp")) {
            group = title.substringBefore(": ").replace(countSuffix, "")
            sender = title.substringAfter(": ")
        } else if (raw.packageName == "org.telegram.messenger" && text.contains(": ") && title.isNotEmpty() && raw.messagingSender == null && raw.isGroupConversation) {
            group = title
            sender = text.substringBefore(": ")
            body = text.substringAfter(": ")
        } else {
            sender = title.replace(countSuffix, "")
        }

        // WhatsApp shows unknown senders as "~ Name" or "+91 98765 43210 ~Name".
        sender = sender.removePrefix("~").trim()
        val phone = EntityExtractor.normalizePhone(sender.substringBefore('~').trim())
            ?: Regex("""\+?[\d\s-]{10,16}""").find(sender)?.value?.let { EntityExtractor.normalizePhone(it) }
        val isGroupAdd = groupAddPatterns.any { it.containsMatchIn(body) }
        return Parsed(
            senderName = sender.ifEmpty { group ?: "Unknown" },
            senderPhone = phone,
            groupName = group,
            text = body,
            isGroupAdd = isGroupAdd,
        )
    }

    /** True when the sender is shown as a raw number, the fallback stranger rule without contacts (SIG-02). */
    fun looksLikeRawNumber(sender: String): Boolean =
        sender.filter { it.isDigit() }.length >= 10 && sender.count { it.isLetter() } == 0
}

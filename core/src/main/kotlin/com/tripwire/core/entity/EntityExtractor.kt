package com.tripwire.core.entity

import com.tripwire.core.model.Amount
import com.tripwire.core.model.Entities

/**
 * Pulls links, UPI handles, phone numbers, amounts, app-file links and app names out of text
 * with fixed rules (SIG-05). The model never does this: entities must be exact and repeatable.
 */
object EntityExtractor {

    private val urlRegex = Regex(
        """(?i)\b(?:https?://|www\.)[^\s<>"']+|\b(?:[a-z0-9-]+\.)+(?:com|in|net|org|io|app|co|xyz|top|vip|site|online|link|info|me|ly|gl)(?:/[^\s<>"']*)?""",
    )

    // A UPI handle's provider part never contains a dot, which is how it differs from an email.
    private val upiRegex = Regex("""(?<![\w.@-])([a-zA-Z0-9][a-zA-Z0-9._-]{1,255}@[a-zA-Z][a-zA-Z0-9]{1,63})(?![\w@.]*\.[a-zA-Z])""")

    private val phoneRegex = Regex("""(?<![\d+])(?:\+?91[\s-]?|0)?([6-9]\d{4})[\s-]?(\d{5})(?!\d)""")

    private val amountRegex = Regex(
        """(?i)(?:₹|rs\.?|inr|rupees?)\s*([\d,]+(?:\.\d{1,2})?)\s*(lakh|lac|lakhs|crore|cr|k|thousand|हज़ार|हजार|लाख|करोड़)?|([\d,]+(?:\.\d{1,2})?)\s*(lakh|lac|lakhs|crore|cr|k|हज़ार|हजार|लाख|करोड़)?\s*(?:rupees?|rs\b|₹|रुपये|रुपए)""",
    )

    private val telegramLinkRegex = Regex("""(?i)\bt\.me/([a-z][a-z0-9_]{3,31})""")
    private val telegramAtRegex = Regex("""(?i)(?:telegram|tg|टेलीग्राम)[^@\n]{0,30}@([a-z][a-z0-9_]{3,31})\b""")

    private val appNameRegexes = listOf(
        // "install SATFIN Pro", "download the Zen Trade app"
        Regex("""(?i)\b(?:install|download|डाउनलोड|इंस्टॉल)\s+(?:the\s+|our\s+)?([A-Z][\w]*(?:\s+[A-Z][\w]*){0,2})"""),
        // "SATFIN Pro app"
        Regex("""\b([A-Z][A-Za-z0-9]+(?:\s+[A-Z][A-Za-z0-9]+){0,2})\s+(?:app|App|APP|ऐप)\b"""),
    )
    private val appNameStopWords = setOf("the", "our", "this", "app", "now", "it", "here", "link", "from", "and")

    fun extract(text: String?): Entities {
        if (text.isNullOrBlank()) return Entities()
        val urls = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', '!', '?') }.distinct().toList()
        val emails = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""").findAll(text).map { it.value }.toSet()
        val handles = upiRegex.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .filter { h -> emails.none { it.lowercase().startsWith(h) } }
            .filterNot { h -> urls.any { it.contains(h) } }
            .distinct().toList()
        val phones = phoneRegex.findAll(text)
            .map { "+91" + it.groupValues[1] + it.groupValues[2] }
            .distinct().toList()
        val amounts = amountRegex.findAll(text).mapNotNull { parseAmount(it) }.distinct().toList()
        val apkLinks = urls.filter { isApkLink(it) }
        val telegram = (telegramLinkRegex.findAll(text).map { it.groupValues[1] } +
            telegramAtRegex.findAll(text).map { it.groupValues[1] })
            .map { it.lowercase() }.distinct().toList()
        val appNames = appNameRegexes.flatMap { r -> r.findAll(text).map { it.groupValues[1].trim() } }
            .map { name -> name.split(Regex("\\s+")).filterNot { it.lowercase() in appNameStopWords }.joinToString(" ") }
            .filter { it.length >= 3 }
            .distinctBy { normalizeName(it) }
        return Entities(
            urls = urls,
            upiHandles = handles,
            phoneNumbers = phones,
            amounts = amounts,
            apkLinks = apkLinks,
            appNames = appNames,
            telegramHandles = telegram,
        )
    }

    fun isApkLink(url: String): Boolean {
        val u = url.lowercase()
        return u.substringBefore('?').endsWith(".apk") || Regex("""[/=._-]apk\b""").containsMatchIn(u)
    }

    /** Domain of a link without `www.`, used to compare links across apps. */
    fun domainOf(url: String): String =
        url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/').substringBefore('?').substringBefore(':')

    /** Lower-case letters and digits only, so "SATFIN Pro" and "satfin-pro" compare equal. */
    fun normalizeName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Canonical +91 form of an Indian mobile number, or null if it is not one. */
    fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val ten = when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            else -> return null
        }
        return if (ten.first() in '6'..'9') "+91$ten" else null
    }

    private fun parseAmount(m: MatchResult): Amount? {
        val number = m.groupValues[1].ifEmpty { m.groupValues[3] }
        val unit = m.groupValues[2].ifEmpty { m.groupValues[4] }.lowercase()
        val value = number.replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (unit) {
            "lakh", "lac", "lakhs", "लाख" -> 1_00_000.0
            "crore", "cr", "करोड़" -> 1_00_00_000.0
            "k", "thousand", "हज़ार", "हजार" -> 1_000.0
            else -> 1.0
        }
        val rupees = value * multiplier
        if (rupees <= 0 || rupees > 1e11) return null
        return Amount(Math.round(rupees * 100))
    }
}

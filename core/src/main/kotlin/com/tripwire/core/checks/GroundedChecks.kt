package com.tripwire.core.checks

import com.tripwire.core.entity.EntityExtractor
import com.tripwire.core.script.ScriptPack
import kotlinx.serialization.Serializable

@Serializable
enum class CheckOutcome { PASS, FAIL, UNKNOWN }

/**
 * One grounded check: a fact, not a model opinion (CHK-04, table `check_result`).
 * [messageKey] indexes the pack's `strings`; [params] fills its placeholders.
 */
@Serializable
data class CheckResult(
    val checkId: String,
    val subject: String,
    val outcome: CheckOutcome,
    val messageKey: String,
    val params: Map<String, String> = emptyMap(),
) {
    /** The engine signal this result contributes, e.g. `check:valid_handle:fail`. */
    val signal: String? get() = when (outcome) {
        CheckOutcome.PASS -> "check:$checkId:pass"
        CheckOutcome.FAIL -> "check:$checkId:fail"
        CheckOutcome.UNKNOWN -> null
    }
}

object CheckIds {
    const val VALID_HANDLE = "valid_handle"
    const val INSTALL_SOURCE = "install_source"
    const val KNOWN_BAD = "known_bad"
    const val NAME_MISMATCH = "name_mismatch"
    const val LOOKALIKE = "lookalike"
}

class GroundedChecks(private val pack: ScriptPack) {
    private val validHandle = Regex(pack.validHandlePattern, RegexOption.IGNORE_CASE)

    /**
     * CHK-01: SEBI requires registered brokers and mutual funds to collect investor money through
     * `@valid` handles. When the case is an investment scam or the payee claims to be a broker,
     * a handle that is not in that format fails. Where the rule does not apply, nothing is reported.
     */
    fun validHandle(handle: String, applies: Boolean): CheckResult? {
        val h = handle.trim().lowercase()
        return when {
            validHandle.matches(h) -> CheckResult(CheckIds.VALID_HANDLE, h, CheckOutcome.PASS, "check.valid_handle.pass", mapOf("handle" to h))
            applies -> CheckResult(CheckIds.VALID_HANDLE, h, CheckOutcome.FAIL, "check.valid_handle.fail", mapOf("handle" to h))
            else -> null
        }
    }

    /** CHK-03: an app that came from a link, not an app store, fails. An unknown installer is reported as unknown. */
    fun installSource(packageName: String, label: String?, installer: String?): CheckResult {
        val subject = label ?: packageName
        val params = mapOf("app" to subject)
        return when {
            installer == null -> CheckResult(CheckIds.INSTALL_SOURCE, subject, CheckOutcome.UNKNOWN, "check.install_source.unknown", params)
            installer in pack.apps.stores -> CheckResult(CheckIds.INSTALL_SOURCE, subject, CheckOutcome.PASS, "check.install_source.pass", params)
            else -> CheckResult(CheckIds.INSTALL_SOURCE, subject, CheckOutcome.FAIL, "check.install_source.fail", params)
        }
    }

    /** True when an installer package means the app was installed from a file or link. */
    fun isLinkInstall(installer: String?): Boolean =
        installer != null && installer !in pack.apps.stores

    /** CHK-05: the handle, number or link is on the script pack's known-bad list. Only failures are reported. */
    fun knownBad(handles: List<String>, numbers: List<String>, urls: List<String>): CheckResult? {
        val bad = pack.knownBad
        handles.map { it.lowercase() }.firstOrNull { it in bad.handles.map(String::lowercase) }?.let {
            return CheckResult(CheckIds.KNOWN_BAD, it, CheckOutcome.FAIL, "check.known_bad.fail", mapOf("subject" to it))
        }
        numbers.firstOrNull { n -> bad.numbers.any { EntityExtractor.normalizePhone(it) == n } }?.let {
            return CheckResult(CheckIds.KNOWN_BAD, it, CheckOutcome.FAIL, "check.known_bad.fail", mapOf("subject" to it))
        }
        urls.map { EntityExtractor.domainOf(it) }.firstOrNull { d -> bad.domains.any { d == it || d.endsWith(".$it") } }?.let {
            return CheckResult(CheckIds.KNOWN_BAD, it, CheckOutcome.FAIL, "check.known_bad.fail", mapOf("subject" to it))
        }
        return null
    }

    /**
     * CHK-06: the name registered on the payee handle shares no word with who the counterparty
     * claims to be (group name, sender name, app name). Unknown when either side is missing.
     */
    fun nameMismatch(payeeName: String?, claimedNames: List<String>): CheckResult? {
        if (payeeName.isNullOrBlank() || claimedNames.isEmpty()) return null
        val payeeTokens = tokens(payeeName)
        if (payeeTokens.isEmpty()) return null
        val claimed = claimedNames.flatMap { tokens(it) }.toSet()
        if (claimed.isEmpty()) return null
        val overlap = payeeTokens.any { p -> claimed.any { c -> c == p || (p.length >= 4 && (c.startsWith(p) || p.startsWith(c))) } }
        val params = mapOf("payee" to payeeName, "claimed" to claimedNames.first())
        return if (overlap) {
            CheckResult(CheckIds.NAME_MISMATCH, payeeName, CheckOutcome.PASS, "check.name_mismatch.pass", params)
        } else {
            CheckResult(CheckIds.NAME_MISMATCH, payeeName, CheckOutcome.FAIL, "check.name_mismatch.fail", params)
        }
    }

    /**
     * CHK-07: an app label or web address imitates a known broker or bank but is not its official
     * package or domain. Reported only when a brand name appears.
     */
    fun lookalikeApp(packageName: String, label: String): CheckResult? {
        val norm = EntityExtractor.normalizeName(label)
        val brand = pack.brands.firstOrNull { b -> (listOf(b.name) + b.aliases).any { norm.contains(EntityExtractor.normalizeName(it)) } }
            ?: return null
        return if (packageName in brand.packages) {
            CheckResult(CheckIds.LOOKALIKE, label, CheckOutcome.PASS, "check.lookalike.pass", mapOf("brand" to brand.name))
        } else {
            CheckResult(CheckIds.LOOKALIKE, label, CheckOutcome.FAIL, "check.lookalike.fail", mapOf("brand" to brand.name, "subject" to label))
        }
    }

    fun lookalikeUrl(url: String): CheckResult? {
        val domain = EntityExtractor.domainOf(url)
        val flat = EntityExtractor.normalizeName(domain)
        val brand = pack.brands.firstOrNull { b -> (listOf(b.name) + b.aliases).any { flat.contains(EntityExtractor.normalizeName(it)) } }
            ?: return null
        val official = brand.domains.any { domain == it || domain.endsWith(".$it") }
        return if (official) {
            CheckResult(CheckIds.LOOKALIKE, domain, CheckOutcome.PASS, "check.lookalike.pass", mapOf("brand" to brand.name))
        } else {
            CheckResult(CheckIds.LOOKALIKE, domain, CheckOutcome.FAIL, "check.lookalike.fail", mapOf("brand" to brand.name, "subject" to domain))
        }
    }

    /** CHK-02: where the user can verify a payee for themselves. */
    val sebiCheckUrl: String get() = pack.sebiCheckUrl

    private val genericWords = setOf(
        "pvt", "ltd", "private", "limited", "llp", "the", "and", "of", "india", "services", "service",
        "group", "club", "official", "team", "mr", "mrs", "ms", "sir", "co", "company", "vip",
    )

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in genericWords && !it.all(Char::isDigit) }
            .toSet()
}

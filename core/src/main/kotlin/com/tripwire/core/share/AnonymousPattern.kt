package com.tripwire.core.share

import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.ledger.Feedback
import com.tripwire.core.ledger.UserChoice
import com.tripwire.core.model.EventType
import com.tripwire.core.pipeline.TripwirePipeline
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * The only user-derived data that may leave the phone, and only with opt-in (PRD 12.4, FBK-03).
 * No text, names, numbers, handles, links, amounts or device identifier, by construction:
 * every field is an enum name, a tag name, a rounded gap or an app package.
 */
@Serializable
data class AnonymousPattern(
    val family: String?,
    val finalStage: String,
    val steps: List<Step>,
    val failedChecks: List<String>,
    val outcome: String,
    val language: String,
    val apps: List<String>,
) {
    @Serializable
    data class Step(val kind: String, val tags: List<String>, val hoursSincePrevious: Int)

    fun toJson(): String = com.tripwire.core.script.ScriptPack.json.encodeToString(this)

    companion object {
        fun build(pipeline: TripwirePipeline, caseId: String, language: String): AnonymousPattern? {
            val store = pipeline.store
            val root = pipeline.rootOf(caseId)
            val state = store.caseState(root) ?: return null
            val events = store.eventsFor(pipeline.membersOf(root))
            var prev: Long? = null
            val steps = events.map { e ->
                val gap = prev?.let { ((e.timestamp - it) / TripwirePipeline.HOUR_MS).toInt() } ?: 0
                prev = e.timestamp
                Step(e.type.wire, store.tags(e.id).map { it.tactic.wire }.sorted(), gap)
            }.filter { it.kind != EventType.CALL_ENDED.wire }
            val interventions = store.interventions(root)
            val outcome = when {
                interventions.any { it.feedback == Feedback.GENUINE } -> "confirmed_genuine"
                interventions.any { it.feedback == Feedback.SCAM } -> "confirmed_scam"
                interventions.any { it.choice == UserChoice.PROCEEDED } -> "proceeded"
                interventions.any { it.choice == UserChoice.STOPPED || it.choice == UserChoice.CALLED_ALLY } -> "stopped"
                else -> "unknown"
            }
            return AnonymousPattern(
                family = state.topFamily,
                finalStage = state.stage.name.lowercase(),
                steps = steps,
                failedChecks = store.checks(root).filter { it.first.outcome == CheckOutcome.FAIL }.map { it.first.checkId }.distinct(),
                outcome = outcome,
                language = language,
                apps = events.map { it.app }.distinct(),
            )
        }
    }
}

/**
 * Verifies a script pack against the public key pinned in the app (UPD-01, PRD 15.4).
 * ECDSA P-256 with SHA-256, which every supported Android version provides.
 */
class PackVerifier(publicKeyBase64: String) {
    private val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))

    fun verify(packBytes: ByteArray, signatureBase64: String): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(packBytes)
            verify(Base64.getDecoder().decode(signatureBase64.trim()))
        }
    } catch (e: Exception) {
        false
    }
}

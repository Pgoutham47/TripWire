package com.tripwire.core.engine

import com.tripwire.core.model.Event
import com.tripwire.core.model.EventType
import com.tripwire.core.model.TacticTag

/** Facts about an event's surroundings that the pipeline knows and the event alone does not. */
data class SignalContext(
    /** A private message from someone who is a member of a tracked group (LED-02). */
    val privateAfterGroup: Boolean = false,
    /** A call with this counterparty is in progress. */
    val duringCall: Boolean = false,
    /** A call with this counterparty ended within the time-link window. */
    val recentCall: Boolean = false,
    /** A call with this counterparty lasted 15 minutes or more. */
    val longCall: Boolean = false,
    /** The counterparty moved the user to another app (cross-app hand-off, SIG-15). */
    val crossAppHandoff: Boolean = false,
    /** The install came from a link, not an app store. */
    val linkInstall: Boolean = false,
)

/** Turns one event, its tags and its context into engine signals. Names match the script pack. */
object Signals {
    private val brokerClaim = Regex(
        """(?i)\b(sebi[- ]?(registered|regd|approved|certified)|registered (investment )?advis[eo]r|research analyst|ria\b|portfolio manager|stock broker|demat partner)""",
    )

    private val kycMention = Regex(
        """(?i)\b(kyc|re-?kyc|pan\s+card\s+(update|link)|aadhaar\s+(update|link|verification)|account\s+(will\s+be\s+)?(blocked|suspended|deactivated))\b|केवाईसी|खाता\s*(बंद|ब्लॉक)""",
    )

    fun fromEvent(event: Event, tags: List<TacticTag>, ctx: SignalContext = SignalContext()): List<SignalHit> {
        val hits = ArrayList<SignalHit>()
        hits += SignalHit(event.type.signal, 1.0)
        tags.forEach { hits += SignalHit(it.tactic.signal, it.confidence) }

        val e = event.entities
        if (e.upiHandles.isNotEmpty() || event.upi != null) hits += SignalHit("entity:upi_handle", 1.0)
        if (e.apkLinks.isNotEmpty()) hits += SignalHit("entity:apk_link", 1.0)
        if (e.urls.isNotEmpty() && e.apkLinks.isEmpty()) hits += SignalHit("entity:url", 1.0)
        if (e.amounts.isNotEmpty()) hits += SignalHit("entity:amount", 1.0)
        if (e.telegramHandles.isNotEmpty()) hits += SignalHit("entity:telegram", 1.0)

        if (event.type == EventType.MESSAGE && event.text != null && brokerClaim.containsMatchIn(event.text)) {
            hits += SignalHit("ctx:claims_broker", 1.0)
        }
        if (event.type == EventType.MESSAGE && event.text != null && kycMention.containsMatchIn(event.text)) {
            hits += SignalHit("ctx:kyc_mention", 1.0)
        }
        if (event.type == EventType.CALL_STARTED && event.isVideoCall) hits += SignalHit("ctx:video_call", 1.0)
        if (ctx.privateAfterGroup) hits += SignalHit("ctx:private_after_group", 1.0)
        if (ctx.duringCall) hits += SignalHit("ctx:during_call", 1.0)
        if (ctx.recentCall) hits += SignalHit("ctx:recent_call", 1.0)
        if (ctx.longCall) hits += SignalHit("ctx:long_call", 1.0)
        if (ctx.crossAppHandoff) hits += SignalHit("ctx:cross_app", 1.0)
        if (ctx.linkInstall) hits += SignalHit("check:install_source:fail", 1.0)
        return hits
    }
}

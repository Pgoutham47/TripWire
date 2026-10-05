package com.tripwire.core

import com.tripwire.core.checks.CheckIds
import com.tripwire.core.checks.CheckOutcome
import com.tripwire.core.engine.ProgressionEngine
import com.tripwire.core.engine.SignalHit
import com.tripwire.core.evidence.AllyAlert
import com.tripwire.core.evidence.Complainant
import com.tripwire.core.evidence.EvidencePackBuilder
import com.tripwire.core.ledger.Feedback
import com.tripwire.core.ledger.InMemoryLedgerStore
import com.tripwire.core.ledger.UserChoice
import com.tripwire.core.model.CaseStatus
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.model.TripwireMoment
import com.tripwire.core.pipeline.PipelineConfig
import com.tripwire.core.pipeline.TripwirePipeline
import com.tripwire.core.replay.ReplayHarness
import com.tripwire.core.script.ScriptPack
import com.tripwire.core.share.AnonymousPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PipelineBehaviourTest {
    private val pack = ScriptPack.bundled()
    private var now = ReplayHarness.DEFAULT_START
    private val store = InMemoryLedgerStore()
    private var config = PipelineConfig(language = "en", allyName = "Anil")
    private val pipeline = TripwirePipeline(pack, store, null, { config }, { now })

    /** Replays the first [upTo] steps of the Ramesh scenario; 7 stops just before the payment link. */
    private fun replayRamesh(upTo: Int = 7): String {
        val s = ReplayHarness.bundledScenarios().first { it.id == "fi_ramesh_hinglish" }
        var caseId: String? = null
        s.steps.take(upTo).forEach { step ->
            now = ReplayHarness.DEFAULT_START + step.t * 60_000
            val out = pipeline.onObservation(step.obs.copy(timestamp = now))
            caseId = caseId ?: out.caseId
        }
        return pipeline.rootOf(caseId!!)
    }

    private fun msg(text: String, phone: String, contact: Boolean = false) = Observation(
        type = EventType.MESSAGE, app = "com.whatsapp", timestamp = now, source = "notification",
        text = text, senderName = phone, senderPhone = phone, fromSavedContact = contact,
    )

    @Test
    fun `messages from saved contacts are never stored`() {
        val out = pipeline.onObservation(msg("Guaranteed 30% returns, kisi ko mat batana", "+919000000099", contact = true))
        assertEquals("saved contact", out.ignored)
        assertTrue(store.allCounterparties().isEmpty())
        assertTrue(store.eventsFor(listOf("tel:+919000000099")).isEmpty())
    }

    @Test
    fun `the payment warning carries the cross-app timeline, the check and the ally button`() {
        replayRamesh()
        now += 5 * 60_000L
        val d = pipeline.onMoment(
            Observation(
                type = EventType.UPI_LINK_OPENED, app = "com.tripwire.app", timestamp = now, source = "upi_link",
                upi = com.tripwire.core.parse.UpiUri.parse("upi://pay?pa=satfin.tripwiredemo@ybl&pn=SATFIN%20Trading&am=200000"),
            ),
        )
        assertTrue(d.show)
        val w = d.warning!!
        assertEquals(TripwireMoment.PAYMENT, w.moment)
        assertEquals("Stop. This payment matches a known investment scam.", w.headline)
        assertTrue(w.reasons.size in 1..3)
        assertTrue(w.check!!.text.contains("@valid"))
        assertEquals("Call Anil first", w.allyLabel)
        assertEquals("Verify on SEBI Check", w.verifyLabel)
        assertTrue(w.timeline.map { it.app }.toSet().size >= 2, "events from more than one app: ${w.timeline}")
        assertFalse(w.headline.contains("is a scam"), "never claims certainty (INT-07)")
        // PRD Appendix C: the story opens with the group add, and one fact is never told twice.
        assertTrue(w.reasons.first().startsWith("A stranger added you to a group"), w.reasons.toString())
        assertTrue(w.reasons.count { it.contains("app", ignoreCase = true) } <= 1, w.reasons.toString())
        assertTrue(w.timeline.any { it.label.startsWith("Installed SATFIN Pro") }, "the install is on the timeline: ${w.timeline}")

        // INT-06: the same payment moment does not fire twice.
        val again = pipeline.onMoment(
            Observation(type = EventType.UPI_LINK_OPENED, app = "com.tripwire.app", timestamp = now + 60_000, source = "upi_link",
                upi = com.tripwire.core.parse.UpiUri.parse("upi://pay?pa=satfin.tripwiredemo@ybl&am=200000")),
        )
        assertFalse(again.show)
    }

    @Test
    fun `warnings are built in Hindi when Hindi is chosen`() {
        config = config.copy(language = "hi")
        replayRamesh(5)
        now += 10 * 60_000
        val d = pipeline.onMoment(
            Observation(type = EventType.APP_INSTALLED, app = "com.google.android.packageinstaller", timestamp = now, source = "package",
                installedPackage = "com.satfin.pro", installedLabel = "SATFIN Pro", installerPackage = "com.google.android.packageinstaller"),
        )
        assertTrue(d.show)
        assertTrue(d.warning!!.headline.startsWith("रुकिए"))
        assertTrue(d.warning!!.reasons.all { r -> r.any { it in 'ऀ'..'ॿ' } }, d.warning!!.reasons.toString())
    }

    /** The system install screen, as the on-screen reader reports it: a name, no package yet (SIG-11). */
    private fun installScreen(label: String?) = Observation(
        type = EventType.INSTALL_SCREEN_OPENED, app = "com.google.android.packageinstaller", timestamp = now, source = "screen",
        installedLabel = label, installerPackage = "com.google.android.packageinstaller",
    )

    @Test
    fun `the install screen warns before the app is installed, once`() {
        replayRamesh(5)
        now += 10 * 60_000
        val before = pipeline.onMoment(installScreen("SATFIN Pro"))
        assertTrue(before.show)
        assertEquals(CheckOutcome.FAIL, before.checks.first { it.checkId == CheckIds.INSTALL_SOURCE }.outcome)
        assertTrue(before.warning!!.timeline.any { it.label == "Started installing SATFIN Pro from a link" }, before.warning!!.timeline.toString())

        // The same app finishing its install a moment later is the same warning, not a second one.
        now += 20_000
        val after = pipeline.onMoment(
            Observation(type = EventType.APP_INSTALLED, app = "com.google.android.packageinstaller", timestamp = now, source = "package",
                installedPackage = "com.satfin.pro", installedLabel = "SATFIN Pro", installerPackage = "com.google.android.packageinstaller"),
        )
        assertFalse(after.show)
    }

    @Test
    fun `the install screen alone, with no suspicious chat, does not warn`() {
        assertFalse(pipeline.onMoment(installScreen("Cricket Scores")).show)
        assertFalse(pipeline.onMoment(installScreen(null)).show)
    }

    @Test
    fun `proceeding raises risk, alerts the ally and pins the paid shortcut`() {
        replayRamesh(5)
        now += 10 * 60_000
        val d = pipeline.onMoment(
            Observation(type = EventType.APP_INSTALLED, app = "x", timestamp = now, source = "package",
                installedPackage = "com.satfin.pro", installedLabel = "SATFIN Pro", installerPackage = "com.android.chrome"),
        )
        val before = store.caseState(d.caseId!!)!!.risk
        val effect = pipeline.recordChoice(d.interventionId!!, UserChoice.PROCEEDED)
        assertTrue(effect.alertAlly)
        assertNotNull(effect.pinPaidShortcutUntil)
        assertTrue(store.caseState(d.caseId!!)!!.risk >= before)
    }

    @Test
    fun `marking as trusted stops tracking and deletes text`() {
        val caseId = replayRamesh(4)
        pipeline.markTrusted(caseId)
        assertEquals(CaseStatus.TRUSTED, store.caseState(caseId)!!.status)
        assertTrue(store.eventsFor(pipeline.membersOf(caseId)).all { it.text == null })
        val out = pipeline.onObservation(msg("Deposit now", "+919000000011"))
        assertEquals("trusted", out.ignored)
    }

    @Test
    fun `retention deletes raw text but keeps the case, and drops quiet strangers`() {
        val caseId = replayRamesh(4)
        pipeline.onObservation(msg("Hello, is this the bakery?", "+919000000098"))
        now += 31L * 24 * 60 * 60_000
        val report = pipeline.purge(retentionDays = 30, now = now)
        assertTrue(report.textsDeleted > 0)
        assertEquals(1, report.counterpartiesDeleted)
        assertNotNull(store.caseState(caseId), "tags and stage outlive the text (LED-06)")
        assertTrue(store.eventsFor(pipeline.membersOf(caseId)).all { it.textDeleted })
    }

    @Test
    fun `a case on hold for an evidence pack is not purged`() {
        val caseId = replayRamesh(4)
        store.setHold(caseId, true)
        now += 120L * 24 * 60 * 60_000
        pipeline.purge(30, now)
        assertTrue(store.eventsFor(pipeline.membersOf(caseId)).any { it.text != null })
    }

    @Test
    fun `the evidence pack has every EVD-01 field`() {
        val caseId = replayRamesh(7)
        now += 10 * 60_000L
        pipeline.onObservation(
            Observation(type = EventType.PAYMENT_SMS, app = "com.google.android.apps.messaging", timestamp = now, source = "notification",
                text = "Rs.2,00,000.00 debited from A/c XX1234 to VPA satfin.tripwiredemo@ybl. UPI Ref No 612345678901."),
        )
        val builder = EvidencePackBuilder(pipeline)
        val tx = builder.latestPayment(caseId)!!
        assertEquals("612345678901", tx.utr)
        val pack = builder.build(caseId, Complainant(name = "Ramesh"), listOf(tx), "en", now)
        assertEquals("fake investment", pack.scamType)
        assertTrue(pack.timeline.size >= 5)
        assertTrue("+919000000011" in pack.phoneNumbers)
        assertTrue("Elite IPO Club 88" in pack.groupNames)
        assertTrue("satfin.tripwiredemo@ybl" in pack.upiHandles)
        assertTrue(pack.links.any { it.endsWith(".apk") })
        assertEquals("com.satfin.pro", pack.apps.single().packageName)
        assertTrue(pack.quotedMessages.isNotEmpty())
        assertEquals(tx.time + TripwirePipeline.HOUR_MS, pack.goldenHourEndsAt)
        assertTrue(builder.callScript(pack, "en").contains("612345678901"))
    }

    @Test
    fun `ally alerts and shared patterns carry no message content or identifiers`() {
        val caseId = replayRamesh(6)
        val state = store.caseState(caseId)!!
        val alert = AllyAlert(AllyAlert.Kind.WARNING_SHOWN, pipeline.explain.familyName(state.topFamily, "en"), "asking for money", now)
        val text = alert.text(pipeline.explain, "Papa", "en")
        assertFalse(text.contains("SATFIN") || text.contains("9000000011") || text.contains("Elite"), text)

        val json = AnonymousPattern.build(pipeline, caseId, "en")!!.toJson()
        listOf("SATFIN", "satfin", "9000000011", "Elite", "Rahul", "allotment", "@ybl", ".apk").forEach {
            assertFalse(json.contains(it), "pattern leaks '$it': $json")
        }
    }

    @Test
    fun `feedback tunes thresholds within limits`() {
        replayRamesh(5)
        now += 10 * 60_000
        val d = pipeline.onMoment(
            Observation(type = EventType.APP_INSTALLED, app = "x", timestamp = now, source = "package",
                installedPackage = "com.satfin.pro", installedLabel = "SATFIN Pro", installerPackage = "com.android.chrome"),
        )
        var offsets = com.tripwire.core.engine.ThresholdOffsets()
        repeat(20) { offsets = pipeline.recordFeedback(d.interventionId!!, Feedback.GENUINE, offsets) }
        assertEquals(pack.thresholds.tuneMax, offsets.warn)
    }

    @Test
    fun `pausing protection ignores observations`() {
        config = config.copy(paused = true)
        assertEquals("paused", pipeline.onObservation(msg("Guaranteed returns", "+919000000097")).ignored)
        assertNull(pipeline.onMoment(
            Observation(type = EventType.PAYMENT_APP_OPENED, app = "com.phonepe.app", timestamp = now, source = "usage"),
        ).caseId)
    }

    @Test
    fun `engine updates take well under 50 ms`() {
        val engine = ProgressionEngine(pack)
        var s = engine.newCase("x", 0)
        val hits = listOf(SignalHit("tag:guaranteed_returns", 0.9), SignalHit("tag:urgency", 0.8), SignalHit("entity:upi_handle", 1.0))
        repeat(200) { s = engine.apply(s, hits, it.toLong(), it.toLong()).state } // warm up
        val start = System.nanoTime()
        repeat(1000) { s = engine.apply(s, hits, it.toLong(), it.toLong()).state }
        val perUpdateMs = (System.nanoTime() - start) / 1e6 / 1000
        assertTrue(perUpdateMs < 50, "per update $perUpdateMs ms")
    }

    @Test
    fun `a payment to a person after the chat invoked a bank fails the name check`() {
        val s = ReplayHarness.bundledScenarios().first { it.id == "kyc_sms_apk" }
        s.steps.forEach { step ->
            now = ReplayHarness.DEFAULT_START + step.t * 60_000
            pipeline.onObservation(step.obs.copy(timestamp = now))
        }
        now += 5 * 60_000
        val d = pipeline.onMoment(
            Observation(type = EventType.UPI_LINK_OPENED, app = "com.whatsapp", timestamp = now, source = "upi_link",
                upi = com.tripwire.core.parse.UpiUri.parse("upi://pay?pa=kyc.tripwiredemo@ybl&pn=Ravi%20Kumar&am=4999")),
        )
        assertTrue(d.show)
        assertEquals("This payment goes to Ravi Kumar, not to SBI.", d.warning!!.check!!.text)
    }

    @Test
    fun `a repeated message is stored once, even across restarts`() {
        val repost = msg("Update KYC today: install our app", "+919000000096")
        val first = pipeline.onObservation(repost)
        assertNotNull(first.eventId)
        now += 4 * 60_000
        // A fresh pipeline on the same ledger, as after an app restart. The re-posted message
        // keeps the time it was sent.
        val restarted = TripwirePipeline(pack, store, null, { config }, { now })
        assertEquals("duplicate", restarted.onObservation(repost).ignored)
        assertEquals(1, store.eventsFor(listOf("tel:+919000000096")).size)
    }

    @Test
    fun `the same text sent again later is a new message`() {
        assertNotNull(pipeline.onObservation(msg("Your account will be blocked today, share your screen on AnyDesk", "+919000000095")).eventId)
        now += 40_000
        assertNotNull(pipeline.onObservation(msg("Your account will be blocked today, share your screen on AnyDesk", "+919000000095")).eventId)
        assertEquals(2, store.eventsFor(listOf("tel:+919000000095")).size)
    }

    @Test
    fun `a resent scam message joins the same case without a second notice`() {
        val text = "Sir I am calling from SBI KYC department, your account will be blocked today, share your screen on AnyDesk to verify"
        val first = pipeline.onObservation(msg(text, "+919000000094"))
        assertNotNull(first.processed?.notice)
        repeat(3) {
            now += 40_000
            val again = pipeline.onObservation(msg(text, "+919000000094"))
            assertEquals(first.caseId, again.caseId)
            assertNull(again.processed?.notice)
        }
        assertEquals(4, store.eventsFor(listOf("tel:+919000000094")).size)
    }
}

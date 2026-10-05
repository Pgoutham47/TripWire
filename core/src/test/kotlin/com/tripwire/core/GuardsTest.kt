package com.tripwire.core

import com.tripwire.core.guard.AppConcern
import com.tripwire.core.guard.BankDirectory
import com.tripwire.core.guard.CollectRequestDetector
import com.tripwire.core.guard.FakeCreditDetector
import com.tripwire.core.guard.GuardAlert
import com.tripwire.core.guard.InstalledApp
import com.tripwire.core.guard.OtpDetector
import com.tripwire.core.guard.PhoneCheckup
import com.tripwire.core.guard.RecoveryDetector
import com.tripwire.core.ledger.InMemoryLedgerStore
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.pipeline.PipelineConfig
import com.tripwire.core.pipeline.TripwirePipeline
import com.tripwire.core.replay.ReplayHarness
import com.tripwire.core.script.ScriptPack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardsTest {
    private val pack = ScriptPack.bundled()
    private var now = ReplayHarness.DEFAULT_START
    private val store = InMemoryLedgerStore()
    private val pipeline = TripwirePipeline(pack, store, null, { PipelineConfig(language = "en", allyName = "Anil") }, { now })

    private fun sms(text: String, sender: String, phone: String? = null) = Observation(
        type = EventType.MESSAGE, app = "com.google.android.apps.messaging", timestamp = now, source = "notification",
        text = text, senderName = sender, senderPhone = phone,
    )

    private fun call(phone: String, type: EventType = EventType.CALL_STARTED) = Observation(
        type = type, app = "com.whatsapp", timestamp = now, source = "call", senderName = phone, senderPhone = phone,
    )

    // ------------------------------------------------------------------ detectors

    @Test
    fun `one-time codes are recognised in English and Hindi, order numbers are not`() {
        assertTrue(OtpDetector.looksLikeOtp("482913 is your OTP for login. Do not share it with anyone."))
        assertTrue(OtpDetector.looksLikeOtp("Your verification code is 7731"))
        assertTrue(OtpDetector.looksLikeOtp("आपका ओटीपी 4821 है"))
        assertFalse(OtpDetector.looksLikeOtp("Your order 482913 has shipped"))
        assertFalse(OtpDetector.looksLikeOtp("Your OTP will arrive shortly"))
    }

    @Test
    fun `a credited SMS from a mobile number is fake, from a bank sender it is not`() {
        val text = "Rs.5,000.00 credited to your A/c XX1234 by UPI ref 401234567890. -SBI"
        assertTrue(FakeCreditDetector.matches(text, "+91 98765 43210"))
        assertTrue(FakeCreditDetector.matches("$text Sent by mistake, please return", "Ravi"), "a saved name is a person, not a bank")
        assertFalse(FakeCreditDetector.matches(text, "VM-SBIINB"))
        assertFalse(FakeCreditDetector.matches(text, "JD-SBIUPI-S"))
        assertFalse(FakeCreditDetector.matches("Call me back please", "+91 98765 43210"))
    }

    @Test
    fun `collect requests are read from PhonePe and Google Pay style notifications`() {
        val phonepe = CollectRequestDetector.parse("PhonePe", "Ramesh Kumar has requested ₹2,000")!!
        assertEquals("Ramesh Kumar", phonepe.requester)
        assertEquals(200_000, phonepe.amount!!.paise)
        val gpay = CollectRequestDetector.parse("Payment request from Ravi Sharma", "₹500 · Tap to pay")!!
        assertEquals("Ravi Sharma", gpay.requester)
        assertNull(CollectRequestDetector.parse("PhonePe", "You received ₹500 from Ravi"))
    }

    @Test
    fun `offers to recover lost money are recognised, ordinary refunds are not`() {
        assertTrue(RecoveryDetector.matches("We are from cyber cell. We can recover your lost money, pay a small processing fee"))
        assertTrue(RecoveryDetector.matches("आपका ठगी वाला पैसा वापस दिला देंगे"))
        assertFalse(RecoveryDetector.matches("Refund for your Amazon order has been initiated"))
    }

    // ------------------------------------------------------------------ pipeline guards

    @Test
    fun `a code arriving during a stranger's call raises the OTP guard once`() {
        pipeline.onObservation(call("+919000000301"))
        now += 60_000
        val alert = pipeline.otpArrived(now)
        assertNotNull(alert)
        assertEquals(GuardAlert.Kind.OTP, alert.kind)
        now += 30_000
        assertNull(pipeline.otpArrived(now), "a second code within two minutes is not alerted again")
    }

    @Test
    fun `a code with no stranger in touch raises nothing`() {
        assertNull(pipeline.otpArrived(now))
        pipeline.onObservation(call("+919000000302"))
        now += 60_000
        pipeline.onObservation(call("+919000000302", EventType.CALL_ENDED))
        now += 2 * 60 * 60_000
        assertNull(pipeline.otpArrived(now), "a call that ended hours ago does not count")
    }

    @Test
    fun `a fake credited SMS raises its guard once`() {
        val text = "Rs.5,000.00 credited to your A/c XX1234 by UPI. Sent by mistake, please return"
        val first = pipeline.onObservation(sms(text, "+91 98765 43210", "+919876543210"))
        assertEquals(GuardAlert.Kind.FAKE_CREDIT, first.processed!!.alert!!.kind)
        now += 60_000
        val second = pipeline.onObservation(sms("$text urgently", "+91 98765 43210", "+919876543210"))
        assertNull(second.processed!!.alert)
        val bank = pipeline.onObservation(sms("Rs.2,000.00 credited to your A/c XX1234 by UPI.", "VM-SBIINB"))
        assertNull(bank.processed?.alert)
    }

    @Test
    fun `a recovery offer raises its guard`() {
        val out = pipeline.onObservation(
            Observation(type = EventType.MESSAGE, app = "com.whatsapp", timestamp = now, source = "notification",
                text = "Cyber cell here. We can recover your lost money from the fraud, pay ₹2,000 processing fee",
                senderName = "+91 90000 00303", senderPhone = "+919000000303"),
        )
        assertEquals(GuardAlert.Kind.RECOVERY, out.processed!!.alert!!.kind)
    }

    @Test
    fun `a collect request from a stranger is explained, from a contact it is not`() {
        val req = Observation(type = EventType.COLLECT_REQUEST, app = "com.phonepe.app", timestamp = now, source = "notification",
            senderName = "Ramesh Kumar", upi = null)
        val alert = pipeline.collectRequestAlert(req)
        assertNotNull(alert)
        assertTrue(alert.body.startsWith("Ramesh Kumar is asking you to approve"), alert.body)
        assertNull(pipeline.collectRequestAlert(req), "the same request is explained once")
        assertNull(pipeline.collectRequestAlert(req.copy(senderName = "Mom", fromSavedContact = true)))
    }

    // ------------------------------------------------------------------ bank fraud lines

    @Test
    fun `the bank is found from the SMS sender ID, then from its name in the text`() {
        val banks = BankDirectory(pack)
        assertEquals("sbi", banks.identify("VM-SBIINB-S", null)?.id)
        assertEquals("hdfc", banks.identify("AD-HDFCBK", "Rs 500 debited")?.id)
        assertEquals("axis", banks.identify("JX-AXSBKT-T", null)?.id)
        assertEquals("canara", banks.identify("+91 98765 43210", "Rs.500 debited from A/c XX12 to VPA x@ybl. -Canara Bank")?.id)
        assertNull(banks.identify("+91 98765 43210", "Call me back"))
        assertTrue(banks.banks.all { it.source.startsWith("https://") && it.fraudLine.isNotBlank() })
    }

    // ------------------------------------------------------------------ phone checkup

    @Test
    fun `the checkup flags link-installed apps with dangerous access, not store apps`() {
        val checkup = PhoneCheckup(pack)
        val result = checkup.assess(
            listOf(
                InstalledApp("com.trade.satfin", "SATFIN Pro", "com.google.android.packageinstaller", false, readsSms = true),
                InstalledApp("com.whatsapp", "WhatsApp", "com.android.vending", false, readsSms = true, readsNotifications = true),
                InstalledApp("com.anydesk.anydeskandroid", "AnyDesk", "com.android.vending", false),
                InstalledApp("com.android.settings", "Settings", null, true, controlsScreen = true),
                InstalledApp("com.tripwire.app", "Tripwire", null, false),
                InstalledApp("com.example.notes", "My Notes", null, false),
            ),
            self = "com.tripwire.app",
        )
        val findings = result.findings
        assertEquals(4, result.checked)
        assertEquals(1, result.outsideStoreOnly, "an app only from outside a store is counted, not listed")
        assertEquals(listOf("com.trade.satfin", "com.anydesk.anydeskandroid"), findings.map { it.app.packageName })
        assertTrue(findings[0].severe)
        assertTrue(AppConcern.READS_SMS in findings[0].concerns)
        assertFalse(findings[1].severe)
        assertEquals(listOf(AppConcern.REMOTE_ACCESS), findings[1].concerns)
    }
}

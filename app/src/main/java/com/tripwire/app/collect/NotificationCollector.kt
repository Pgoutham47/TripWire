package com.tripwire.app.collect

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tripwire.app.engine.Guardian
import com.tripwire.app.graph
import com.tripwire.core.entity.EntityExtractor
import com.tripwire.core.model.EventType
import com.tripwire.core.model.Observation
import com.tripwire.core.parse.NotificationParser
import com.tripwire.core.parse.PaymentSmsParser

/**
 * Reads notifications from messaging apps (SIG-01..04) and call notifications (SIG-12).
 * Text from saved contacts is dropped here and never reaches the ledger (SIG-03, privacy rule 2).
 */
class NotificationCollector : NotificationListenerService() {

    private val seen = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 500
    }
    private val activeCalls = HashMap<String, Observation>()

    override fun onListenerConnected() {
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val graph = applicationContext.graph
        val pack = graph.pack
        val pkg = sbn.packageName
        val n = sbn.notification ?: return

        if (n.category == Notification.CATEGORY_CALL || isCallNotification(n)) {
            onCall(sbn, n)
            return
        }
        if (pack.apps.messaging.none { it.packageName == pkg }) return
        // "Missed voice call" is not a message; the ringing call was already recorded above (SIG-12).
        if (n.category == CATEGORY_MISSED_CALL) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // The app's own status notices ("Checking for new messages", "WhatsApp Web is active")
        // are ongoing; a chat message never is. Calls were handled above.
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            Guardian.debug { "notification skipped: $pkg status notice" }
            return
        }

        val extras = n.extras ?: return
        val messages = latestMessages(extras)
        Guardian.debug { "notification from $pkg: style=${extras.getString(Notification.EXTRA_TEMPLATE)?.substringAfterLast('$')} messages=${messages.size} group=${extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)}" }
        // Each message keeps the time it was sent, so a re-post of an old message can be told apart
        // from the same text sent again (a scammer repeating a demand is a new message).
        val postedAt = n.`when`.takeIf { it > 0 } ?: sbn.postTime
        val raws = if (messages.isNotEmpty()) {
            messages.map { m -> raw(pkg, extras, m.text, m.sender, false) to m.time }
        } else {
            listOf(raw(pkg, extras, extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(), null, false) to postedAt)
        }
        for ((raw, sentAt) in raws) {
            val parsed = NotificationParser.parse(raw)
            // Android 15+ redacts notifications it classes as sensitive for untrusted listeners
            // (PRD 15.3). The placeholder carries no sender or text, so it is dropped, not stored.
            if (isRedacted(raw.text)) {
                redactedCount++
                Guardian.debug { "notification redacted by the system ($redactedCount so far)" }
                continue
            }
            if (parsed == null) {
                Guardian.debug { "notification not parsed" }
                continue
            }
            val key = "$pkg|${parsed.groupName}|${parsed.senderName}|${parsed.text}|$sentAt"
            if (seen.put(key, Unit) != null) {
                Guardian.debug { "notification skipped: message already seen" }
                continue // the same message re-posted with the next one
            }

            val now = sentAt.coerceAtMost(System.currentTimeMillis())
            // Payment confirmations from bank short codes feed the evidence pack (SIG-14).
            if (isSmsApp(pkg) && PaymentSmsParser.parse(parsed.text, now) != null) {
                graph.guardian.submit(
                    Observation(EventType.PAYMENT_SMS, pkg, now, "notification", text = parsed.text, senderName = parsed.senderName),
                )
                continue
            }
            val contact = Contacts.isSavedContact(this, parsed.senderName, parsed.senderPhone)
            graph.guardian.submit(
                Observation(
                    type = if (parsed.isGroupAdd) EventType.GROUP_ADDED else EventType.MESSAGE,
                    app = pkg,
                    timestamp = now,
                    source = "notification",
                    // SIG-03: a saved contact's text is discarded before it leaves this method.
                    text = if (contact) null else parsed.text,
                    senderName = parsed.senderName,
                    senderPhone = parsed.senderPhone,
                    groupName = parsed.groupName,
                    fromSavedContact = contact,
                ),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val started = activeCalls.remove(sbn.key) ?: return
        applicationContext.graph.guardian.submit(started.copy(type = EventType.CALL_ENDED, timestamp = System.currentTimeMillis()))
    }

    /** WhatsApp, Telegram and dialer call notifications carry the caller in the title (SIG-12). */
    private fun onCall(sbn: StatusBarNotification, n: Notification) {
        if (activeCalls.containsKey(sbn.key)) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty()) return
        val phone = EntityExtractor.normalizePhone(title)
        val contact = Contacts.isSavedContact(this, title, phone)
        val obs = Observation(
            type = EventType.CALL_STARTED,
            app = sbn.packageName,
            timestamp = System.currentTimeMillis(),
            source = "call",
            senderName = title,
            senderPhone = phone,
            fromSavedContact = contact,
            isVideoCall = VIDEO.containsMatchIn(text) || VIDEO.containsMatchIn(title),
        )
        activeCalls[sbn.key] = obs
        applicationContext.graph.guardian.submit(obs)
    }

    private fun isCallNotification(n: Notification): Boolean {
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return false
        return CALL_TEXT.containsMatchIn(text) && n.flags and Notification.FLAG_ONGOING_EVENT != 0
    }

    private fun isSmsApp(pkg: String) = pkg in SMS_APPS

    private fun isRedacted(text: String?): Boolean {
        if (text == null) return false
        val placeholder = runCatching {
            val id = resources.getIdentifier("redacted_notification_message", "string", "android")
            if (id != 0) getString(id) else null
        }.getOrNull()
        return text == placeholder || REDACTED.any { text.equals(it, ignoreCase = true) }
    }

    private fun raw(pkg: String, extras: Bundle, text: String?, sender: String?, summary: Boolean) = NotificationParser.Raw(
        packageName = pkg,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = text,
        conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
        isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
        messagingSender = sender,
        isSummary = summary,
    )

    /**
     * MessagingStyle notifications repeat the thread's recent history. Only messages from the last
     * few minutes are new; older ones were seen before, or arrived while Tripwire was not running.
     */
    private fun latestMessages(extras: Bundle): List<StyledMessage> {
        @Suppress("DEPRECATION")
        val arr: Array<Parcelable> = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val now = System.currentTimeMillis()
        val cutoff = now - FRESH_MS
        return arr.takeLast(3).mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val time = b.getLong("time", 0L)
            if (time in 1 until cutoff) return@mapNotNull null
            val text = b.getCharSequence("text")?.toString() ?: return@mapNotNull null
            @Suppress("DEPRECATION")
            val person = b.getParcelable<android.app.Person>("sender_person")
            val sender = person?.name?.toString() ?: b.getCharSequence("sender")?.toString()
            StyledMessage(sender, text, if (time > 0) time else now)
        }
    }

    private data class StyledMessage(val sender: String?, val text: String, val time: Long)

    companion object {
        @Volatile var connected = false
            private set

        /** Notifications the system hid from Tripwire; shown on the home screen as a limitation. */
        @Volatile var redactedCount = 0
            private set

        private val REDACTED = setOf("Sensitive notification content hidden", "संवेदनशील सूचना की सामग्री छिपाई गई")

        /** Notification.CATEGORY_MISSED_CALL, added in API 30; the value is stable. */
        private const val CATEGORY_MISSED_CALL = "missed_call"
        private const val FRESH_MS = 5 * 60_000L
        private val CALL_TEXT = Regex("(?i)(ongoing|incoming|calling|voice call|video call|कॉल)")
        private val VIDEO = Regex("(?i)(video|वीडियो)")
        private val SMS_APPS = setOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.truecaller")
    }
}

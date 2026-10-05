package com.tripwire.app.collect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.tripwire.core.parse.NotificationParser

/**
 * Decides whether a sender is a saved contact (SIG-02). Messaging apps show saved contacts by
 * name and strangers by number (or "~ name"), so a name match or a number match both count.
 * Without contacts permission, only senders shown as a raw number count as strangers.
 */
object Contacts {
    fun granted(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun isSavedContact(context: Context, displayName: String, phone: String?): Boolean {
        if (!granted(context)) {
            // PRD 23.2 open question: the right default when contacts are refused. This follows SIG-02.
            return !NotificationParser.looksLikeRawNumber(displayName) && !displayName.trim().startsWith("~") && phone == null
        }
        if (phone != null && numberSaved(context, phone)) return true
        val name = displayName.removePrefix("~").trim()
        if (name.isEmpty() || NotificationParser.looksLikeRawNumber(name)) return false
        if (displayName.trim().startsWith("~")) return false // WhatsApp marks unsaved senders with ~
        return nameSaved(context, name)
    }

    private fun numberSaved(context: Context, phone: String): Boolean {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun nameSaved(context: Context, name: String): Boolean = runCatching {
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
            arrayOf(name),
            null,
        )?.use { it.count > 0 } ?: false
    }.getOrDefault(false)
}

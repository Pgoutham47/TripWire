package com.tripwire.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tripwire.core.engine.ThresholdOffsets
import com.tripwire.core.model.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Every user setting in one immutable snapshot (SET, ONB, PRD 13.1 Settings). */
data class TripwireSettings(
    val onboarded: Boolean = false,
    val consentGiven: Boolean = false,
    val language: String = "en",
    val speechOn: Boolean = true,
    val retentionDays: Int = 30,
    /** Protection paused until this time (SET-04); 0 when not paused. */
    val pausedUntil: Long = 0,
    /** Collectors switched off (SET-01), by event-type wire name. */
    val disabledCollectors: Set<String> = emptySet(),
    val watchOffset: Int = 0,
    val warnOffset: Int = 0,
    /** The "I already paid" shortcut stays pinned until this time (INT-08). */
    val paidShortcutUntil: Long = 0,
    /** Opt-ins; both off by default (PRD 15.1 rule 4, PRD 17). */
    val shareAnonymousPatterns: Boolean = false,
    val telemetry: Boolean = false,
    /** SHA-256 of the optional settings PIN (SET-06), or empty. */
    val pinHash: String = "",
    /** The user's own name, used in ally alerts and the complaint pack. */
    val ownerName: String = "",
) {
    val offsets get() = ThresholdOffsets(watchOffset, warnOffset)
    fun isPaused(now: Long) = pausedUntil > now
    val disabledTypes: Set<EventType> get() = disabledCollectors.mapNotNull { EventType.fromWire(it) }.toSet()
}

class SettingsStore(private val context: Context, scope: CoroutineScope) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val consent = booleanPreferencesKey("consent")
        val language = stringPreferencesKey("language")
        val speech = booleanPreferencesKey("speech")
        val retention = intPreferencesKey("retention")
        val pausedUntil = longPreferencesKey("paused_until")
        val disabled = stringSetPreferencesKey("disabled_collectors")
        val watchOffset = intPreferencesKey("watch_offset")
        val warnOffset = intPreferencesKey("warn_offset")
        val paidUntil = longPreferencesKey("paid_shortcut_until")
        val share = booleanPreferencesKey("share_patterns")
        val telemetry = booleanPreferencesKey("telemetry")
        val pin = stringPreferencesKey("pin_hash")
        val owner = stringPreferencesKey("owner_name")
    }

    private val _state = MutableStateFlow(runBlocking { read(context.dataStore.data.first()) })

    /** Always current; the pipeline reads it synchronously on every call. */
    val state: StateFlow<TripwireSettings> = _state
    val current: TripwireSettings get() = _state.value

    init {
        scope.launch { context.dataStore.data.map { read(it) }.collect { _state.value = it } }
    }

    suspend fun update(transform: (TripwireSettings) -> TripwireSettings) {
        val next = transform(current)
        _state.value = next
        context.dataStore.edit { p ->
            p[K.onboarded] = next.onboarded
            p[K.consent] = next.consentGiven
            p[K.language] = next.language
            p[K.speech] = next.speechOn
            p[K.retention] = next.retentionDays
            p[K.pausedUntil] = next.pausedUntil
            p[K.disabled] = next.disabledCollectors
            p[K.watchOffset] = next.watchOffset
            p[K.warnOffset] = next.warnOffset
            p[K.paidUntil] = next.paidShortcutUntil
            p[K.share] = next.shareAnonymousPatterns
            p[K.telemetry] = next.telemetry
            p[K.pin] = next.pinHash
            p[K.owner] = next.ownerName
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
        _state.value = TripwireSettings()
    }

    private fun read(p: Preferences) = TripwireSettings(
        onboarded = p[K.onboarded] ?: false,
        consentGiven = p[K.consent] ?: false,
        language = p[K.language] ?: "en",
        speechOn = p[K.speech] ?: true,
        retentionDays = p[K.retention] ?: 30,
        pausedUntil = p[K.pausedUntil] ?: 0,
        disabledCollectors = p[K.disabled] ?: emptySet(),
        watchOffset = p[K.watchOffset] ?: 0,
        warnOffset = p[K.warnOffset] ?: 0,
        paidShortcutUntil = p[K.paidUntil] ?: 0,
        shareAnonymousPatterns = p[K.share] ?: false,
        telemetry = p[K.telemetry] ?: false,
        pinHash = p[K.pin] ?: "",
        ownerName = p[K.owner] ?: "",
    )
}

package com.tripwire.app.intervene

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads warnings aloud with the phone's built-in voice (EXP-02). Uses the accessibility audio
 * stream so it is heard even in silent mode (EXP-04). Sarvam Edge voices are a later step (EXP-05).
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private var pending: Pair<String, String>? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            tts.setSpeechRate(0.9f)
            pending?.let { (text, lang) -> speak(text, lang) }
            pending = null
        }
    }

    fun speak(text: String, lang: String) {
        if (!ready) {
            pending = text to lang
            return
        }
        val locale = when (lang.substringBefore('-')) {
            "hi" -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tripwire-warning")
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }
}

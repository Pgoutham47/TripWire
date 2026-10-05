package com.tripwire.app

import com.tripwire.app.intervene.warningDateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.GregorianCalendar
import java.util.Locale

class WarningDateTest {
    private val at = GregorianCalendar(2026, 9, 4, 19, 18).time

    /** PRD 13.4: no English inside a Hindi warning, even on a phone set to English. */
    @Test
    fun hindiWarningOnAnEnglishPhoneHasHindiMonths() {
        val text = warningDateFormat("hi", Locale.US).format(at)
        assertEquals("4 अक्तू॰, 7:18 pm", text)
        assertFalse(text, "Oct" in text)
    }

    @Test
    fun matchingPhoneLanguageKeepsThePhoneFormat() {
        assertEquals("4 Oct, 7:18 PM", warningDateFormat("en", Locale.US).format(at))
    }

    @Test
    fun englishWarningOnAHindiPhoneUsesIndianEnglish() {
        assertEquals("4 Oct, 7:18 pm", warningDateFormat("en", Locale.forLanguageTag("hi-IN")).format(at))
    }
}

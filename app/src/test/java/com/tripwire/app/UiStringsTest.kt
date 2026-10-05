package com.tripwire.app

import com.tripwire.app.ui.Ui
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStringsTest {
    /** PRD 13.4: every string exists in every supported language. */
    @Test
    fun everyStringHasEveryLanguage() {
        val langs = Ui.languages.map { it.first }
        val missing = Ui.strings.flatMap { (k, v) -> langs.filter { v[it].isNullOrBlank() }.map { "$k:$it" } }
        assertTrue("missing: $missing", missing.isEmpty())
    }

    /** Placeholders must match across languages, or a translation would show a raw {name}. */
    @Test
    fun placeholdersMatchAcrossLanguages() {
        val ph = Regex("\\{[a-z]+\\}")
        val bad = Ui.strings.filter { (_, v) -> v.values.map { s -> ph.findAll(s).map { it.value }.toSet() }.toSet().size > 1 }.keys
        assertTrue("mismatched placeholders: $bad", bad.isEmpty())
    }
}

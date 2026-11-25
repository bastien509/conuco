package com.petiht.conuco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conuco ships in Spanish and English: every key must exist in both, with the same {placeholders}. */
class StringsTest {
    private val ph = Regex("""\{(\w+)}""")

    @Test fun everyKeyIsTranslated() {
        val missingEs = Strings.EN.keys - Strings.ES.keys
        val missingEn = Strings.ES.keys - Strings.EN.keys
        assertTrue("missing in Spanish: $missingEs", missingEs.isEmpty())
        assertTrue("missing in English: $missingEn", missingEn.isEmpty())
    }

    @Test fun placeholdersMatchAcrossLanguages() {
        val bad = Strings.EN.keys.filter { k ->
            ph.findAll(Strings.EN.getValue(k)).map { it.value }.toSet() != ph.findAll(Strings.ES[k] ?: "").map { it.value }.toSet()
        }
        assertTrue("placeholder mismatch: $bad", bad.isEmpty())
    }

    @Test fun fillsPlaceholdersAndFallsBackToKey() {
        Strings.lang = "es"
        assertEquals("Riega: faltan unos 7 mm", Strings.t("t_irrigate", "mm" to 7))
        Strings.lang = "en"
        assertEquals("Water: about 7 mm short", Strings.t("t_irrigate", "mm" to 7))
        assertEquals("no_such_key", Strings.t("no_such_key"))
    }
}

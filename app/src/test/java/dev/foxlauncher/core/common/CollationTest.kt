package dev.foxlauncher.core.common

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CollationTest {

    private val comparator = Collation.labelComparator(Locale.ENGLISH)

    @Test
    fun sortsCaseInsensitively() {
        val sorted = listOf("Zoom", "airbnb", "Books").sortedWith(comparator)
        assertEquals(listOf("airbnb", "Books", "Zoom"), sorted)
    }

    @Test
    fun accentedLabelsSortNextToTheirBaseLetter() {
        val sorted = listOf("Zalando", "Étoile", "Email").sortedWith(comparator)
        assertEquals(listOf("Email", "Étoile", "Zalando"), sorted)
    }

    @Test
    fun caseOnlyDifferencesAreOrderedButNeverEqual() {
        assertTrue(Collation.compareLabels("fox", "Fox", Locale.ENGLISH) != 0)
        val sorted = listOf("fox", "Fox").sortedWith(comparator)
        // Deterministic tie-break on the raw string: 'F' (0x46) before 'f' (0x66).
        assertEquals(listOf("Fox", "fox"), sorted)
    }

    @Test
    fun identicalLabelsCompareEqual() {
        assertEquals(0, Collation.compareLabels("Fox Launcher", "Fox Launcher", Locale.ENGLISH))
    }

    @Test
    fun digitsSortBeforeLetters() {
        val sorted = listOf("Maps", "1Password", "2048").sortedWith(comparator)
        assertEquals(listOf("1Password", "2048", "Maps"), sorted)
    }

    @Test
    fun emptyLabelSortsFirst() {
        val sorted = listOf("Fox", "").sortedWith(comparator)
        assertEquals(listOf("", "Fox"), sorted)
    }

    @Test
    fun localeChangesOrdering() {
        // In Swedish 'ä' is its own letter after 'z'; in English it collates with 'a'.
        assertTrue(Collation.compareLabels("äpple", "zebra", Locale.ENGLISH) < 0)
        assertTrue(Collation.compareLabels("äpple", "zebra", Locale.forLanguageTag("sv")) > 0)
    }
}

package dev.foxlauncher.core.apps

import dev.foxlauncher.core.apps.AppSearch.Rank
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AppSearchTest {

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(AppSearch.match("", "Maps"))
        assertTrue(AppSearch.match("   ", "Maps"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(AppSearch.match("MAPS", "maps"))
        assertTrue(AppSearch.match("yout", "YouTube"))
    }

    @Test
    fun matchingIgnoresDiacritics() {
        assertTrue(AppSearch.match("cafe", "Café"))
        assertTrue(AppSearch.match("café", "Cafe"))
        assertTrue(AppSearch.match("nandu", "Ñandú"))
    }

    @Test
    fun prefixOfTheLabelRanksFirst() {
        assertEquals(Rank.LABEL_PREFIX, AppSearch.rank("goo", "Google Maps"))
    }

    @Test
    fun prefixOfALaterWordRanksSecond() {
        assertEquals(Rank.WORD_PREFIX, AppSearch.rank("map", "Google Maps"))
        assertEquals(Rank.WORD_PREFIX, AppSearch.rank("drive", "Google-Drive"))
    }

    @Test
    fun substringRanksLast() {
        assertEquals(Rank.SUBSTRING, AppSearch.rank("tube", "YouTube"))
    }

    @Test
    fun noMatchIsNull() {
        assertNull(AppSearch.rank("xyz", "Maps"))
        assertFalse(AppSearch.match("xyz", "Maps"))
    }

    @Test
    fun queryWhitespaceIsTrimmed() {
        assertEquals(Rank.LABEL_PREFIX, AppSearch.rank(" ma ", "Maps"))
    }

    @Test
    fun multiWordQueryMatchesAsSubstring() {
        assertEquals(Rank.LABEL_PREFIX, AppSearch.rank("google m", "Google Maps"))
        assertTrue(AppSearch.match("le ma", "Google Maps"))
    }
}

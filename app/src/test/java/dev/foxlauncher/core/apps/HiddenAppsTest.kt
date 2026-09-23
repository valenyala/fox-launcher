package dev.foxlauncher.core.apps

import dev.foxlauncher.core.model.AppEntry
import kotlin.test.assertEquals
import org.junit.Test

class HiddenAppsTest {

    private val maps = app("Maps")
    private val bank = app("Bank Secret")
    private val workBank = app("Bank Secret", serial = 10)
    private val apps = listOf(bank, workBank, maps)

    @Test
    fun nothingHiddenKeepsEverything() {
        assertEquals(apps, applyHidden(apps, emptySet(), ""))
    }

    @Test
    fun hiddenAppsAreDropped() {
        assertEquals(listOf(workBank, maps), applyHidden(apps, setOf(bank.key), ""))
    }

    @Test
    fun hidingIsPerProfile() {
        // Same component, different user: hiding the personal copy leaves the work copy.
        assertEquals(listOf(workBank, maps), applyHidden(apps, setOf(bank.key), "ban"))
    }

    @Test
    fun partialQueryDoesNotRevealAHiddenApp() {
        assertEquals(emptyList(), applyHidden(listOf(bank), setOf(bank.key), "bank"))
    }

    @Test
    fun exactLabelRevealsAHiddenApp() {
        assertEquals(listOf(bank), applyHidden(listOf(bank), setOf(bank.key), "Bank Secret"))
    }

    @Test
    fun exactMatchIgnoresCaseAccentsAndSurroundingSpace() {
        val cafe = app("Café Notes")
        assertEquals(listOf(cafe), applyHidden(listOf(cafe), setOf(cafe.key), "  cafe notes "))
    }

    @Test
    fun blankQueryNeverReveals() {
        val blank = app(" ")
        assertEquals(emptyList(), applyHidden(listOf(blank), setOf(blank.key), "  "))
    }

    private fun app(label: String, serial: Long = 0) = AppEntry(
        component = "pkg.${label.filter(Char::isLetter).lowercase()}/.Main",
        userSerial = serial,
        label = label,
        packageName = "pkg.${label.filter(Char::isLetter).lowercase()}",
        isWorkProfile = serial != 0L,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}

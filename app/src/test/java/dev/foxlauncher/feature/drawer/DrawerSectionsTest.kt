package dev.foxlauncher.feature.drawer

import dev.foxlauncher.core.apps.sortEntries
import dev.foxlauncher.core.common.Collation
import dev.foxlauncher.core.model.AppEntry
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class DrawerSectionsTest {

    private val apps = sortEntries(
        listOf("Maps", "Mail", "Éclair", "Email", "2048", "YouTube", "Tube Map", "Camera").map(::app),
        Collation.labelComparator(Locale.ENGLISH),
    )

    @Test
    fun blankQueryGroupsByFirstLetter() {
        val sections = buildSections(apps, "")
        assertEquals(listOf("#", "C", "E", "M", "T", "Y"), sections.map { it.letter })
        assertEquals(listOf("Éclair", "Email"), sections.single { it.letter == "E" }.apps.map { it.label })
    }

    @Test
    fun everyAppAppearsExactlyOnceWithoutAQuery() {
        assertEquals(apps, buildSections(apps, "  ").flatMap { it.apps })
    }

    @Test
    fun queryYieldsOneHeaderlessSectionOfMatches() {
        val sections = buildSections(apps, "ma")
        assertEquals(1, sections.size)
        assertEquals("", sections[0].letter)
        // "Email" contains "ma" only as a substring, so it ranks after both prefix kinds.
        assertEquals(listOf("Mail", "Maps", "Tube Map", "Email"), sections[0].apps.map { it.label })
    }

    @Test
    fun betterRanksComeFirst() {
        // "Tube Map" matches as a label prefix, "YouTube" only as a substring.
        assertEquals(listOf("Tube Map", "YouTube"), buildSections(apps, "tube").single().apps.map { it.label })
    }

    @Test
    fun noMatchesMeansNoSections() {
        assertTrue(buildSections(apps, "zzz").isEmpty())
    }

    @Test
    fun sectionLetterStripsAccentsAndBucketsNonLetters() {
        assertEquals("E", sectionLetter("éclair"))
        assertEquals("#", sectionLetter("1Password"))
        assertEquals("#", sectionLetter(""))
        assertEquals("Ж", sectionLetter("журнал"))
    }

    private fun app(label: String) = AppEntry(
        component = "pkg.${label.hashCode()}/.Main",
        userSerial = 0,
        label = label,
        packageName = "pkg.${label.hashCode()}",
        isWorkProfile = false,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}

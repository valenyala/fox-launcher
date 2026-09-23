package dev.foxlauncher.core.apps

import dev.foxlauncher.core.common.Collation
import dev.foxlauncher.core.model.AppEntry
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

class AppSortTest {

    private val comparator = Collation.labelComparator(Locale.ENGLISH)

    @Test
    fun sortsByCollatedLabel() {
        val sorted = sortEntries(listOf(app("Zoom"), app("éclair"), app("Maps"), app("apps")), comparator)
        assertEquals(listOf("apps", "éclair", "Maps", "Zoom"), sorted.map { it.label })
    }

    @Test
    fun sameLabelIsOrderedByComponentThenUser() {
        val work = app("Chrome", component = "com.android.chrome/.Main", serial = 10)
        val personal = app("Chrome", component = "com.android.chrome/.Main", serial = 0)
        val other = app("Chrome", component = "com.beta.chrome/.Main", serial = 0)

        val sorted = sortEntries(listOf(other, work, personal), comparator)

        assertEquals(listOf(personal, work, other), sorted)
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList(), sortEntries(emptyList(), comparator))
    }

    private fun app(label: String, component: String = "pkg.$label/.Main", serial: Long = 0) = AppEntry(
        component = component,
        userSerial = serial,
        label = label,
        packageName = component.substringBefore('/'),
        isWorkProfile = serial != 0L,
        isSuspended = false,
        isAvailable = true,
        versionCode = 1,
    )
}

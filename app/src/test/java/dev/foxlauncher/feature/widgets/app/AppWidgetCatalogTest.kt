package dev.foxlauncher.feature.widgets.app

import dev.foxlauncher.core.common.Collation
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

class AppWidgetCatalogTest {

    private val order = Collation.labelComparator(Locale.ENGLISH)

    private fun option(pkg: String, app: String, label: String, serial: Long = 0, provider: String = "$pkg/.$label") = AppWidgetOption(
        provider = provider,
        userSerial = serial,
        packageName = pkg,
        appLabel = app,
        label = label,
        description = null,
        isWorkProfile = serial != 0L,
        size = ProviderSize(minWidth = 100, minHeight = 100),
    )

    private val clock = option("com.clock", "Clock", "Analog")
    private val clockDigital = option("com.clock", "Clock", "digital")
    private val calendar = option("com.cal", "calendar", "Month")
    private val calendarWork = option("com.cal", "calendar", "Agenda", serial = 10)
    private val ebook = option("com.ebook", "Éclair", "Shelf")

    @Test
    fun widgetsGroupByAppAndProfileInLabelOrder() {
        val groups = AppWidgetCatalog.group(listOf(clockDigital, calendarWork, ebook, clock, calendar), order)

        assertEquals(
            listOf("com.cal|0", "com.cal|10", "com.clock|0", "com.ebook|0"),
            groups.map { it.key },
            "case- and accent-insensitive app labels; personal before work for the same app",
        )
        assertEquals(listOf(listOf(calendar), listOf(calendarWork), listOf(clock, clockDigital), listOf(ebook)), groups.map { it.widgets })
    }

    @Test
    fun aGroupCarriesItsAppLabelAndProfile() {
        val work = AppWidgetCatalog.group(listOf(calendarWork), order).single()

        assertEquals("calendar", work.appLabel)
        assertEquals(true, work.isWorkProfile)
        assertEquals(10L, work.userSerial)
    }

    @Test
    fun twoAppsWithTheSameLabelStayApart() {
        val a = option("com.a", "Notes", "Note")
        val b = option("com.b", "Notes", "Note")

        assertEquals(listOf("com.a|0", "com.b|0"), AppWidgetCatalog.group(listOf(b, a), order).map { it.key })
    }

    @Test
    fun nothingInstalledMeansNoGroups() {
        assertEquals(emptyList(), AppWidgetCatalog.group(emptyList(), order))
    }
}

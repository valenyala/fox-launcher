package dev.foxlauncher.core.apps.iconpack

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AppFilterTest {

    private fun item(component: String, drawable: String) = XmlTag("item", mapOf("component" to component, "drawable" to drawable))

    @Test
    fun itemsMapComponentsToDrawables() {
        val filter = AppFilter.parse(
            sequenceOf(
                XmlTag("resources", emptyMap()),
                item("ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}", "chrome"),
                item("ComponentInfo{org.example/.Main}", "example"),
            ),
        )

        assertEquals("chrome", filter.drawableFor("com.android.chrome/com.google.android.apps.chrome.Main"))
        assertEquals("example", filter.drawableFor("org.example/org.example.Main"))
    }

    @Test
    fun anUnmappedActivityFallsBackToItsPackagesFirstEntry() {
        val filter = AppFilter.parse(
            sequenceOf(
                item("ComponentInfo{org.example/org.example.Old}", "example"),
                item("ComponentInfo{org.example/org.example.Other}", "example_other"),
            ),
        )

        assertEquals("example", filter.drawableFor("org.example/org.example.Renamed"))
        assertNull(filter.drawableFor("org.unknown/org.unknown.Main"))
    }

    @Test
    fun theFirstEntryForAComponentWins() {
        val filter = AppFilter.parse(
            sequenceOf(
                item("ComponentInfo{a.b/a.b.Main}", "first"),
                item("ComponentInfo{a.b/a.b.Main}", "second"),
            ),
        )

        assertEquals("first", filter.drawableFor("a.b/a.b.Main"))
    }

    @Test
    fun malformedItemsAreSkipped() {
        val filter = AppFilter.parse(
            sequenceOf(
                item("a.b/a.b.Main", "no_wrapper"),
                item("ComponentInfo{}", "empty"),
                item("ComponentInfo{a.b/}", "no_class"),
                item("ComponentInfo{c.d/c.d.Main}", "  "),
                XmlTag("item", mapOf("drawable" to "no_component")),
                item(" ComponentInfo{e.f/e.f.Main} ", " ef "),
            ),
        )

        assertEquals(mapOf("e.f/e.f.Main" to "ef"), filter.drawables)
    }

    @Test
    fun framesAndScaleAreRead() {
        val filter = AppFilter.parse(
            sequenceOf(
                XmlTag("iconback", mapOf("img2" to "back2", "img1" to "back1", "other" to "x")),
                XmlTag("iconmask", mapOf("img1" to "mask")),
                XmlTag("iconupon", mapOf("img1" to "upon")),
                XmlTag("scale", mapOf("factor" to "0.75")),
            ),
        )

        assertEquals(listOf("back1", "back2"), filter.backs)
        assertEquals(listOf("mask"), filter.masks)
        assertEquals(listOf("upon"), filter.upons)
        assertEquals(0.75f, filter.scale)
        assertTrue(filter.framesOthers)
    }

    @Test
    fun aBadScaleKeepsTheDefault() {
        listOf("0", "-1", "3", "abc").forEach { factor ->
            val filter = AppFilter.parse(sequenceOf(XmlTag("scale", mapOf("factor" to factor))))
            assertEquals(AppFilter.DEFAULT_SCALE, filter.scale, factor)
        }
        assertFalse(AppFilter.parse(emptySequence()).framesOthers)
    }

    @Test
    fun onlyTheKeptPackagesItemsStay() {
        val filter = AppFilter.parse(
            sequenceOf(
                item("ComponentInfo{a.b/a.b.Main}", "ab"),
                item("ComponentInfo{c.d/c.d.Main}", "cd"),
                XmlTag("iconback", mapOf("img1" to "back")),
            ),
            keep = { it == "a.b" },
        )

        assertEquals(mapOf("a.b/a.b.Main" to "ab"), filter.drawables)
        assertEquals(listOf("back"), filter.backs)
    }
}

package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType
import dev.foxlauncher.core.model.HomeContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ShortcutPinsTest {

    private val chrome = ShortcutOwner("com.android.chrome", 0)
    private val workChrome = ShortcutOwner("com.android.chrome", 10)

    @Test
    fun shortcutsAnywhereAreGroupedByPackageAndProfile() {
        val rows = listOf(
            shortcut(1, "com.android.chrome", "incognito", serial = 0),
            shortcut(2, "com.android.chrome", "new_tab", serial = 0, container = HomeContainer.FOLDER),
            shortcut(3, "com.android.chrome", "incognito", serial = 10, container = HomeContainer.DOCK),
            app(4),
        )

        assertEquals(
            mapOf(chrome to setOf("incognito", "new_tab"), workChrome to setOf("incognito")),
            pinnedShortcutIds(rows),
        )
    }

    @Test
    fun anUnchangedSetNeedsNoPinning() {
        val pins = mapOf(chrome to setOf("incognito"))

        assertTrue(pinChanges(pins, pins).isEmpty())
    }

    @Test
    fun anAddedShortcutRepinsItsOwnerWithTheWholeSet() {
        val before = mapOf(chrome to setOf("incognito"))
        val after = mapOf(chrome to setOf("incognito", "new_tab"))

        assertEquals(mapOf(chrome to setOf("incognito", "new_tab")), pinChanges(before, after))
    }

    @Test
    fun anOwnersLastShortcutLeavingUnpinsEverything() {
        val before = mapOf(chrome to setOf("incognito"), workChrome to setOf("new_tab"))
        val after = mapOf(workChrome to setOf("new_tab"))

        assertEquals(mapOf(chrome to emptySet<String>()), pinChanges(before, after))
    }

    private fun shortcut(id: Long, pkg: String, shortcutId: String, serial: Long, container: HomeContainer = HomeContainer.PAGE) =
        HomeItemEntity(
            id = id,
            container = container,
            page = 0,
            col = 0,
            row = 0,
            type = HomeItemType.SHORTCUT,
            component = "$pkg/",
            userSerial = serial,
            shortcutId = shortcutId,
            createdAt = 0,
        )

    private fun app(id: Long) = HomeItemEntity(
        id = id,
        container = HomeContainer.PAGE,
        page = 0,
        col = 1,
        row = 0,
        type = HomeItemType.APP,
        component = "com.android.chrome/.Main",
        userSerial = 0,
        createdAt = 0,
    )
}

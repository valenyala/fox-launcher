package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.FolderEntity
import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomePosition
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class HomeLayoutMapperTest {

    @Test
    fun emptyDatabaseIsOneEmptyPageAndAnEmptyDock() {
        val layout = HomeLayoutMapper.toLayout(emptyList(), emptyList())
        assertEquals(1, layout.pages.size)
        assertTrue(layout.pages[0].items.isEmpty())
        assertTrue(layout.dock.items.isEmpty())
    }

    @Test
    fun itemsLandOnTheirPageAndGapsBecomeEmptyPages() {
        val rows = listOf(app(1, page = 0, col = 2, row = 1), app(2, page = 2, col = 0, row = 0))
        val layout = HomeLayoutMapper.toLayout(rows, emptyList())
        assertEquals(listOf(0, 1, 2), layout.pages.map { it.index })
        assertEquals(listOf(1L), layout.pages[0].items.map { it.id })
        assertTrue(layout.pages[1].items.isEmpty())
        assertEquals(listOf(2L), layout.pages[2].items.map { it.id })
    }

    @Test
    fun pageItemsAreOrderedByRowThenColumn() {
        val rows = listOf(app(1, col = 3, row = 1), app(2, col = 0, row = 1), app(3, col = 4, row = 0))
        assertEquals(listOf(3L, 2L, 1L), HomeLayoutMapper.toLayout(rows, emptyList()).pages[0].items.map { it.id })
    }

    @Test
    fun dockItemsGoToTheDock() {
        val rows = listOf(app(1, container = HomeContainer.DOCK, col = 1), app(2, col = 0))
        val layout = HomeLayoutMapper.toLayout(rows, emptyList())
        assertEquals(listOf(1L), layout.dock.items.map { it.id })
        assertEquals(listOf(2L), layout.pages[0].items.map { it.id })
    }

    @Test
    fun folderCollectsItsChildrenInRankOrderAndTakesItsTitleFromTheFolderTable() {
        val rows = listOf(
            HomeItemEntity(id = 10, container = HomeContainer.PAGE, page = 0, col = 0, row = 0,
                type = HomeItemType.FOLDER, folderId = 7, title = "stale", createdAt = 0),
            app(11, container = HomeContainer.FOLDER, col = 1, folderId = 7),
            app(12, container = HomeContainer.FOLDER, col = 0, folderId = 7),
        )
        val folder = HomeLayoutMapper.toLayout(rows, listOf(FolderEntity(7, "Games"))).pages[0].items.single()

        assertIs<HomeItem.Folder>(folder)
        assertEquals("Games", folder.title)
        assertEquals(listOf(12L, 11L), folder.items.map { it.id })
    }

    @Test
    fun folderChildrenNeverAppearAtTopLevel() {
        val rows = listOf(app(11, container = HomeContainer.FOLDER, folderId = 99))
        val layout = HomeLayoutMapper.toLayout(rows, emptyList())
        assertTrue(layout.pages.all { it.items.isEmpty() } && layout.dock.items.isEmpty())
    }

    @Test
    fun malformedRowsAreDropped() {
        val rows = listOf(
            HomeItemEntity(id = 1, container = HomeContainer.PAGE, page = 0, col = 0, row = 0,
                type = HomeItemType.APP, component = null, userSerial = 0, createdAt = 0),
            HomeItemEntity(id = 2, container = HomeContainer.PAGE, page = 0, col = 1, row = 0,
                type = HomeItemType.SHORTCUT, component = "pkg/", userSerial = 0, shortcutId = null, createdAt = 0),
            HomeItemEntity(id = 3, container = HomeContainer.PAGE, page = 0, col = 2, row = 0,
                type = HomeItemType.APP_WIDGET, appWidgetId = null, createdAt = 0),
        )
        assertTrue(HomeLayoutMapper.toLayout(rows, emptyList()).pages[0].items.isEmpty())
    }

    @Test
    fun everyItemKindSurvivesARoundTrip() {
        val pos = HomePosition(HomeContainer.PAGE, page = 1, col = 2, row = 3, spanX = 2, spanY = 2)
        val items = listOf(
            HomeItem.App(1, pos, "com.a/.Main", 0),
            HomeItem.Shortcut(2, pos.copy(col = 0), "com.b", "compose", 10, "New message"),
            HomeItem.AppWidget(3, pos.copy(row = 0), 42),
            HomeItem.NativeWidget(4, pos.copy(row = 1, col = 0), "ram_gauge"),
        )
        val layout = HomeLayoutMapper.toLayout(items.map { HomeLayoutMapper.toEntity(it, createdAt = 5) }, emptyList())
        assertEquals(items.sortedBy { it.id }, layout.pages[1].items.sortedBy { it.id })
    }

    @Test
    fun shortcutRowsStoreThePackageAsAComponentPrefix() {
        val shortcut = HomeItem.Shortcut(2, HomePosition(HomeContainer.PAGE, 0, 0, 0), "com.b", "id", 0, "t")
        assertEquals("com.b/", HomeLayoutMapper.toEntity(shortcut, 0).component)
    }

    @Test
    fun orphansAreAppRowsMissingFromTheCatalog() {
        val rows = listOf(app(1, component = "com.a/.Main"), app(2, component = "com.gone/.Main"), app(3, serial = 10))
        val installed = setOf("com.a/.Main|0", "com.x/.Main|0")
        assertEquals(listOf(2L, 3L), orphanedAppItemIds(rows, installed))
    }

    @Test
    fun anEmptyCatalogOrphansNothing() {
        assertEquals(emptyList(), orphanedAppItemIds(listOf(app(1)), emptySet()))
    }

    @Test
    fun contiguousPagesNeedNoRenumbering() {
        assertEquals(emptyMap(), pageRenumbering(listOf(0, 0, 1, 2)))
    }

    @Test
    fun anEmptyMiddlePageIsClosedUp() {
        assertEquals(mapOf(2 to 1, 3 to 2), pageRenumbering(listOf(0, 2, 3, 3)))
    }

    @Test
    fun anEmptyFirstPageIsClosedUp() {
        assertEquals(mapOf(1 to 0, 4 to 1), pageRenumbering(listOf(4, 1)))
    }

    @Test
    fun noPagesNeedNoRenumbering() {
        assertEquals(emptyMap(), pageRenumbering(emptyList()))
    }

    private fun app(
        id: Long,
        container: HomeContainer = HomeContainer.PAGE,
        page: Int = 0,
        col: Int = 0,
        row: Int = 0,
        folderId: Long? = null,
        component: String = "com.x/.Main",
        serial: Long = 0,
    ) = HomeItemEntity(
        id = id,
        container = container,
        page = page,
        col = col,
        row = row,
        type = HomeItemType.APP,
        component = component,
        userSerial = serial,
        folderId = folderId,
        createdAt = 0,
    )
}

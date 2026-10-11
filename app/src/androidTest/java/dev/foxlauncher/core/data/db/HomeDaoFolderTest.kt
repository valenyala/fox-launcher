package dev.foxlauncher.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.feature.home.folderCleanup
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The folder transactions behind drag and drop (task 2.4), on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class HomeDaoFolderTest {

    private lateinit var db: FoxDatabase
    private lateinit var dao: HomeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoxDatabase::class.java).build()
        dao = db.homeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createFolderPutsATileInTheTargetsPlaceWithBothInside() = runBlocking {
        val target = dao.insert(app(HomeContainer.PAGE, page = 1, col = 2, row = 3))
        val dragged = dao.insert(app(HomeContainer.DOCK, page = 0, col = 4, row = 0))

        val folderId = assertNotNull(dao.createFolder(target, dragged, title = "", createdAt = 0))

        val rows = dao.getAll()
        val tile = rows.single { it.type == HomeItemType.FOLDER }
        assertEquals(listOf(HomeContainer.PAGE, 1, 2, 3, folderId), listOf(tile.container, tile.page, tile.col, tile.row, tile.folderId))
        val children = dao.getFolderChildren(folderId).sortedBy { it.col }
        assertEquals(listOf(target to 0, dragged to 1), children.map { it.id to it.col })
    }

    @Test
    fun createFolderWithAMissingRowChangesNothing() = runBlocking {
        val target = dao.insert(app(HomeContainer.PAGE, page = 0, col = 0, row = 0))
        val before = dao.getAll()

        assertNull(dao.createFolder(target, draggedId = 999, title = "", createdAt = 0))

        assertEquals(before, dao.getAll())
    }

    @Test
    fun addToFolderAppendsAfterTheLastChild() = runBlocking {
        val a = dao.insert(app(HomeContainer.PAGE, page = 0, col = 0, row = 0))
        val b = dao.insert(app(HomeContainer.PAGE, page = 0, col = 1, row = 0))
        val c = dao.insert(app(HomeContainer.PAGE, page = 0, col = 2, row = 0))
        val folderId = assertNotNull(dao.createFolder(a, b, title = "", createdAt = 0))
        val tile = dao.getAll().single { it.type == HomeItemType.FOLDER }.id

        assertTrue(dao.addToFolder(tile, c))

        assertEquals(listOf(a to 0, b to 1, c to 2), dao.getFolderChildren(folderId).sortedBy { it.col }.map { it.id to it.col })
    }

    @Test
    fun deleteFolderWithItemsRemovesTileChildrenAndFolder() = runBlocking {
        val a = dao.insert(app(HomeContainer.PAGE, page = 0, col = 0, row = 0))
        val b = dao.insert(app(HomeContainer.PAGE, page = 0, col = 1, row = 0))
        val other = dao.insert(app(HomeContainer.PAGE, page = 0, col = 2, row = 0))
        val folderId = assertNotNull(dao.createFolder(a, b, title = "", createdAt = 0))
        val tile = dao.getAll().single { it.type == HomeItemType.FOLDER }.id

        dao.deleteFolderWithItems(tile)

        assertEquals(listOf(other), dao.getAll().map { it.id })
        assertTrue(dao.getFolderChildren(folderId).isEmpty())
    }

    @Test
    fun insertIntoNewFolderAddsTheNewItemAfterTheTarget() = runBlocking {
        val target = dao.insert(app(HomeContainer.DOCK, page = 0, col = 2, row = 0))

        val folderId = assertNotNull(dao.insertIntoNewFolder(app(HomeContainer.PAGE, 0, 0, 0), target, title = "", createdAt = 0))

        val children = dao.getFolderChildren(folderId).sortedBy { it.col }
        assertEquals(2, children.size)
        assertEquals(target, children.first().id)
        val tile = dao.getAll().single { it.type == HomeItemType.FOLDER }
        assertEquals(listOf(HomeContainer.DOCK, 2), listOf(tile.container, tile.col))
    }

    @Test
    fun insertIntoNewFolderKeepsNothingWhenTheTargetIsGone() = runBlocking {
        assertNull(dao.insertIntoNewFolder(app(HomeContainer.PAGE, 0, 0, 0), targetId = 999, title = "", createdAt = 0))

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun insertIntoFolderKeepsNothingWhenTheFolderIsGone() = runBlocking {
        assertEquals(false, dao.insertIntoFolder(app(HomeContainer.PAGE, 0, 0, 0), folderItemId = 999))

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun aFolderDownToOneItemCollapsesIntoItsPlace() = runBlocking {
        val a = dao.insert(app(HomeContainer.PAGE, page = 0, col = 0, row = 0))
        val b = dao.insert(app(HomeContainer.PAGE, page = 0, col = 1, row = 0))
        val folderId = assertNotNull(dao.createFolder(a, b, title = "", createdAt = 0))
        val tile = dao.getAll().single { it.type == HomeItemType.FOLDER }
        dao.deleteByIds(listOf(b))

        val cleanup = folderCleanup(dao.getAll())
        dao.applyFolderCleanup(cleanup.promoted, cleanup.deletedItemIds, cleanup.deletedFolderIds)

        val last = dao.getAll().single()
        assertEquals(listOf<Any?>(a, HomeContainer.PAGE, tile.page, tile.col, tile.row, null), last.run { listOf(id, container, page, col, row, this.folderId) })
        assertTrue(dao.getFolderChildren(folderId).isEmpty())
    }

    private fun app(container: HomeContainer, page: Int, col: Int, row: Int) = HomeItemEntity(
        container = container,
        page = page,
        col = col,
        row = row,
        type = HomeItemType.APP,
        component = "com.example/.App$page$col$row",
        userSerial = 0,
        createdAt = 0,
    )
}

package dev.foxlauncher.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Wallpaper sources and entries (tasks 4.1, 4.7, 4.9), on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class WallpaperDaoTest {

    private lateinit var db: FoxDatabase
    private lateinit var dao: WallpaperDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoxDatabase::class.java).build()
        dao = db.wallpaperDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun onlyEnabledSourcesFeedTheRotation() = runBlocking {
        val on = dao.insertSource(source("On"))
        val off = dao.insertSource(source("Off").copy(enabled = false))
        dao.applyScan(on, listOf(entry("a")), emptyList(), emptyList())
        dao.applyScan(off, listOf(entry("b")), emptyList(), emptyList())

        assertEquals(listOf("a"), dao.observeRotationEntries().first().map { it.displayName })
    }

    @Test
    fun removingASourceRemovesItsEntries() = runBlocking {
        val id = dao.insertSource(source("Folder"))
        dao.applyScan(id, listOf(entry("a"), entry("b")), emptyList(), emptyList())

        dao.deleteSource(id)

        assertTrue(dao.getEntries(id).isEmpty())
    }

    @Test
    fun aScanAddsChangesAndRemovesInOneGo() = runBlocking {
        val id = dao.insertSource(source("Folder"))
        dao.applyScan(id, listOf(entry("a"), entry("b")), emptyList(), emptyList())
        val (a, b) = dao.getEntries(id).sortedBy { it.displayName }

        dao.applyScan(id, listOf(entry("c")), listOf(a.copy(sizeBytes = 999)), listOf(b.id))

        val after = dao.getEntries(id).associateBy { it.displayName }
        assertEquals(setOf("a", "c"), after.keys)
        assertEquals(999L, after.getValue("a").sizeBytes)
        assertEquals(a.id, after.getValue("a").id)
    }

    @Test
    fun showingAnEntryRecordsTheTimeAndForgivesItsFailures() = runBlocking {
        val id = dao.insertSource(source("Folder"))
        dao.applyScan(id, listOf(entry("a")), emptyList(), emptyList())
        val entryId = dao.getEntries(id).single().id

        dao.markFailed(entryId)
        dao.markFailed(entryId)
        assertEquals(2, dao.getEntry(entryId)?.failedCount)
        dao.markShown(entryId, now = 1234)

        val shown = dao.getEntry(entryId)
        assertEquals(1234L, shown?.lastShownAt)
        assertEquals(0, shown?.failedCount)
    }

    @Test
    fun theBuiltInSourceIsFoundByType() = runBlocking {
        assertNull(dao.getSourceOfType(WallpaperSourceType.BUILT_IN))
        dao.insertSource(source("Folder"))
        val builtIn = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.BUILT_IN, displayName = "Fox"))

        assertEquals(builtIn, dao.getSourceOfType(WallpaperSourceType.BUILT_IN)?.id)
    }

    @Test
    fun needsReauthCanBeSetAndCleared() = runBlocking {
        val id = dao.insertSource(source("Folder"))

        dao.setNeedsReauth(id, true)
        assertTrue(dao.getSources().single().needsReauth)
        dao.setNeedsReauth(id, false)

        assertEquals(false, dao.getSources().single().needsReauth)
    }

    @Test
    fun turningASourceOffTakesItsEntriesOutOfTheRotation() = runBlocking {
        val id = dao.insertSource(source("Folder"))
        dao.applyScan(id, listOf(entry("a")), emptyList(), emptyList())

        dao.setSourceEnabled(id, false)
        assertTrue(dao.observeRotationEntries().first().isEmpty())
        dao.setSourceEnabled(id, true)

        assertEquals(listOf("a"), dao.observeRotationEntries().first().map { it.displayName })
    }

    @Test
    fun aFolderThatLostItsAccessLeavesTheRotation() = runBlocking {
        val readable = dao.insertSource(source("Readable"))
        val lost = dao.insertSource(source("Lost"))
        dao.applyScan(readable, listOf(entry("a")), emptyList(), emptyList())
        dao.applyScan(lost, listOf(entry("b")), emptyList(), emptyList())

        dao.setNeedsReauth(lost, true)

        assertEquals(listOf("a"), dao.observeRotationEntries().first().map { it.displayName })
    }

    @Test
    fun withNothingElseToShowTheBuiltInPicturesStepIn() = runBlocking {
        val builtIn = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.BUILT_IN, displayName = "Fox", enabled = false))
        val lost = dao.insertSource(source("Lost"))
        dao.applyScan(builtIn, listOf(entry("fox")), emptyList(), emptyList())
        dao.applyScan(lost, listOf(entry("b")), emptyList(), emptyList())
        assertEquals(listOf("b"), dao.observeRotationEntries().first().map { it.displayName })

        dao.setNeedsReauth(lost, true)

        assertEquals(listOf("fox"), dao.observeRotationEntries().first().map { it.displayName }, "turned off, but nothing else is left")
    }

    @Test
    fun unreadableEntriesCountAsFailedThreeTimes() = runBlocking {
        val id = dao.insertSource(source("Folder"))
        dao.applyScan(id, listOf(entry("a"), entry("b")), emptyList(), emptyList())
        val (a, b) = dao.getEntries(id).sortedBy { it.displayName }

        dao.markUnreadable(listOf(a.id))

        assertEquals(WallpaperEntryEntity.MAX_FAILURES, dao.getEntry(a.id)?.failedCount)
        assertEquals(0, dao.getEntry(b.id)?.failedCount)
    }

    @Test
    fun entriesAreCountedPerSourceEnabledOrNot() = runBlocking {
        val on = dao.insertSource(source("On"))
        val off = dao.insertSource(source("Off").copy(enabled = false))
        dao.insertSource(source("Empty"))
        dao.applyScan(on, listOf(entry("a"), entry("b")), emptyList(), emptyList())
        dao.applyScan(off, listOf(entry("c")), emptyList(), emptyList())

        assertEquals(setOf(SourceEntryCount(on, 2), SourceEntryCount(off, 1)), dao.observeEntryCounts().first().toSet())
    }

    @Test
    fun aFolderPickedAgainGetsItsTreeAndAccessBack() = runBlocking {
        val id = dao.insertSource(source("Old"))
        dao.setNeedsReauth(id, true)

        dao.reauthorizeSource(id, "content://tree/New", "New")

        val source = dao.getSource(id)
        assertEquals("content://tree/New", source?.uri)
        assertEquals("New", source?.displayName)
        assertEquals(false, source?.needsReauth)
    }

    private fun source(name: String) =
        WallpaperSourceEntity(type = WallpaperSourceType.FOLDER_TREE, uri = "content://tree/$name", displayName = name)

    private fun entry(name: String) = WallpaperEntryEntity(sourceId = 0, uri = "content://tree/x/$name", displayName = name)
}

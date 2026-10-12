package dev.foxlauncher.feature.wallpaper.source

import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScanDiffTest {

    private val a = entry(1, "a.jpg", size = 100, modified = 10)
    private val b = entry(2, "b.jpg", size = 200, modified = 20)

    @Test
    fun aFirstScanAddsEverything() {
        val found = listOf(image("a.jpg"), image("b.jpg"))

        val diff = diffScan(emptyList(), found)

        assertEquals(found, diff.added)
        assertTrue(diff.changed.isEmpty() && diff.removedIds.isEmpty())
    }

    @Test
    fun anUnchangedFolderChangesNothing() {
        val diff = diffScan(listOf(a, b), listOf(image("a.jpg", 100, 10), image("b.jpg", 200, 20)))

        assertTrue(diff.isEmpty)
    }

    @Test
    fun aNewImageIsAddedAndAVanishedOneRemoved() {
        val diff = diffScan(listOf(a, b), listOf(image("a.jpg", 100, 10), image("c.jpg")))

        assertEquals(listOf(image("c.jpg")), diff.added)
        assertEquals(listOf(2L), diff.removedIds)
        assertTrue(diff.changed.isEmpty())
    }

    @Test
    fun aChangedFileKeepsItsIdAndHistoryButGetsAFreshStart() {
        val shownAndFailing = a.copy(lastShownAt = 99, failedCount = 2)

        val diff = diffScan(listOf(shownAndFailing), listOf(image("a.jpg", size = 150, modified = 30)))

        assertEquals(listOf(shownAndFailing.copy(sizeBytes = 150, lastModified = 30, failedCount = 0)), diff.changed)
        assertTrue(diff.added.isEmpty() && diff.removedIds.isEmpty())
    }

    @Test
    fun aRenameIsAChange() {
        val diff = diffScan(listOf(a), listOf(FoundImage(uri = a.uri, displayName = "renamed.jpg", sizeBytes = 100, lastModified = 10)))

        assertEquals("renamed.jpg", diff.changed.single().displayName)
    }

    @Test
    fun anEmptiedFolderRemovesEverything() {
        assertEquals(listOf(1L, 2L), diffScan(listOf(a, b), emptyList()).removedIds)
    }

    @Test
    fun aDuplicateInTheScanCountsOnce() {
        val diff = diffScan(emptyList(), listOf(image("a.jpg"), image("a.jpg")))

        assertEquals(1, diff.added.size)
    }

    @Test
    fun aFoundImageBecomesAnEntryOfItsSource() {
        val entry = image("a.jpg", 100, 10).toEntry(sourceId = 7)

        assertEquals(WallpaperEntryEntity(sourceId = 7, uri = "content://tree/a.jpg", displayName = "a.jpg", sizeBytes = 100, lastModified = 10), entry)
    }

    private fun image(name: String, size: Long = 0, modified: Long = 0) =
        FoundImage(uri = "content://tree/$name", displayName = name, sizeBytes = size, lastModified = modified)

    private fun entry(id: Long, name: String, size: Long, modified: Long) =
        WallpaperEntryEntity(id = id, sourceId = 1, uri = "content://tree/$name", displayName = name, sizeBytes = size, lastModified = modified)
}

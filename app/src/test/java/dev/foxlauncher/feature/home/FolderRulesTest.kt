package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType
import dev.foxlauncher.core.model.HomeContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FolderRulesTest {

    @Test
    fun columnsGrowWithTheItemCount() {
        assertEquals(listOf(2, 2, 3, 3, 4, 4), listOf(1, 4, 5, 9, 10, 30).map(::folderColumns))
    }

    @Test
    fun movingForwardShiftsTheOthersBack() {
        assertEquals(listOf(2L, 3L, 1L, 4L), moveToRank(listOf(1, 2, 3, 4), id = 1, rank = 2))
    }

    @Test
    fun movingBackwardShiftsTheOthersForward() {
        assertEquals(listOf(4L, 1L, 2L, 3L), moveToRank(listOf(1, 2, 3, 4), id = 4, rank = 0))
    }

    @Test
    fun aRankPastTheEndIsTheLastPlace() {
        assertEquals(listOf(2L, 3L, 1L), moveToRank(listOf(1, 2, 3), id = 1, rank = 99))
        assertEquals(listOf(3L, 1L, 2L), moveToRank(listOf(1, 2, 3), id = 3, rank = -5))
    }

    @Test
    fun anUnknownIdLeavesTheOrderAlone() {
        assertEquals(listOf(1L, 2L), moveToRank(listOf(1, 2), id = 9, rank = 0))
    }

    @Test
    fun foldersWithTwoOrMoreItemsStay() {
        val rows = listOf(tile(id = 10, folderId = 1), child(1, folderId = 1), child(2, folderId = 1))

        assertTrue(folderCleanup(rows).isEmpty)
    }

    @Test
    fun anEmptyFolderIsDeleted() {
        val cleanup = folderCleanup(listOf(tile(id = 10, folderId = 1)))

        assertEquals(FolderCleanup(deletedItemIds = listOf(10), deletedFolderIds = listOf(1)), cleanup)
    }

    @Test
    fun aFolderDownToOneItemIsReplacedByItInItsPlace() {
        val last = child(1, folderId = 1, rank = 3)
        val cleanup = folderCleanup(listOf(tile(id = 10, folderId = 1, container = HomeContainer.DOCK, col = 2), last))

        val promoted = cleanup.promoted.single()
        assertEquals(listOf<Any?>(1L, HomeContainer.DOCK, 0, 2, 0, null), promoted.run { listOf(id, container, page, col, row, folderId) })
        assertEquals(listOf(10L), cleanup.deletedItemIds)
        assertEquals(listOf(1L), cleanup.deletedFolderIds)
    }

    private fun tile(id: Long, folderId: Long, container: HomeContainer = HomeContainer.PAGE, col: Int = 1) =
        HomeItemEntity(id = id, container = container, page = 0, col = col, row = 0, type = HomeItemType.FOLDER, folderId = folderId, createdAt = 0)

    private fun child(id: Long, folderId: Long, rank: Int = 0) = HomeItemEntity(
        id = id,
        container = HomeContainer.FOLDER,
        page = 0,
        col = rank,
        row = 0,
        type = HomeItemType.APP,
        component = "com.x/.A$id",
        userSerial = 0,
        folderId = folderId,
        createdAt = 0,
    )
}

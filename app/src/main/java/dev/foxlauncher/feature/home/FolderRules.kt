package dev.foxlauncher.feature.home

import dev.foxlauncher.core.data.db.HomeItemEntity
import dev.foxlauncher.core.data.db.HomeItemType
import dev.foxlauncher.core.model.HomeContainer

/**
 * Columns of an open folder's grid (task 2.5), as Launcher3 does: 2 wide up to 4 items, 3 wide
 * up to 9, then 4 wide.
 */
fun folderColumns(itemCount: Int): Int = when {
    itemCount <= 4 -> 2
    itemCount <= 9 -> 3
    else -> 4
}

/** [ids] in folder order with [id] moved to [rank], clamped into range. Unchanged when [id] is absent. */
fun moveToRank(ids: List<Long>, id: Long, rank: Int): List<Long> {
    if (id !in ids) return ids
    val rest = ids - id
    val at = rank.coerceIn(0, rest.size)
    return rest.subList(0, at) + id + rest.subList(at, rest.size)
}

/** The writes [folderCleanup] asks for; all empty when every folder still has two items or more. */
data class FolderCleanup(
    /** Last items of collapsing folders, moved into their folder's place. */
    val promoted: List<HomeItemEntity> = emptyList(),
    val deletedItemIds: List<Long> = emptyList(),
    val deletedFolderIds: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = promoted.isEmpty() && deletedItemIds.isEmpty() && deletedFolderIds.isEmpty()
}

/**
 * Folders that no longer earn their tile (task 2.5): an empty one goes away, and one down to a
 * single item is replaced by that item, in the tile's place.
 */
fun folderCleanup(rows: List<HomeItemEntity>): FolderCleanup {
    val children = rows.filter { it.container == HomeContainer.FOLDER && it.folderId != null }.groupBy { it.folderId }
    val promoted = mutableListOf<HomeItemEntity>()
    val deletedItems = mutableListOf<Long>()
    val deletedFolders = mutableListOf<Long>()
    for (tile in rows) {
        if (tile.type != HomeItemType.FOLDER || tile.container == HomeContainer.FOLDER) continue
        val folderId = tile.folderId ?: continue
        val inside = children[folderId].orEmpty()
        if (inside.size > 1) continue
        inside.singleOrNull()?.let { last ->
            promoted += last.copy(
                container = tile.container,
                page = tile.page,
                col = tile.col,
                row = tile.row,
                spanX = 1,
                spanY = 1,
                folderId = null,
            )
        }
        deletedItems += tile.id
        deletedFolders += folderId
    }
    return FolderCleanup(promoted, deletedItems, deletedFolders)
}

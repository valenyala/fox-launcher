package dev.foxlauncher.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.foxlauncher.core.model.HomeContainer
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomeDao {

    @Query("SELECT * FROM home_items")
    abstract fun observeAll(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM folders")
    abstract fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM home_items")
    abstract suspend fun getAll(): List<HomeItemEntity>

    @Insert
    abstract suspend fun insert(item: HomeItemEntity): Long

    @Update
    abstract suspend fun update(item: HomeItemEntity)

    @Update
    abstract suspend fun updateAll(items: List<HomeItemEntity>)

    @Delete
    abstract suspend fun delete(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    /** Every item of [packageName] in profile [userSerial]. Prefix match on `<package>/`, not LIKE: `_` is a LIKE wildcard. */
    @Query(
        "DELETE FROM home_items WHERE userSerial = :userSerial " +
            "AND substr(component, 1, length(:packageName) + 1) = :packageName || '/'",
    )
    abstract suspend fun deleteByPackage(packageName: String, userSerial: Long)

    /** Repositions several items atomically, so a drag never leaves two items in one cell. */
    @Transaction
    open suspend fun moveItems(items: List<HomeItemEntity>) {
        updateAll(items)
    }

    @Query("UPDATE home_items SET backgroundOpacity = :opacity WHERE id = :id")
    abstract suspend fun setBackgroundOpacity(id: Long, opacity: Int?)

    @Query("SELECT MAX(page) FROM home_items WHERE container = 'PAGE'")
    abstract suspend fun maxPage(): Int?

    @Query("SELECT COUNT(*) FROM home_items")
    abstract suspend fun count(): Int

    /**
     * Inserts [items] only while the home is empty, so the first-run layout (task 2.7) never lands
     * on a home that already has something. Returns whether it inserted.
     */
    @Transaction
    open suspend fun insertIntoEmptyHome(items: List<HomeItemEntity>): Boolean {
        if (count() > 0) return false
        items.forEach { insert(it) }
        return true
    }

    @Query("SELECT * FROM home_items WHERE id = :id")
    abstract suspend fun getById(id: Long): HomeItemEntity?

    @Query("SELECT * FROM home_items WHERE container = 'FOLDER' AND folderId = :folderId")
    abstract suspend fun getFolderChildren(folderId: Long): List<HomeItemEntity>

    @Query("DELETE FROM home_items WHERE container = 'FOLDER' AND folderId = :folderId")
    abstract suspend fun deleteFolderChildren(folderId: Long)

    @Insert
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    abstract suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: Long)

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    abstract suspend fun deleteFolders(folderIds: List<Long>)

    /** Collapses and deletes folders in one go (see `folderCleanup`). */
    @Transaction
    open suspend fun applyFolderCleanup(promoted: List<HomeItemEntity>, deletedItemIds: List<Long>, deletedFolderIds: List<Long>) {
        deleteByIds(deletedItemIds)
        updateAll(promoted)
        deleteFolders(deletedFolderIds)
    }

    /**
     * Puts a new folder tile where [targetId] is and moves [targetId] then [draggedId] into it.
     * Returns the new folder's id, or null (and changes nothing) when either row is gone.
     */
    @Transaction
    open suspend fun createFolder(targetId: Long, draggedId: Long, title: String, createdAt: Long): Long? {
        val target = getById(targetId) ?: return null
        val dragged = getById(draggedId) ?: return null
        val folderId = insertFolder(FolderEntity(title = title))
        insert(
            HomeItemEntity(
                container = target.container,
                page = target.page,
                col = target.col,
                row = target.row,
                type = HomeItemType.FOLDER,
                folderId = folderId,
                createdAt = createdAt,
            ),
        )
        updateAll(listOf(target.intoFolder(folderId, rank = 0), dragged.intoFolder(folderId, rank = 1)))
        return folderId
    }

    /** Inserts [item] and merges it with [targetId] into a new folder. Keeps nothing if [targetId] is gone. */
    @Transaction
    open suspend fun insertIntoNewFolder(item: HomeItemEntity, targetId: Long, title: String, createdAt: Long): Long? {
        val id = insert(item)
        return createFolder(targetId, id, title, createdAt) ?: run {
            deleteByIds(listOf(id))
            null
        }
    }

    /** Inserts [item] straight into the folder whose tile is [folderItemId]. Keeps nothing if it is gone. */
    @Transaction
    open suspend fun insertIntoFolder(item: HomeItemEntity, folderItemId: Long): Boolean {
        val id = insert(item)
        return addToFolder(folderItemId, id).also { added -> if (!added) deleteByIds(listOf(id)) }
    }

    /** Appends [itemId] to the folder whose tile is [folderItemId]. False when either row is gone. */
    @Transaction
    open suspend fun addToFolder(folderItemId: Long, itemId: Long): Boolean {
        val folderId = getById(folderItemId)?.folderId ?: return false
        val item = getById(itemId) ?: return false
        val rank = (getFolderChildren(folderId).maxOfOrNull { it.col } ?: -1) + 1
        update(item.intoFolder(folderId, rank))
        return true
    }

    /** Deletes the folder tile [folderItemId], everything inside it and its folder row. */
    @Transaction
    open suspend fun deleteFolderWithItems(folderItemId: Long) {
        getById(folderItemId)?.folderId?.let { folderId ->
            deleteFolderChildren(folderId)
            deleteFolder(folderId)
        }
        deleteByIds(listOf(folderItemId))
    }

    /** Folder children are 1×1, keep their rank in `col`, and ignore page and row. */
    private fun HomeItemEntity.intoFolder(folderId: Long, rank: Int) = copy(
        container = HomeContainer.FOLDER,
        folderId = folderId,
        page = 0,
        col = rank,
        row = 0,
        spanX = 1,
        spanY = 1,
    )
}

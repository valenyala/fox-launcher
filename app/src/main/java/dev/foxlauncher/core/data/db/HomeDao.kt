package dev.foxlauncher.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT MAX(page) FROM home_items WHERE container = 'PAGE'")
    abstract suspend fun maxPage(): Int?

    @Insert
    abstract suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    abstract suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: Long)
}

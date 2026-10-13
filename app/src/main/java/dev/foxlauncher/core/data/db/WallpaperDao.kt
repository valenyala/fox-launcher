package dev.foxlauncher.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WallpaperDao {

    @Query("SELECT * FROM wallpaper_sources ORDER BY id")
    abstract fun observeSources(): Flow<List<WallpaperSourceEntity>>

    @Query("SELECT * FROM wallpaper_sources ORDER BY id")
    abstract suspend fun getSources(): List<WallpaperSourceEntity>

    @Query("SELECT * FROM wallpaper_sources WHERE id = :id")
    abstract suspend fun getSource(id: Long): WallpaperSourceEntity?

    @Query("SELECT * FROM wallpaper_sources WHERE type = :type LIMIT 1")
    abstract suspend fun getSourceOfType(type: WallpaperSourceType): WallpaperSourceEntity?

    @Insert
    abstract suspend fun insertSource(source: WallpaperSourceEntity): Long

    @Query("UPDATE wallpaper_sources SET needsReauth = :needsReauth WHERE id = :sourceId")
    abstract suspend fun setNeedsReauth(sourceId: Long, needsReauth: Boolean)

    @Query("UPDATE wallpaper_sources SET enabled = :enabled WHERE id = :sourceId")
    abstract suspend fun setSourceEnabled(sourceId: Long, enabled: Boolean)

    /** The folder was picked again (task 4.7): its tree, possibly a new one, and access are back. */
    @Query("UPDATE wallpaper_sources SET uri = :uri, displayName = :displayName, needsReauth = 0 WHERE id = :sourceId")
    abstract suspend fun reauthorizeSource(sourceId: Long, uri: String, displayName: String)

    /** How many images each source has, enabled or not; a source with none is missing. */
    @Query("SELECT sourceId, COUNT(*) AS count FROM wallpaper_entries GROUP BY sourceId")
    abstract fun observeEntryCounts(): Flow<List<SourceEntryCount>>

    /** Removes the source and, by cascade, its entries. */
    @Query("DELETE FROM wallpaper_sources WHERE id = :sourceId")
    abstract suspend fun deleteSource(sourceId: Long)

    /** Every entry of every enabled source, in a stable order. */
    @Query(
        "SELECT e.* FROM wallpaper_entries e JOIN wallpaper_sources s ON s.id = e.sourceId " +
            "WHERE s.enabled = 1 ORDER BY e.sourceId, e.displayName, e.id",
    )
    abstract fun observeEnabledEntries(): Flow<List<WallpaperEntryEntity>>

    @Query("SELECT * FROM wallpaper_entries WHERE sourceId = :sourceId")
    abstract suspend fun getEntries(sourceId: Long): List<WallpaperEntryEntity>

    @Query("SELECT * FROM wallpaper_entries WHERE id = :id")
    abstract suspend fun getEntry(id: Long): WallpaperEntryEntity?

    @Upsert
    abstract suspend fun upsertAll(entries: List<WallpaperEntryEntity>)

    @Update
    abstract suspend fun updateAll(entries: List<WallpaperEntryEntity>)

    @Query("DELETE FROM wallpaper_entries WHERE id IN (:ids)")
    abstract suspend fun deleteEntries(ids: List<Long>)

    @Query("DELETE FROM wallpaper_entries WHERE sourceId = :sourceId")
    abstract suspend fun deleteBySource(sourceId: Long)

    @Query("UPDATE wallpaper_entries SET lastShownAt = :now, failedCount = 0 WHERE id = :id")
    abstract suspend fun markShown(id: Long, now: Long)

    @Query("UPDATE wallpaper_entries SET failedCount = failedCount + 1 WHERE id = :id")
    abstract suspend fun markFailed(id: Long)

    /** Applies one scan of [sourceId] atomically: new images in, changed ones updated, vanished ones out. */
    @Transaction
    open suspend fun applyScan(sourceId: Long, added: List<WallpaperEntryEntity>, changed: List<WallpaperEntryEntity>, removedIds: List<Long>) {
        if (removedIds.isNotEmpty()) deleteEntries(removedIds)
        if (changed.isNotEmpty()) updateAll(changed)
        if (added.isNotEmpty()) upsertAll(added.map { it.copy(sourceId = sourceId) })
    }
}

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

    /**
     * The entries the rotation walks, in a stable order: those of enabled sources that can still be
     * read (a folder that lost its access is left out, task 4.9). When that leaves nothing, the
     * built-in pictures step in even if turned off, so the wallpaper never goes blank.
     */
    @Query(
        "SELECT e.* FROM wallpaper_entries e JOIN wallpaper_sources s ON s.id = e.sourceId " +
            "WHERE (s.enabled = 1 AND s.needsReauth = 0) " +
            "OR (s.type = 'BUILT_IN' AND NOT EXISTS (SELECT 1 FROM wallpaper_entries e2 " +
            "JOIN wallpaper_sources s2 ON s2.id = e2.sourceId WHERE s2.enabled = 1 AND s2.needsReauth = 0)) " +
            "ORDER BY e.sourceId, e.displayName, e.id",
    )
    abstract fun observeRotationEntries(): Flow<List<WallpaperEntryEntity>>

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

    /** Entries that cannot be read at all (their access is gone): skipped like three failures (task 4.9). */
    @Query("UPDATE wallpaper_entries SET failedCount = :failures WHERE id IN (:ids)")
    abstract suspend fun markUnreadable(ids: List<Long>, failures: Int = WallpaperEntryEntity.MAX_FAILURES)

    /** Applies one scan of [sourceId] atomically: new images in, changed ones updated, vanished ones out. */
    @Transaction
    open suspend fun applyScan(sourceId: Long, added: List<WallpaperEntryEntity>, changed: List<WallpaperEntryEntity>, removedIds: List<Long>) {
        if (removedIds.isNotEmpty()) deleteEntries(removedIds)
        if (changed.isNotEmpty()) updateAll(changed)
        if (added.isNotEmpty()) upsertAll(added.map { it.copy(sourceId = sourceId) })
    }
}

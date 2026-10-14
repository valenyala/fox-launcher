package dev.foxlauncher.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.foxlauncher.core.model.WallpaperFraming

/** Where a wallpaper source's images come from (task 4.1). */
enum class WallpaperSourceType {
    /** A folder the user picked (`ACTION_OPEN_DOCUMENT_TREE`); [WallpaperSourceEntity.uri] is the tree. */
    FOLDER_TREE,

    /** Images picked one by one; each entry carries its own persisted URI. */
    PICKED_IMAGES,

    /** The wallpapers shipped in `res/raw`, so rotation works with no setup. */
    BUILT_IN,
}

@Entity(tableName = "wallpaper_sources")
data class WallpaperSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: WallpaperSourceType,
    /** The folder tree for [WallpaperSourceType.FOLDER_TREE]; null otherwise. */
    val uri: String? = null,
    val displayName: String,
    val enabled: Boolean = true,
    /** The persisted permission is gone: the user must pick the folder again (task 4.9). */
    val needsReauth: Boolean = false,
)

/** One image of a source. Rotation goes through entries, never through files directly. */
@Entity(
    tableName = "wallpaper_entries",
    foreignKeys = [
        ForeignKey(
            entity = WallpaperSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sourceId"), Index(value = ["sourceId", "uri"], unique = true)],
)
data class WallpaperEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long = 0,
    val lastModified: Long = 0,
    val lastShownAt: Long? = null,
    /** Failed loads in a row; the entry is skipped after [MAX_FAILURES] (task 4.9). */
    val failedCount: Int = 0,
    /** Which part of the image shows ([WallpaperFraming], ADR-26); the defaults centre it. */
    @ColumnInfo(defaultValue = "0.5") val focusX: Float = 0.5f,
    @ColumnInfo(defaultValue = "0.5") val focusY: Float = 0.5f,
    @ColumnInfo(defaultValue = "1.0") val zoom: Float = 1f,
) {
    companion object {
        /** Failed loads in a row after which the rotation skips an entry. */
        const val MAX_FAILURES = 3
    }
}

/** Which part of the image shows. */
val WallpaperEntryEntity.framing: WallpaperFraming get() = WallpaperFraming(focusX, focusY, zoom)

/** How many images a source has (task 4.7). */
data class SourceEntryCount(val sourceId: Long, val count: Int)

package dev.foxlauncher.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.foxlauncher.core.model.HomeContainer

/** Persistence discriminator for [HomeItemEntity]; which of the nullable columns are set depends on it. */
enum class HomeItemType { APP, SHORTCUT, FOLDER, APP_WIDGET, NATIVE_WIDGET }

/**
 * One row per thing on the home screen, the dock or inside a folder (see CLAUDE.md, Persistence
 * shape). A FOLDER-typed row is the folder's tile; its children are rows with
 * `container = FOLDER` and the same [folderId], ordered by [col].
 */
@Entity(
    tableName = "home_items",
    indices = [Index("container", "page"), Index("folderId")],
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: HomeContainer,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val type: HomeItemType,
    /** `ComponentName.flattenToString()`; for shortcuts `<package>/` when no activity is known. */
    val component: String? = null,
    val userSerial: Long? = null,
    val shortcutId: String? = null,
    val appWidgetId: Int? = null,
    val nativeWidgetKind: String? = null,
    val folderId: Long? = null,
    val title: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
)

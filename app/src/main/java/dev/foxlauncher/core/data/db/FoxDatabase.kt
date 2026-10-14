package dev.foxlauncher.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

/**
 * The launcher database. Every schema change bumps [VERSION], ships a migration and commits the
 * exported JSON under `app/schemas/`.
 */
@Database(
    entities = [HomeItemEntity::class, FolderEntity::class, WallpaperSourceEntity::class, WallpaperEntryEntity::class],
    version = FoxDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = FoxDatabase.DropKeyValue::class),
        // v2 → v3: wallpaper sources and entries (task 4.1), new tables only.
        AutoMigration(from = 2, to = 3),
        // v3 → v4: each wallpaper entry's framing (ADR-26), columns with defaults.
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class FoxDatabase : RoomDatabase() {

    abstract fun homeDao(): HomeDao

    abstract fun wallpaperDao(): WallpaperDao

    /** v1 → v2: the Phase 0 placeholder `key_value` table had no users; home tables are added. */
    @DeleteTable(tableName = "key_value")
    class DropKeyValue : AutoMigrationSpec

    companion object {
        const val VERSION: Int = 4
        const val NAME: String = "fox.db"
    }
}

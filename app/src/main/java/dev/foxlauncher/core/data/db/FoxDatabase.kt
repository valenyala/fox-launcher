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
    entities = [HomeItemEntity::class, FolderEntity::class],
    version = FoxDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = FoxDatabase.DropKeyValue::class)],
)
abstract class FoxDatabase : RoomDatabase() {

    abstract fun homeDao(): HomeDao

    /** v1 → v2: the Phase 0 placeholder `key_value` table had no users; home tables are added. */
    @DeleteTable(tableName = "key_value")
    class DropKeyValue : AutoMigrationSpec

    companion object {
        const val VERSION: Int = 2
        const val NAME: String = "fox.db"
    }
}

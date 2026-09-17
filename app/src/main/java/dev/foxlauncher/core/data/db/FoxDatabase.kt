package dev.foxlauncher.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The launcher database. Every schema change bumps [VERSION], ships a migration and commits the
 * exported JSON under `app/schemas/`.
 */
@Database(
    entities = [KeyValueEntity::class],
    version = FoxDatabase.VERSION,
    exportSchema = true,
)
abstract class FoxDatabase : RoomDatabase() {

    abstract fun keyValueDao(): KeyValueDao

    companion object {
        const val VERSION: Int = 1
        const val NAME: String = "fox.db"
    }
}

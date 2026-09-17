package dev.foxlauncher.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Placeholder row so the database has at least one entity at version 1. Phase 2 adds the real
 * tables (home items, folders); this one stays only if something needs it, otherwise it goes with
 * a migration.
 */
@Entity(tableName = "key_value")
data class KeyValueEntity(
    @PrimaryKey val key: String,
    val value: String,
)

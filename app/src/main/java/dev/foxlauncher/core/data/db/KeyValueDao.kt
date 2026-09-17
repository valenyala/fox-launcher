package dev.foxlauncher.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyValueDao {

    @Query("SELECT value FROM key_value WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM key_value WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(row: KeyValueEntity)

    @Query("DELETE FROM key_value WHERE `key` = :key")
    suspend fun delete(key: String)
}

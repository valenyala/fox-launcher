package dev.foxlauncher.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dev.foxlauncher.core.common.IoDispatcher

/**
 * Typed access to the Preferences DataStore. Reads are cold [Flow]s, writes are `suspend` and land
 * on IO. Structured settings (gesture map, widget config) arrive in later phases as single
 * JSON-serialized keys.
 */
@Singleton
class FoxSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    /** True until the launcher has completed its first successful start as the default home app. */
    val isFirstRun: Flow<Boolean> =
        dataStore.data.map { it[Keys.IS_FIRST_RUN] ?: DEFAULT_IS_FIRST_RUN }

    suspend fun setFirstRun(firstRun: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.IS_FIRST_RUN] = firstRun }
        Unit
    }

    private object Keys {
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
    }

    companion object {
        const val DEFAULT_IS_FIRST_RUN: Boolean = true
    }
}

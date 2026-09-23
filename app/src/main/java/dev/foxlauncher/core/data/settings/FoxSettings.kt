package dev.foxlauncher.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** Mask shape id for adaptive icons (`IconShape.id`); unknown ids fall back to the default shape. */
    val iconShapeId: Flow<String?> = dataStore.data.map { it[Keys.ICON_SHAPE] }

    /** Icon edge length in dp, shared by home and drawer. */
    val iconSizeDp: Flow<Int> = dataStore.data.map { it[Keys.ICON_SIZE_DP] ?: DEFAULT_ICON_SIZE_DP }

    /** Columns in the app drawer grid. */
    val drawerColumns: Flow<Int> = dataStore.data.map { it[Keys.DRAWER_COLUMNS] ?: DEFAULT_DRAWER_COLUMNS }

    private object Keys {
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
    }

    companion object {
        const val DEFAULT_IS_FIRST_RUN: Boolean = true
        const val DEFAULT_ICON_SIZE_DP: Int = 56
        const val DEFAULT_DRAWER_COLUMNS: Int = 5
    }
}

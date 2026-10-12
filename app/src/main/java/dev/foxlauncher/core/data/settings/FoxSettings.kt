package dev.foxlauncher.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType

/**
 * Typed access to the Preferences DataStore. Reads are cold [Flow]s, writes are `suspend` and land
 * on IO. Structured settings are single JSON-serialized keys: the gesture map ([GestureMapJson])
 * now, widget config in later phases.
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

    /** Home grid columns, rows and dock slots, in that order. */
    val homeGrid: Flow<Triple<Int, Int, Int>> = dataStore.data.map {
        Triple(
            it[Keys.HOME_COLUMNS] ?: DEFAULT_HOME_COLUMNS,
            it[Keys.HOME_ROWS] ?: DEFAULT_HOME_ROWS,
            it[Keys.DOCK_SLOTS] ?: DEFAULT_DOCK_SLOTS,
        )
    }

    /** Whether home and dock items show their label under the icon. */
    val showHomeLabels: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_HOME_LABELS] ?: DEFAULT_SHOW_HOME_LABELS }

    /** Apps hidden from the drawer, as `AppEntry.key` (`component|serial`). */
    val hiddenApps: Flow<Set<String>> = dataStore.data.map { it[Keys.HIDDEN_APPS].orEmpty() }

    suspend fun setHidden(appKey: String, hidden: Boolean) = withContext(io) {
        dataStore.edit {
            val current = it[Keys.HIDDEN_APPS].orEmpty()
            it[Keys.HIDDEN_APPS] = if (hidden) current + appKey else current - appKey
        }
        Unit
    }

    /** What each gesture does: the defaults with the user's changes on top (task 3.1). */
    val gestureMap: Flow<Map<GestureType, GestureAction>> =
        dataStore.data.map { GestureMapJson.decode(it[Keys.GESTURE_MAP]) }

    suspend fun setGesture(type: GestureType, action: GestureAction) = withContext(io) {
        dataStore.edit {
            val changes = GestureMapJson.decodeChanges(it[Keys.GESTURE_MAP]) + (type to action)
            it[Keys.GESTURE_MAP] = GestureMapJson.encodeChanges(changes)
        }
        Unit
    }

    private object Keys {
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_SIZE_DP = intPreferencesKey("icon_size_dp")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val HOME_COLUMNS = intPreferencesKey("home_columns")
        val HOME_ROWS = intPreferencesKey("home_rows")
        val DOCK_SLOTS = intPreferencesKey("dock_slots")
        val SHOW_HOME_LABELS = booleanPreferencesKey("show_home_labels")
        val GESTURE_MAP = stringPreferencesKey("gesture_map")
    }

    companion object {
        const val DEFAULT_IS_FIRST_RUN: Boolean = true
        const val DEFAULT_ICON_SIZE_DP: Int = 56
        const val DEFAULT_DRAWER_COLUMNS: Int = 5
        const val DEFAULT_HOME_COLUMNS: Int = 5
        const val DEFAULT_HOME_ROWS: Int = 6
        const val DEFAULT_DOCK_SLOTS: Int = 5
        const val DEFAULT_SHOW_HOME_LABELS: Boolean = true
    }
}

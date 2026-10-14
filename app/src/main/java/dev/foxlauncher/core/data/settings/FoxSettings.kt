package dev.foxlauncher.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import dev.foxlauncher.core.model.GestureMaps
import dev.foxlauncher.core.model.PlaylistMode
import dev.foxlauncher.core.model.PlaylistState
import dev.foxlauncher.core.model.RotationTriggers

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

    /** Each home page's own gestures (task 9.3), by page index; elsewhere [gestureMap] applies. */
    val gesturePageMap: Flow<Map<Int, Map<GestureType, GestureAction>>> =
        dataStore.data.map { GestureMapJson.decodePages(it[Keys.GESTURE_PAGE_MAP]) }

    /** Gives [page] its own [action] for [type], or with null lets it follow all pages again. */
    suspend fun setPageGesture(page: Int, type: GestureType, action: GestureAction?) = withContext(io) {
        dataStore.edit {
            val pages = GestureMaps.withPageAction(GestureMapJson.decodePages(it[Keys.GESTURE_PAGE_MAP]), page, type, action)
            if (pages.isEmpty()) it.remove(Keys.GESTURE_PAGE_MAP) else it[Keys.GESTURE_PAGE_MAP] = GestureMapJson.encodePages(pages)
        }
        Unit
    }

    /** Every gesture back to its default action, on every page. */
    suspend fun resetGestures() = withContext(io) {
        dataStore.edit {
            it.remove(Keys.GESTURE_MAP)
            it.remove(Keys.GESTURE_PAGE_MAP)
        }
        Unit
    }

    /**
     * Back, Home and Recents by swiping from the screen edges in other apps (task 3.8); off until the
     * user turns it on, since it needs the accessibility service and takes the edges' touches.
     */
    val navigationGestures: Flow<Boolean> = dataStore.data.map { it[Keys.NAVIGATION_GESTURES] ?: false }

    suspend fun setNavigationGestures(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.NAVIGATION_GESTURES] = enabled }
        Unit
    }

    /** Commands from other apps by broadcast (task 9.4, ADR-22): off until the user turns it on. */
    val automation: Flow<Boolean> = dataStore.data.map { it[Keys.AUTOMATION] ?: false }

    suspend fun setAutomation(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.AUTOMATION] = enabled }
        Unit
    }

    /** Boost through Shizuku (task 9.1, ADR-9): off until the user turns it on. */
    val deepBoost: Flow<Boolean> = dataStore.data.map { it[Keys.DEEP_BOOST] ?: false }

    suspend fun setDeepBoost(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.DEEP_BOOST] = enabled }
        Unit
    }

    /**
     * The wallpaper entry the live wallpaper shows (task 4.3); null until the rotation first picks
     * one. Everything that changes the wallpaper writes this, and only the engine draws it.
     */
    val activeWallpaperEntryId: Flow<Long?> = dataStore.data.map { it[Keys.ACTIVE_WALLPAPER] }

    /** When the wallpaper last changed, epoch ms (task 4.4); null before the first change. */
    val wallpaperChangedAt: Flow<Long?> = dataStore.data.map { it[Keys.WALLPAPER_CHANGED_AT] }

    /** Records a change of wallpaper: the entry now active and when (task 4.4). */
    suspend fun setActiveWallpaper(id: Long, changedAt: Long) = withContext(io) {
        dataStore.edit {
            it[Keys.ACTIVE_WALLPAPER] = id
            it[Keys.WALLPAPER_CHANGED_AT] = changedAt
        }
        Unit
    }

    /** What changes the wallpaper by itself (task 4.4). */
    val rotationTriggers: Flow<RotationTriggers> = dataStore.data.map {
        RotationTriggers(
            intervalMinutes = it[Keys.ROTATION_INTERVAL] ?: 0,
            onUnlock = it[Keys.ROTATE_ON_UNLOCK] ?: false,
            onPageChange = it[Keys.ROTATE_ON_PAGE_CHANGE] ?: false,
        )
    }

    suspend fun setRotationInterval(minutes: Int) = withContext(io) {
        dataStore.edit { it[Keys.ROTATION_INTERVAL] = minutes }
        Unit
    }

    suspend fun setRotateOnUnlock(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.ROTATE_ON_UNLOCK] = enabled }
        Unit
    }

    suspend fun setRotateOnPageChange(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.ROTATE_ON_PAGE_CHANGE] = enabled }
        Unit
    }

    /** Whether each new wallpaper is also set on the lock screen, as a still image (task 4.6). */
    val changeLockScreen: Flow<Boolean> = dataStore.data.map { it[Keys.CHANGE_LOCK_SCREEN] ?: false }

    suspend fun setChangeLockScreen(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.CHANGE_LOCK_SCREEN] = enabled }
        Unit
    }

    /** How long a wallpaper crossfade lasts, in ms (task 4.3; set in the wallpaper settings, task 4.7). */
    val crossfadeMs: Flow<Int> = dataStore.data.map { it[Keys.CROSSFADE_MS] ?: DEFAULT_CROSSFADE_MS }

    suspend fun setCrossfadeMs(ms: Int) = withContext(io) {
        dataStore.edit { it[Keys.CROSSFADE_MS] = ms.coerceIn(MIN_CROSSFADE_MS, MAX_CROSSFADE_MS) }
        Unit
    }

    /** Whether the wallpaper shifts a little as the home pages scroll (task 4.7). */
    val wallpaperParallax: Flow<Boolean> = dataStore.data.map { it[Keys.WALLPAPER_PARALLAX] ?: true }

    suspend fun setWallpaperParallax(enabled: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.WALLPAPER_PARALLAX] = enabled }
        Unit
    }

    /** The wallpaper rotation's mode, shuffle seed and history (task 4.2). */
    val playlistState: Flow<PlaylistState> = dataStore.data.map {
        PlaylistState(
            mode = it[Keys.PLAYLIST_MODE]?.let { name -> PlaylistMode.entries.firstOrNull { mode -> mode.name == name } }
                ?: PlaylistMode.SEQUENTIAL,
            seed = it[Keys.PLAYLIST_SEED],
            history = IdListCodec.decode(it[Keys.PLAYLIST_HISTORY]),
        )
    }

    suspend fun setPlaylistMode(mode: PlaylistMode) = withContext(io) {
        dataStore.edit { it[Keys.PLAYLIST_MODE] = mode.name }
        Unit
    }

    /** After each change of wallpaper: the seed in use and the history with the new current one last. */
    suspend fun savePlaylist(seed: Long, history: List<Long>) = withContext(io) {
        dataStore.edit {
            it[Keys.PLAYLIST_SEED] = seed
            it[Keys.PLAYLIST_HISTORY] = IdListCodec.encode(history)
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
        val GESTURE_PAGE_MAP = stringPreferencesKey("gesture_page_map")
        val NAVIGATION_GESTURES = booleanPreferencesKey("navigation_gestures")
        val DEEP_BOOST = booleanPreferencesKey("deep_boost")
        val AUTOMATION = booleanPreferencesKey("automation")
        val ACTIVE_WALLPAPER = longPreferencesKey("wallpaper_active_entry")
        val CROSSFADE_MS = intPreferencesKey("wallpaper_crossfade_ms")
        val WALLPAPER_CHANGED_AT = longPreferencesKey("wallpaper_changed_at")
        val ROTATION_INTERVAL = intPreferencesKey("wallpaper_rotation_interval_minutes")
        val ROTATE_ON_UNLOCK = booleanPreferencesKey("wallpaper_rotate_on_unlock")
        val ROTATE_ON_PAGE_CHANGE = booleanPreferencesKey("wallpaper_rotate_on_page_change")
        val CHANGE_LOCK_SCREEN = booleanPreferencesKey("wallpaper_change_lock_screen")
        val WALLPAPER_PARALLAX = booleanPreferencesKey("wallpaper_parallax")
        val PLAYLIST_MODE = stringPreferencesKey("wallpaper_playlist_mode")
        val PLAYLIST_SEED = longPreferencesKey("wallpaper_playlist_seed")
        val PLAYLIST_HISTORY = stringPreferencesKey("wallpaper_playlist_history")
    }

    companion object {
        const val DEFAULT_IS_FIRST_RUN: Boolean = true
        const val DEFAULT_ICON_SIZE_DP: Int = 56
        const val DEFAULT_DRAWER_COLUMNS: Int = 5
        const val DEFAULT_HOME_COLUMNS: Int = 5
        const val DEFAULT_HOME_ROWS: Int = 6
        const val DEFAULT_DOCK_SLOTS: Int = 5
        const val DEFAULT_SHOW_HOME_LABELS: Boolean = true
        const val DEFAULT_CROSSFADE_MS: Int = 800
        const val MIN_CROSSFADE_MS: Int = 300
        const val MAX_CROSSFADE_MS: Int = 2000
    }
}

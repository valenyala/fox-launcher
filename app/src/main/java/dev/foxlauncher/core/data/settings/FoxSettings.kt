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

    suspend fun setIconShape(shapeId: String) = withContext(io) {
        dataStore.edit { it[Keys.ICON_SHAPE] = shapeId }
        Unit
    }

    /** Icon edge length in dp, shared by home and drawer, within [ICON_SIZES_DP]. */
    val iconSizeDp: Flow<Int> = dataStore.data.map { it[Keys.ICON_SIZE_DP]?.coerceIn(ICON_SIZES_DP) ?: DEFAULT_ICON_SIZE_DP }

    suspend fun setIconSize(sizeDp: Int) = withContext(io) {
        dataStore.edit { it[Keys.ICON_SIZE_DP] = sizeDp.coerceIn(ICON_SIZES_DP) }
        Unit
    }

    /** The icon pack's package (task 7.1), or null for the apps' own icons. */
    val iconPack: Flow<String?> = dataStore.data.map { it[Keys.ICON_PACK] }

    suspend fun setIconPack(packageName: String?) = withContext(io) {
        dataStore.edit { if (packageName == null) it.remove(Keys.ICON_PACK) else it[Keys.ICON_PACK] = packageName }
        Unit
    }

    /** Themed icons (task 7.1): apps' monochrome icons in the wallpaper's colors, Android 13+. */
    val themedIcons: Flow<Boolean> = dataStore.data.map { it[Keys.THEMED_ICONS] ?: false }

    suspend fun setThemedIcons(themed: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.THEMED_ICONS] = themed }
        Unit
    }

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

    suspend fun setShowHomeLabels(show: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.SHOW_HOME_LABELS] = show }
        Unit
    }

    /**
     * How opaque the rounded surface behind Fox's own widgets is, in percent (user request, 2026-10-15);
     * app widgets draw their own.
     */
    val widgetBackgroundOpacity: Flow<Int> = dataStore.data.map {
        it[Keys.WIDGET_BACKGROUND_OPACITY]?.coerceIn(WIDGET_BACKGROUND_OPACITIES) ?: DEFAULT_WIDGET_BACKGROUND_OPACITY
    }

    suspend fun setWidgetBackgroundOpacity(percent: Int) = withContext(io) {
        dataStore.edit { it[Keys.WIDGET_BACKGROUND_OPACITY] = percent.coerceIn(WIDGET_BACKGROUND_OPACITIES) }
        Unit
    }

    /** Apps hidden from the drawer, as `AppEntry.key` (`component|serial`). */
    val hiddenApps: Flow<Set<String>> = dataStore.data.map { it[Keys.HIDDEN_APPS].orEmpty() }

    suspend fun setHidden(appKey: String, hidden: Boolean) = withContext(io) {
        dataStore.edit {
            val current = it[Keys.HIDDEN_APPS].orEmpty()
            it[Keys.HIDDEN_APPS] = if (hidden) current + appKey else current - appKey
        }
        Unit
    }

    /** Apps Fox opens with the navigation buttons while they are hidden elsewhere (task 9.7), as `AppEntry.key`. */
    val buttonApps: Flow<Set<String>> = dataStore.data.map { it[Keys.BUTTON_APPS].orEmpty() }

    suspend fun setButtonApp(appKey: String, withButtons: Boolean) = withContext(io) {
        dataStore.edit {
            val current = it[Keys.BUTTON_APPS].orEmpty()
            it[Keys.BUTTON_APPS] = if (withButtons) current + appKey else current - appKey
        }
        Unit
    }

    /** Whether Fox showed the navigation buttons to open such an app, so that home hides them again. */
    val buttonsShownByFox: Flow<Boolean> = dataStore.data.map { it[Keys.BUTTONS_SHOWN_BY_FOX] ?: false }

    suspend fun setButtonsShownByFox(shown: Boolean) = withContext(io) {
        dataStore.edit { it[Keys.BUTTONS_SHOWN_BY_FOX] = shown }
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

    /** Every gesture back to its default action. */
    suspend fun resetGestures() = withContext(io) {
        dataStore.edit { it.remove(Keys.GESTURE_MAP) }
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

    /** The entry Fox last set on the lock screen; null before the first. A change catches it up at screen off. */
    val lockWallpaperEntryId: Flow<Long?> = dataStore.data.map { it[Keys.LOCK_WALLPAPER] }

    suspend fun setLockWallpaperEntry(id: Long?) = withContext(io) {
        dataStore.edit { if (id == null) it.remove(Keys.LOCK_WALLPAPER) else it[Keys.LOCK_WALLPAPER] = id }
        Unit
    }

    /** How long a wallpaper crossfade lasts, in ms (task 4.3; set in the wallpaper settings, task 4.7). */
    val crossfadeMs: Flow<Int> = dataStore.data.map { it[Keys.CROSSFADE_MS] ?: DEFAULT_CROSSFADE_MS }

    suspend fun setCrossfadeMs(ms: Int) = withContext(io) {
        dataStore.edit { it[Keys.CROSSFADE_MS] = ms.coerceIn(MIN_CROSSFADE_MS, MAX_CROSSFADE_MS) }
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
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val BUTTON_APPS = stringSetPreferencesKey("button_apps")
        val BUTTONS_SHOWN_BY_FOX = booleanPreferencesKey("buttons_shown_by_fox")
        val HOME_COLUMNS = intPreferencesKey("home_columns")
        val HOME_ROWS = intPreferencesKey("home_rows")
        val DOCK_SLOTS = intPreferencesKey("dock_slots")
        val SHOW_HOME_LABELS = booleanPreferencesKey("show_home_labels")
        val WIDGET_BACKGROUND_OPACITY = intPreferencesKey("widget_background_opacity")
        val GESTURE_MAP = stringPreferencesKey("gesture_map")
        val NAVIGATION_GESTURES = booleanPreferencesKey("navigation_gestures")
        val DEEP_BOOST = booleanPreferencesKey("deep_boost")
        val ACTIVE_WALLPAPER = longPreferencesKey("wallpaper_active_entry")
        val CROSSFADE_MS = intPreferencesKey("wallpaper_crossfade_ms")
        val WALLPAPER_CHANGED_AT = longPreferencesKey("wallpaper_changed_at")
        val ROTATION_INTERVAL = intPreferencesKey("wallpaper_rotation_interval_minutes")
        val ROTATE_ON_UNLOCK = booleanPreferencesKey("wallpaper_rotate_on_unlock")
        val ROTATE_ON_PAGE_CHANGE = booleanPreferencesKey("wallpaper_rotate_on_page_change")
        val CHANGE_LOCK_SCREEN = booleanPreferencesKey("wallpaper_change_lock_screen")
        val LOCK_WALLPAPER = longPreferencesKey("wallpaper_lock_entry")
        val PLAYLIST_MODE = stringPreferencesKey("wallpaper_playlist_mode")
        val PLAYLIST_SEED = longPreferencesKey("wallpaper_playlist_seed")
        val PLAYLIST_HISTORY = stringPreferencesKey("wallpaper_playlist_history")
    }

    companion object {
        const val DEFAULT_IS_FIRST_RUN: Boolean = true
        const val DEFAULT_ICON_SIZE_DP: Int = 56

        /** What the icon size setting allows (task 7.1). */
        val ICON_SIZES_DP: IntRange = 48..64
        const val DEFAULT_DRAWER_COLUMNS: Int = 5
        const val DEFAULT_HOME_COLUMNS: Int = 5
        const val DEFAULT_HOME_ROWS: Int = 6
        const val DEFAULT_DOCK_SLOTS: Int = 5
        const val DEFAULT_SHOW_HOME_LABELS: Boolean = true

        /** What the widget background setting allows, in percent; the default is the surface widgets always had. */
        val WIDGET_BACKGROUND_OPACITIES: IntRange = 0..100
        const val DEFAULT_WIDGET_BACKGROUND_OPACITY: Int = 25
        const val DEFAULT_CROSSFADE_MS: Int = 800
        const val MIN_CROSSFADE_MS: Int = 300
        const val MAX_CROSSFADE_MS: Int = 2000
    }
}

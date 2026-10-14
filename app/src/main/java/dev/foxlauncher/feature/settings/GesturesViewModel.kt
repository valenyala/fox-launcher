package dev.foxlauncher.feature.settings

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.AppShortcut
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.SystemActions
import dev.foxlauncher.feature.gestures.system.AccessibilityRepair
import dev.foxlauncher.feature.gestures.system.FoxAccessibilityService
import dev.foxlauncher.feature.home.HomeRepository
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class GestureRow(val type: GestureType, val action: GestureAction)

@Immutable
data class GesturesUiState(
    val rows: ImmutableList<GestureRow> = persistentListOf(),
    /** The catalog, sorted, for naming apps and choosing one. */
    val apps: ImmutableList<AppEntry> = persistentListOf(),
    /** Fox's accessibility service: system actions work only while it is [AccessibilityStatus.ON]. */
    val accessibility: AccessibilityStatus = AccessibilityStatus.ON,
    /** The user granted WRITE_SECURE_SETTINGS, so Fox restarts a stopped service by itself (ADR-18). */
    val autoRestart: Boolean = false,
    /** A Xiaomi phone, whose "clear recent apps" closes Fox unless it is locked (PLATFORM-NOTES G). */
    val showMiuiLockHint: Boolean = false,
    /** Back, Home and Recents from the screen edges in other apps (task 3.8). */
    val navigationGestures: Boolean = false,
    /** Each page's own gestures (task 9.3), by page index. */
    val pageGestures: ImmutableMap<Int, Map<GestureType, GestureAction>> = persistentMapOf(),
    /** How many home pages there are, to choose one. */
    val pageCount: Int = 1,
)

/** Gesture settings (task 3.5): every gesture with its action, editable. */
@HiltViewModel
class GesturesViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val appRepository: AppRepository,
    homeRepository: HomeRepository,
    private val systemActions: SystemActions,
    private val accessibilityRepair: AccessibilityRepair,
) : ViewModel() {

    /** Bumped to re-read the status: the settings change it while this screen is away. */
    private val statusChecks = MutableStateFlow(0)

    val uiState: StateFlow<GesturesUiState> = combine(
        settings.gestureMap,
        appRepository.apps,
        statusChecks,
        FoxAccessibilityService.connected,
        combine(settings.navigationGestures, settings.gesturePageMap, homeRepository.layout.map { it.pages.size }.distinctUntilChanged(), ::Triple),
    ) { map, apps, _, _, (navigationGestures, pages, pageCount) ->
        GesturesUiState(
            rows = EDITABLE_GESTURES.map { GestureRow(it, map[it] ?: GestureAction.None) }.toImmutableList(),
            apps = apps,
            accessibility = systemActions.status,
            autoRestart = accessibilityRepair.isAvailable,
            showMiuiLockHint = IS_XIAOMI,
            navigationGestures = navigationGestures,
            pageGestures = pages.toImmutableMap(),
            pageCount = pageCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GesturesUiState())

    /** Coming back from the system settings: the service may have been turned on or off there. */
    fun refreshAccessibility() {
        statusChecks.value++
    }

    fun restartService() {
        viewModelScope.launch {
            accessibilityRepair.repairIfStopped()
            refreshAccessibility()
        }
    }

    fun setGesture(type: GestureType, action: GestureAction) {
        viewModelScope.launch { settings.setGesture(type, action) }
    }

    /** [page] gets its own [action] for [type]; null lets it follow all pages again (task 9.3). */
    fun setPageGesture(page: Int, type: GestureType, action: GestureAction?) {
        viewModelScope.launch { settings.setPageGesture(page, type, action) }
    }

    fun setNavigationGestures(enabled: Boolean) {
        viewModelScope.launch { settings.setNavigationGestures(enabled) }
    }

    fun resetGestures() {
        viewModelScope.launch { settings.resetGestures() }
    }

    suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut> = appRepository.shortcutsFor(entry, max = MAX_SHORTCUTS)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_SHORTCUTS = 20

        /** Xiaomi, Redmi and POCO phones all report Xiaomi. */
        val IS_XIAOMI = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

        val EDITABLE_GESTURES = GestureType.entries
    }
}

package dev.foxlauncher.feature.settings

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
import dev.foxlauncher.feature.gestures.system.FoxAccessibilityService
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

/** Gesture settings (task 3.5): every gesture with its action, editable. */
@HiltViewModel
class GesturesViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val appRepository: AppRepository,
    private val systemActions: SystemActions,
) : ViewModel() {

    /** Bumped to re-read the status: the settings change it while this screen is away. */
    private val statusChecks = MutableStateFlow(0)

    val uiState: StateFlow<GesturesUiState> = combine(
        settings.gestureMap,
        appRepository.apps,
        statusChecks,
        FoxAccessibilityService.connected,
    ) { map, apps, _, _ ->
        GesturesUiState(
            rows = EDITABLE_GESTURES.map { GestureRow(it, map[it] ?: GestureAction.None) }.toImmutableList(),
            apps = apps,
            accessibility = systemActions.status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GesturesUiState())

    /** Coming back from the system settings: the service may have been turned on or off there. */
    fun refreshAccessibility() {
        statusChecks.value++
    }

    fun setGesture(type: GestureType, action: GestureAction) {
        viewModelScope.launch { settings.setGesture(type, action) }
    }

    fun resetGestures() {
        viewModelScope.launch { settings.resetGestures() }
    }

    suspend fun shortcutsFor(entry: AppEntry): List<AppShortcut> = appRepository.shortcutsFor(entry, max = MAX_SHORTCUTS)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_SHORTCUTS = 20

        /** TODO(3.6): HOME_KEY joins once the Home key goes through the gesture map. */
        val EDITABLE_GESTURES = GestureType.entries - GestureType.HOME_KEY
    }
}

package dev.foxlauncher.feature.drawer

import android.graphics.Rect
import android.os.Bundle
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.DefaultDispatcher
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DrawerUiState(
    val query: String = "",
    val sections: ImmutableList<DrawerSection> = persistentListOf(),
    val isLoading: Boolean = true,
    val columns: Int = FoxSettings.DEFAULT_DRAWER_COLUMNS,
    val iconSizeDp: Int = FoxSettings.DEFAULT_ICON_SIZE_DP,
    val popup: DrawerPopup? = null,
    /** Null when the device has no work profile; then there are no tabs. */
    val work: WorkUiState? = null,
    val tab: DrawerTab = DrawerTab.PERSONAL,
)

@Immutable
data class WorkUiState(val isPaused: Boolean)

/** The long-press menu, open on [entry]. */
@Immutable
data class DrawerPopup(val entry: AppEntry, val canUninstall: Boolean)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val appRepository: AppRepository,
    settings: FoxSettings,
    @param:DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    /**
     * The search field's text. Owned here rather than mirrored through [uiState], because feeding
     * a text field from an asynchronous flow drops keystrokes and jumps the cursor.
     */
    val searchState = TextFieldState()

    private val query = snapshotFlow { searchState.text.toString() }

    private val popup = MutableStateFlow<DrawerPopup?>(null)

    private val selectedTab = MutableStateFlow(DrawerTab.PERSONAL)

    /** Without a work profile there is only the personal list, whatever tab was last picked. */
    private val tab = combine(selectedTab, appRepository.workProfile) { tab, work ->
        if (work == null) DrawerTab.PERSONAL else tab
    }

    private val layout = combine(settings.drawerColumns, settings.iconSizeDp, ::Pair)

    private val sections = combine(appRepository.apps, query, tab) { apps, query, tab ->
        buildSections(appsForTab(apps, tab, query), query)
    }.flowOn(default)

    private val profile = combine(appRepository.workProfile, tab) { work, tab ->
        work?.let { WorkUiState(isPaused = it.isPaused) } to tab
    }

    private val content = combine(sections, query, appRepository.isLoaded, ::Triple)

    val uiState: StateFlow<DrawerUiState> = combine(
        content,
        layout,
        popup,
        profile,
    ) { (sections, query, isLoaded), (columns, iconSizeDp), popup, (work, tab) ->
        DrawerUiState(
            query = query,
            sections = sections,
            isLoading = !isLoaded,
            columns = columns,
            iconSizeDp = iconSizeDp,
            popup = popup,
            work = work,
            tab = tab,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DrawerUiState())

    fun onQueryChange(query: String) {
        searchState.edit { replace(0, length, query) }
    }

    fun clearQuery() {
        searchState.clearText()
    }

    fun onTabSelected(tab: DrawerTab) {
        selectedTab.value = tab
    }

    fun onWorkPausedChange(paused: Boolean) {
        viewModelScope.launch { appRepository.setWorkPaused(paused) }
    }

    fun onAppClick(entry: AppEntry, sourceBounds: Rect?, options: Bundle?) {
        popup.value = null
        appRepository.launch(entry, sourceBounds, options)
    }

    fun onAppLongClick(entry: AppEntry) {
        viewModelScope.launch {
            popup.value = DrawerPopup(entry, canUninstall = !appRepository.isSystemApp(entry))
        }
    }

    fun dismissPopup() {
        popup.value = null
    }

    fun onAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        popup.value = null
        appRepository.openAppInfo(entry, sourceBounds)
    }

    fun onUninstall(entry: AppEntry) {
        popup.value = null
        appRepository.uninstall(entry)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package dev.foxlauncher.feature.home

import android.graphics.Rect
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import dev.foxlauncher.feature.home.grid.GridSpec
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the home screen needs to render. Grows with every phase. */
@Immutable
data class HomeUiState(
    val isFirstRun: Boolean = FoxSettings.DEFAULT_IS_FIRST_RUN,
    val layout: HomeLayout = HomeLayout(),
    /** The catalog keyed by `AppEntry.key`, so home items can find their label and icon. */
    val apps: ImmutableMap<String, AppEntry> = persistentMapOf(),
    val gridSpec: GridSpec = GridSpec.DEFAULT,
    val iconSizeDp: Int = FoxSettings.DEFAULT_ICON_SIZE_DP,
    val showLabels: Boolean = FoxSettings.DEFAULT_SHOW_HOME_LABELS,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val appsByKey = appRepository.apps.map { apps -> apps.associateBy { it.key }.toImmutableMap() }

    private val gridSpec = settings.homeGrid.map { (cols, rows, dock) -> GridSpec(cols, rows, dock) }

    private val look = combine(gridSpec, settings.iconSizeDp, settings.showHomeLabels, ::Triple)

    val uiState: StateFlow<HomeUiState> = combine(
        settings.isFirstRun,
        homeRepository.layout,
        appsByKey,
        look,
    ) { isFirstRun, layout, apps, (spec, iconSizeDp, showLabels) ->
        HomeUiState(isFirstRun, layout, apps, spec, iconSizeDp, showLabels)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(),
    )

    /** Called once Fox is actually holding the home role; 2.7 hangs the default layout here. */
    fun onLauncherReady() {
        if (!uiState.value.isFirstRun) return
        viewModelScope.launch { settings.setFirstRun(false) }
    }

    fun onItemClick(item: HomeItem, sourceBounds: Rect?, options: Bundle?) {
        when (item) {
            is HomeItem.App -> uiState.value.apps[item.appKey]?.let { appRepository.launch(it, sourceBounds, options) }
            is HomeItem.Shortcut ->
                appRepository.startShortcut(item.packageName, item.shortcutId, item.userSerial, sourceBounds, options)
            is HomeItem.Folder -> Unit // TODO(2.5): open the folder sheet.
            is HomeItem.AppWidget, is HomeItem.NativeWidget -> Unit
        }
    }

    /**
     * DEBUG ONLY, TODO(2.7): remove. Fills the dock and page 0, plus a few on page 1, with the
     * first apps of the catalog so rendering can be checked before drag and drop exists.
     */
    fun addTestItems() {
        viewModelScope.launch {
            val spec = uiState.value.gridSpec
            val apps = appRepository.apps.first { it.isNotEmpty() }.iterator()
            repeat(spec.dockSlots) { slot ->
                if (apps.hasNext()) homeRepository.addApp(apps.next(), HomePosition(HomeContainer.DOCK, 0, slot, 0))
            }
            val engine = GridLayoutEngine(spec)
            listOf(0 to spec.cols * 2, 1 to 3).forEach { (page, count) ->
                val taken = uiState.value.layout.pages.getOrNull(page)?.items.orEmpty()
                    .map { CellRect(it.position.col, it.position.row, it.position.spanX, it.position.spanY) }
                    .toMutableList()
                repeat(count) {
                    val cell = engine.firstFree(1, 1, engine.occupancy(taken)) ?: return@repeat
                    if (!apps.hasNext()) return@repeat
                    homeRepository.addApp(apps.next(), HomePosition(HomeContainer.PAGE, page, cell.col, cell.row))
                    taken += cell
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

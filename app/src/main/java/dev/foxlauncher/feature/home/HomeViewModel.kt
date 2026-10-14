package dev.foxlauncher.feature.home

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Rect
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.apps.IconCache
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.AppShortcut
import dev.foxlauncher.core.model.DEFAULT_GESTURE_MAP
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.RotationTrigger
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.ActionExecutor
import dev.foxlauncher.feature.gestures.ActionResult
import dev.foxlauncher.feature.gestures.ExecContext
import dev.foxlauncher.feature.gestures.system.AccessibilityRepair
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.widgets.NativeWidgetRegistry
import dev.foxlauncher.feature.widgets.app.FoxAppWidgetHost
import dev.foxlauncher.feature.widgets.app.PendingAppWidget
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
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
    /** Icons of the shortcut items, keyed by `HomeItem.Shortcut.shortcutKey`; missing ones show their app's icon. */
    val shortcutIcons: ImmutableMap<String, ImageBitmap> = persistentMapOf(),
    /** What each gesture does (task 3.1); the home screen reads it to know which swipes drag the drawer (task 3.7). */
    val gestureMap: ImmutableMap<GestureType, GestureAction> = DEFAULT_GESTURE_MAP.toImmutableMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val homeRepository: HomeRepository,
    private val appRepository: AppRepository,
    private val iconCache: IconCache,
    private val actionExecutor: ActionExecutor,
    private val accessibilityRepair: AccessibilityRepair,
    private val rotation: RotationScheduler,
    private val nativeWidgets: NativeWidgetRegistry,
    private val appWidgetHost: FoxAppWidgetHost,
) : ViewModel() {

    private val appsByKey = appRepository.apps.map { apps -> apps.associateBy { it.key }.toImmutableMap() }

    private val gestureMap = settings.gestureMap.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_GESTURE_MAP)

    private val gridSpec = settings.homeGrid.map { (cols, rows, dock) -> GridSpec(cols, rows, dock) }

    private val look = combine(gridSpec, settings.iconSizeDp, settings.showHomeLabels, ::Triple)

    /** Loaded off the main thread whenever the set of shortcuts on the home changes or icons go stale. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val shortcutIcons: Flow<ImmutableMap<String, ImageBitmap>> = homeRepository.layout
        .map { layout -> layout.shortcuts() }
        .distinctUntilChanged()
        .combine(iconCache.generation) { shortcuts, _ -> shortcuts }
        .mapLatest { shortcuts ->
            shortcuts.mapNotNull { shortcut -> iconCache.shortcutIcon(shortcut)?.let { shortcut.key to it } }.toMap().toImmutableMap()
        }
        .onStart { emit(persistentMapOf()) }

    /**
     * Kept running while Fox is in the background: the home is what every return shows. Stopping
     * it (`WhileSubscribed`) re-ran the Room query, the settings reads and the shortcut icons on
     * every return after 5 s away, alongside the home's first frame, and shortcut items showed
     * their app's icon until theirs had loaded again.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        settings.isFirstRun,
        homeRepository.layout,
        appsByKey,
        combine(look, gestureMap, ::Pair),
        shortcutIcons,
    ) { isFirstRun, layout, apps, (look, gestures), icons ->
        val (spec, iconSizeDp, showLabels) = look
        HomeUiState(isFirstRun, layout, apps, spec, iconSizeDp, showLabels, icons, gestures.toImmutableMap())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    private val _accessibilityPrompt = MutableStateFlow<AccessibilityStatus?>(null)

    /**
     * Set while the accessibility dialog should show: a system action needed the service, which is
     * [AccessibilityStatus.OFF] (the disclosure) or [AccessibilityStatus.STOPPED] (turn it off and on).
     */
    val accessibilityPrompt: StateFlow<AccessibilityStatus?> = _accessibilityPrompt.asStateFlow()

    /** A home gesture was recognized (task 3.3): run the action it is mapped to (task 3.4). */
    fun onGesture(type: GestureType, context: ExecContext) {
        val action = gestureMap.value[type] ?: GestureAction.None
        val result = actionExecutor.execute(action, context)
        FoxLog.d("Gesture $type -> $action: $result")
        when (result) {
            ActionResult.NEEDS_ACCESSIBILITY, ActionResult.ACCESSIBILITY_STOPPED -> {
                // A stopped service can be restarted with the user's adb grant (ADR-18); one being restarted
                // right now is listed as off for a moment. Either way, run the action once it is back.
                val restartable = result == ActionResult.ACCESSIBILITY_STOPPED || accessibilityRepair.isRepairing
                if (!restartable) {
                    _accessibilityPrompt.value = AccessibilityStatus.OFF
                    return
                }
                viewModelScope.launch {
                    if (accessibilityRepair.repairIfStopped()) {
                        FoxLog.d("Gesture $type -> $action after restarting the service: ${actionExecutor.execute(action, context)}")
                    } else {
                        _accessibilityPrompt.value = AccessibilityStatus.STOPPED
                    }
                }
            }
            ActionResult.NEEDS_LIVE_WALLPAPER -> _liveWallpaperPrompt.value = true
            ActionResult.DONE, ActionResult.NOTHING, ActionResult.FAILED -> Unit
        }
    }

    private val _liveWallpaperPrompt = MutableStateFlow(false)

    /** True while the offer of Fox's live wallpaper should show (a wallpaper gesture without it, task 4.5). */
    val liveWallpaperPrompt: StateFlow<Boolean> = _liveWallpaperPrompt.asStateFlow()

    fun dismissLiveWallpaperPrompt() {
        _liveWallpaperPrompt.value = false
    }

    fun dismissAccessibilityPrompt() {
        _accessibilityPrompt.value = null
    }

    /** The page in view, where new widgets go (task 5.1). */
    private var currentPage = 0

    /** The pager settled on [page]: the optional page-change wallpaper trigger (task 4.4). */
    fun onPageSettled(page: Int) {
        currentPage = page
        viewModelScope.launch { rotation.advanceIfDue(RotationTrigger.PAGE_CHANGE) }
    }

    private var firstRun: Job? = null

    /**
     * Called on every resume while Fox holds the home role. The first time, it fills the dock with
     * the device's default apps (task 2.7), then records that first run is over. The flag is read
     * from the store: [uiState] says "first run" until the settings have loaded.
     */
    fun onLauncherReady() {
        if (!uiState.value.isFirstRun || firstRun?.isActive == true) return
        firstRun = viewModelScope.launch {
            if (!settings.isFirstRun.first()) return@launch
            homeRepository.placeDefaultLayout(dockSlots = settings.homeGrid.first().third)
            settings.setFirstRun(false)
        }
    }

    fun onItemClick(item: HomeItem, sourceBounds: Rect?, options: Bundle?) {
        when (item) {
            is HomeItem.App -> uiState.value.apps[item.appKey]?.let { appRepository.launch(it, sourceBounds, options) }
            is HomeItem.Shortcut ->
                appRepository.startShortcut(item.packageName, item.shortcutId, item.userSerial, sourceBounds, options)
            is HomeItem.Folder -> Unit // HomeScreen opens its sheet.
            is HomeItem.AppWidget, is HomeItem.NativeWidget -> Unit
        }
    }

    /**
     * What [item]'s long-press popup offers (task 2.6): an app's shortcuts, app info for apps and
     * shortcuts, uninstall for non-system apps.
     */
    suspend fun popupFor(item: HomeItem): ItemPopupModel {
        if (item is HomeItem.NativeWidget) return ItemPopupModel(canResize = nativeWidgets[item.kind]?.resizable == true)
        if (item is HomeItem.AppWidget) {
            val resizeMode = appWidgetHost.providerInfo(item.appWidgetId)?.resizeMode ?: AppWidgetProviderInfo.RESIZE_NONE
            return ItemPopupModel(canResize = resizeMode != AppWidgetProviderInfo.RESIZE_NONE)
        }
        val app = appOf(item) ?: return ItemPopupModel()
        // A shortcut item is itself a shortcut: its popup does not list the app's others.
        val shortcuts = if (item is HomeItem.App) appRepository.shortcutsFor(app) else emptyList()
        return ItemPopupModel(
            shortcuts = shortcuts.map { PopupShortcut(it, iconCache.shortcutIcon(it)) }.toImmutableList(),
            canOpenAppInfo = true,
            canUninstall = item is HomeItem.App && !appRepository.isSystemApp(app),
        )
    }

    fun onPopupAction(item: HomeItem, action: PopupAction, sourceBounds: Rect?) {
        when (action) {
            PopupAction.AppInfo -> appOf(item)?.let { appRepository.openAppInfo(it, sourceBounds) }
            PopupAction.Uninstall -> appOf(item)?.let(appRepository::uninstall)
            PopupAction.Remove -> viewModelScope.launch { homeRepository.applyDrop(item, DropAction.Remove(item.id)) }
            PopupAction.Resize -> Unit // HomeScreen shows the handles.
            is PopupAction.OpenShortcut -> action.shortcut.let {
                appRepository.startShortcut(it.packageName, it.id, it.userSerial, sourceBounds, null)
            }
        }
    }

    /** The installed app behind an app or shortcut item. */
    private fun appOf(item: HomeItem): AppEntry? {
        val apps = uiState.value.apps
        return when (item) {
            is HomeItem.App -> apps[item.appKey]
            is HomeItem.Shortcut -> apps.values.firstOrNull { it.packageName == item.packageName && it.userSerial == item.userSerial }
            is HomeItem.Folder, is HomeItem.AppWidget, is HomeItem.NativeWidget -> null
        }
    }

    fun onRenameFolder(folder: HomeItem.Folder, title: String) {
        viewModelScope.launch { homeRepository.renameFolder(folder.folderId, title) }
    }

    /** A widget's resize handles were let go at [cell] (task 5.1), already checked to be free. */
    fun onResizeWidget(item: HomeItem, cell: CellRect) {
        val moved = item.position.copy(col = cell.col, row = cell.row, spanX = cell.spanX, spanY = cell.spanY)
        viewModelScope.launch { homeRepository.move(listOf(item to moved)) }
    }

    /**
     * Places a new native widget at its default size (task 5.1): on the page in view when it fits,
     * else on the first page with room, else on a new page.
     */
    fun addNativeWidget(kind: String) {
        val spec = nativeWidgets[kind] ?: return
        viewModelScope.launch {
            val position = homeRepository.addNativeWidget(kind, spec.defaultSpan, currentPage, uiState.value.gridSpec)
            FoxLog.d("Widget $kind placed on page ${position.page} at ${position.col},${position.row}")
        }
    }

    /** Places an app widget that is bound, and configured if it had to be (task 6.3). */
    fun addAppWidget(pending: PendingAppWidget) {
        viewModelScope.launch {
            val position = homeRepository.addAppWidget(
                pending.appWidgetId,
                pending.provider,
                pending.userSerial,
                pending.span,
                currentPage,
                uiState.value.gridSpec,
            )
            FoxLog.d("App widget ${pending.appWidgetId} placed on page ${position.page} at ${position.col},${position.row}")
        }
    }

    /** A home drag ended; [action] is what `DragResolver` decided. */
    fun onDrop(item: HomeItem, action: DropAction) {
        viewModelScope.launch { homeRepository.applyDrop(item, action) }
    }

    /** Every shortcut item on the home: on pages, in the dock and in folders. */
    private fun HomeLayout.shortcuts(): Set<AppShortcut> =
        (pages.asSequence().flatMap { it.items } + dock.items)
            .flatMap { item -> if (item is HomeItem.Folder) item.items.asSequence() else sequenceOf(item) }
            .filterIsInstance<HomeItem.Shortcut>()
            .map { AppShortcut(it.packageName, it.shortcutId, it.userSerial, it.title, longLabel = null) }
            .toSet()
}

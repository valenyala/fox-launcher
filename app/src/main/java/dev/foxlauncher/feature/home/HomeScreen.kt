package dev.foxlauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureMaps
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.feature.gestures.gestureArena
import dev.foxlauncher.feature.home.drag.DragOverlay
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.drag.DropHighlight
import dev.foxlauncher.feature.home.drag.HomeDragState
import dev.foxlauncher.feature.home.drag.REMOVE_ZONE_HEIGHT
import dev.foxlauncher.feature.home.drag.RemoveZone
import dev.foxlauncher.feature.home.drag.homeDragLayer
import dev.foxlauncher.feature.home.drag.newAppItem
import dev.foxlauncher.feature.home.drag.newShortcutItem
import dev.foxlauncher.feature.home.drag.rememberHomeDragState
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.home.grid.WidgetResize
import dev.foxlauncher.feature.widgets.LocalNativeWidgets
import dev.foxlauncher.feature.widgets.app.AppWidgetSizing
import dev.foxlauncher.feature.widgets.app.LocalAppWidgetHost
import dev.foxlauncher.feature.widgets.app.providerSize
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Drag progress past which a released drawer snaps open instead of closed. */
private const val OPEN_THRESHOLD = 0.2f

/** Vertical fling speed (per second) that opens or closes the drawer regardless of progress. */
private val FLING_VELOCITY = 300.dp

private const val DEFAULT_PAGE = 0

/** Dock height on top of the icon: room for a label and padding. */
private val DOCK_EXTRA_HEIGHT = 32.dp

/** Horizontal padding around each page's grid and the dock. */
private val GRID_PADDING = 8.dp

private const val FOLDER_OPEN_SCALE = 0.9f

/** Room kept between the Remove strip and the shrunk pages while dragging. */
private val REMOVE_ZONE_GAP = 8.dp

/** The pages never shrink below this while dragging, however short the screen. */
private const val MIN_DRAG_GRID_SCALE = 0.7f

/** A dragged item held this close to a side edge flips the page... */
private val EDGE_FLIP_ZONE = 40.dp

/** ...after resting there this long, and again after each further wait. */
private const val EDGE_FLIP_DWELL_MS = 400L

/** A dragged item held off an open folder's card this long closes the folder (Launcher3 waits about as long). */
private const val FOLDER_EXIT_DWELL_MS = 300L

/** Height of a cell in an open folder, on top of the icon: room for the label. */
private val FOLDER_CELL_EXTRA_HEIGHT = 36.dp

object HomeTestTags {
    const val DRAWER = "home_drawer"
    const val PAGER = "home_pager"
    const val DOCK = "home_dock"
    const val REMOVE_ZONE = "home_remove_zone"
    const val ITEM_POPUP = "home_item_popup"
    const val FOLDER_SHEET = "home_folder_sheet"
    const val FOLDER_CARD = "home_folder_card"
    const val FOLDER_TITLE = "home_folder_title"
    const val FOLDER_GRID = "home_folder_grid"
}

/** An item's open popup: the item, its bounds in the drag layer (the anchor) and in the window. */
private data class OpenPopup(val item: HomeItem, val anchor: Rect, val windowBounds: Rect)

/**
 * The widget being resized (task 5.1): its id, and the cells and drag-layer bounds it had when the
 * handles appeared, from which its bounds after each resize follow.
 */
private data class ResizeTarget(val itemId: Long, val originCell: CellRect, val originBounds: Rect)

/** What the drawer slot knows of, and can ask of, the home screen. */
@Stable
interface DrawerScope {
    /**
     * True while the drawer is on screen. It stays composed while closed, so it opens without
     * building anything; closing is the moment to reset it (scroll position).
     */
    val isShown: Boolean

    /** True when the drawer was opened to search (the "Search apps" action): it focuses its search field. */
    val isSearchRequested: Boolean

    /**
     * A long press on [entry] in the drawer. If the finger then moves, the drawer gets out of the
     * way and the app is dragged onto the home screen (task 2.4).
     */
    fun onAppLongPress(entry: AppEntry)
}

/** What a gesture's action can ask of the home screen itself (task 3.4, `ExecContext`). */
interface HomeControls {
    fun openDrawer()

    /** Opens the drawer with its search field focused. */
    fun openSearch()

    fun scrollToDefaultPage()

    /** Does nothing yet: no phase has built a page overview. */
    fun openPageOverview()
}

/**
 * The home surface: wallpaper through the window, pages, the dock, and the app drawer as an overlay
 * that follows the finger (search lives only in the drawer). [homeKeyPresses] increments on every
 * Home press, which closes whatever is open; one made while already home ([homeKeyWhileHome]) with
 * nothing open is the HOME_KEY gesture (task 3.6). [onPageSettled] hears each page the pager
 * settles on after paging. [onCellSizeChanged] hears the size of one grid cell (task 6.2: app widget sizes).
 * Long-pressing an item drags it, and so does long-pressing then moving an app in the drawer
 * ([DrawerScope]); [onDrop] receives what the drop should do (task 2.4). Removing a folder asks first.
 * Tapping a folder opens its [FolderSheet] (task 2.5); a changed title goes to [onRenameFolder].
 * A long press that lifts in place opens the item's [ItemPopup] (task 2.6): [loadPopup] says what
 * it offers and [onPopupAction] receives the choice with the item's window bounds. [onGesture] hears
 * every home gesture the arena recognizes while nothing else is open (task 3.3), with the
 * [HomeControls] its action may use (task 3.4).
 */
@Composable
fun HomeScreen(
    homeKeyPresses: Int,
    onDrawerClosed: () -> Unit,
    modifier: Modifier = Modifier,
    state: HomeUiState = HomeUiState(),
    onItemClick: (HomeItem, Rect?) -> Unit = { _, _ -> },
    onDrop: (HomeItem, DropAction) -> Unit = { _, _ -> },
    onRenameFolder: (HomeItem.Folder, String) -> Unit = { _, _ -> },
    loadPopup: suspend (HomeItem) -> ItemPopupModel = { ItemPopupModel() },
    onPopupAction: (HomeItem, PopupAction, Rect?) -> Unit = { _, _, _ -> },
    onGesture: (GestureType, HomeControls) -> Unit = { _, _ -> },
    onResizeWidget: (HomeItem, CellRect) -> Unit = { _, _ -> },
    homeKeyWhileHome: Boolean = false,
    onPageSettled: (Int) -> Unit = {},
    onCellSizeChanged: (DpSize) -> Unit = {},
    drawer: @Composable DrawerScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val engine = remember(state.gridSpec) { GridLayoutEngine(state.gridSpec) }
    val iconSize = state.iconSizeDp.dp
    var folderToRemove by remember { mutableStateOf<HomeItem.Folder?>(null) }
    val dragState = rememberHomeDragState(state.layout, engine) { item, action ->
        // A folder takes its apps with it: ask first.
        if (action is DropAction.Remove && item is HomeItem.Folder) folderToRemove = item else onDrop(item, action)
    }
    val layout by rememberUpdatedState(state.layout)
    // While dragging, an empty page waits past the last one, unless the dragged item would leave
    // the last page empty anyway. Dropping there creates it; otherwise it vanishes again.
    val hasExtraPage by remember {
        derivedStateOf { dragState.dragging?.let { dragged -> layout.pages.last().items.any { it.id != dragged.id } } == true }
    }
    val pagerState = rememberPagerState(pageCount = { layout.pages.size + if (hasExtraPage) 1 else 0 })
    var pagerWidthPx by remember { mutableFloatStateOf(0f) }
    // While dragging, the pages shrink up from their bottom edge so no cell sits under the Remove
    // strip (Launcher3's "spring-loaded" state). They stay small until the item has settled.
    var pagerHeightPx by remember { mutableFloatStateOf(0f) }
    val dragGridScale = with(LocalDensity.current) {
        if (pagerHeightPx <= 0f) 1f else (1f - (REMOVE_ZONE_HEIGHT + REMOVE_ZONE_GAP).toPx() / pagerHeightPx).coerceAtLeast(MIN_DRAG_GRID_SCALE)
    }
    val gridScale = animateFloatAsState(if (dragState.dragging != null) dragGridScale else 1f, label = "gridScale")
    // The folder whose sheet is open, by its tile's id. It closes by itself if the folder goes away (collapsed, removed).
    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openFolder = openFolderId?.let { id ->
        (state.layout.pages.asSequence().flatMap { it.items } + state.layout.dock.items).firstOrNull { it.id == id } as? HomeItem.Folder
    }
    SideEffect { dragState.setOpenFolder(openFolder) }
    var popup by remember { mutableStateOf<OpenPopup?>(null) }
    var resizing by remember { mutableStateOf<ResizeTarget?>(null) }
    val appWidgetHost = LocalAppWidgetHost.current
    val popupItem = popup?.item
    val popupModel by produceState<ItemPopupModel?>(null, popupItem) {
        value = null
        value = popupItem?.let { loadPopup(it) }
    }
    // A long press opens the popup and arms a drag; moving on starts the drag, which closes the popup.
    val onItemLongPress: (HomeItem, LayoutCoordinates) -> Unit = { item, coordinates ->
        dragState.boundsOf(coordinates)?.let { anchor -> popup = OpenPopup(item, anchor, coordinates.boundsInWindow()) }
        dragState.arm(item, coordinates) { popup = null }
    }
    val itemContent: @Composable (HomeItem) -> Unit = { item ->
        HomeItemView(
            item = item,
            apps = state.apps,
            iconSize = iconSize,
            showLabel = state.showLabels,
            shortcutIcons = state.shortcutIcons,
            onClick = { clicked, bounds ->
                if (clicked is HomeItem.Folder) openFolderId = clicked.id else onItemClick(clicked, bounds)
            },
            // The overlay stands in for the dragged item until it has settled.
            modifier = Modifier.graphicsLayer { alpha = if (dragState.dragging?.id == item.id) 0f else 1f },
            onLongClick = onItemLongPress,
        )
    }
    // 0 = closed, 1 = fully open. The settled state is saved separately, since Animatable is not.
    val progress = remember { Animatable(0f) }
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var openedForSearch by rememberSaveable { mutableStateOf(false) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val isDrawerShown by remember { derivedStateOf { progress.value > 0f } }

    val currentOnDrawerClosed by rememberUpdatedState(onDrawerClosed)
    val currentOnGesture by rememberUpdatedState(onGesture)
    val isRtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val gestureMap by rememberUpdatedState(state.gestureMap)
    val pageGestureMap by rememberUpdatedState(state.pageGestureMap)
    // The bottom strip where the system's own swipes start (home, recents in gesture navigation; the
    // bar in three-button). A gesture that starts there is the system's, never ours (task 3.6).
    val systemGestureStrip by rememberUpdatedState(WindowInsets.mandatorySystemGestures.getBottom(LocalDensity.current))
    val focusManager = LocalFocusManager.current

    /** A drag leaving the drawer: drop it at once so the home screen is the drop surface. */
    fun closeDrawerNow() {
        if (isOpen) currentOnDrawerClosed()
        // The drawer stays composed: drop its search focus (and the keyboard).
        focusManager.clearFocus()
        openedForSearch = false
        isOpen = false
        scope.launch { progress.snapTo(0f) }
    }

    val drawerScope = object : DrawerScope {
        override val isShown: Boolean get() = isDrawerShown
        override val isSearchRequested: Boolean get() = openedForSearch
        override fun onAppLongPress(entry: AppEntry) = dragState.arm(newAppItem(entry), onDragStart = ::closeDrawerNow)
    }
    // Building the drawer is the costliest frame in the app (about 60 ms on the POCO F3), so it is
    // built once, right after the home's first frame, and kept: opening only moves a layer.
    var drawerBuilt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        drawerBuilt = true
    }

    fun settle(open: Boolean, velocity: Float = 0f) {
        if (isOpen && !open) currentOnDrawerClosed()
        if (!open) {
            focusManager.clearFocus()
            openedForSearch = false
        }
        isOpen = open
        scope.launch {
            progress.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = velocity,
            )
        }
    }

    val homeControls = object : HomeControls {
        override fun openDrawer() = settle(open = true)
        override fun openSearch() {
            openedForSearch = true
            settle(open = true)
        }
        override fun scrollToDefaultPage() {
            scope.launch { pagerState.animateScrollToPage(DEFAULT_PAGE) }
        }
        override fun openPageOverview() = FoxLog.d("No page overview yet")
    }
    val currentHomeControls by rememberUpdatedState(homeControls)

    val density = LocalDensity.current
    val flingPx by rememberUpdatedState(with(density) { FLING_VELOCITY.toPx() })
    val currentOnCellSizeChanged by rememberUpdatedState(onCellSizeChanged)
    LaunchedEffect(pagerWidthPx, pagerHeightPx, state.gridSpec, density) {
        if (pagerWidthPx > 0f && pagerHeightPx > 0f) {
            // A page is the pager less its side padding (see HomePage).
            val cell = with(density) { DpSize((pagerWidthPx.toDp() - GRID_PADDING * 2) / state.gridSpec.cols, pagerHeightPx.toDp() / state.gridSpec.rows) }
            currentOnCellSizeChanged(cell)
        }
    }
    // Where a dragged drawer is, kept in step with each move: progress.value lags behind while snapTo
    // calls are still queued, and the last move and the lift often arrive in the same frame.
    val drawerDrag = remember { mutableFloatStateOf(0f) }
    fun dragDrawerTo(target: Float) {
        val clamped = target.coerceIn(0f, 1f)
        drawerDrag.floatValue = clamped
        scope.launch { progress.snapTo(clamped) }
    }
    // The open drawer's own surface outside its list (search field, tabs): dragging it down closes it, by
    // the list's rule (DrawerPullConnection), as does a fling.
    val drawerSurfaceDrag = rememberDraggableState { delta -> dragDrawerTo(drawerDrag.floatValue - delta / heightPx) }
    val drawerPull = remember(density) {
        DrawerPullConnection(
            progress = { progress.value },
            heightPx = { heightPx },
            flingPx = { with(density) { FLING_VELOCITY.toPx() } },
            snapTo = { target -> scope.launch { progress.snapTo(target) } },
            settle = { open, velocity -> settle(open, velocity) },
        )
    }

    LaunchedEffect(Unit) { if (isOpen) progress.snapTo(1f) }
    LaunchedEffect(homeKeyPresses) {
        if (homeKeyPresses > 0) {
            val nothingOpen = dragState.dragging == null && popup == null && openFolderId == null && !isOpen && resizing == null
            dragState.cancel()
            popup = null
            resizing = null
            openFolderId = null
            settle(open = false)
            // Back from an app, Home keeps the page (as Launcher3); while home it runs its action (default: first page).
            if (homeKeyWhileHome && nothingOpen) currentOnGesture(GestureType.HOME_KEY, currentHomeControls)
        }
    }
    WallpaperOffsetsEffect(pagerState)
    // A page settled after paging (task 4.4: the optional "change on page change" trigger).
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { currentOnPageSettled(it) }
    }
    EdgeFlipEffect(dragState, pagerState)
    BackHandler(enabled = isOpen) { settle(open = false) }
    BackHandler(enabled = openFolder != null) { openFolderId = null }
    BackHandler(enabled = popup != null) { popup = null }
    BackHandler(enabled = resizing != null) { resizing = null }
    val outsideFolder by remember { derivedStateOf { dragState.isOutsideOpenFolder() } }
    LaunchedEffect(outsideFolder) {
        if (outsideFolder) {
            delay(FOLDER_EXIT_DWELL_MS)
            openFolderId = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                heightPx = it.height.toFloat().coerceAtLeast(1f)
                // A resize mid-drag (multi-window, a large screen rotating): every cell moved, so put the item back.
                dragState.cancel()
                popup = null
                resizing = null
            }
            .homeDragLayer(dragState)
            // After the drag layer: a drag that owns the finger has consumed its events before the arena sees them.
            .gestureArena(
                enabled = { !isDrawerShown && openFolderId == null && popup == null && resizing == null && dragState.dragging == null },
                isOnItem = { dragState.itemAt(it) != null },
                onGesture = { currentOnGesture(it, currentHomeControls) },
                // A swipe to the left goes to the next page, or to the previous one right to left.
                pagerTakesSwipeLeft = { if (isRtl) pagerState.canScrollBackward else pagerState.canScrollForward },
                pagerTakesSwipeRight = { if (isRtl) pagerState.canScrollForward else pagerState.canScrollBackward },
                dockTop = dragState::dockTop,
                systemGestureTop = { heightPx - systemGestureStrip },
                // A list in an app widget scrolls itself (task 6.4).
                startsOnScrollingContent = { at ->
                    val widget = dragState.itemAt(at) as? HomeItem.AppWidget
                    val window = widget?.let { dragState.windowPositionOf(at) }
                    window != null && appWidgetHost?.viewOf(widget.appWidgetId)?.scrollsVerticallyAt(window.x, window.y) == true
                },
                // Task 3.7: a swipe up that opens the drawer drags it with the finger; any other vertical swipe
                // (remapped, downwards) is recognized when it lifts.
                followsVerticalDrag = { fromDock, upward ->
                    val type = if (fromDock) GestureType.DOCK_SWIPE_UP else GestureType.SWIPE_UP
                    upward && GestureMaps.actionFor(type, pagerState.currentPage, gestureMap, pageGestureMap) == GestureAction.OpenDrawer
                },
                onDragStart = {
                    drawerDrag.floatValue = progress.value
                    openedForSearch = false
                },
                onDrag = { dy -> dragDrawerTo(drawerDrag.floatValue - dy / heightPx) },
                onDragEnd = { velocityY ->
                    val open = when {
                        velocityY < -flingPx -> true
                        velocityY > flingPx -> false
                        else -> drawerDrag.floatValue > OPEN_THRESHOLD
                    }
                    settle(open, velocity = -velocityY / heightPx)
                },
            ),
    ) {
        DropHighlight(dragState)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                // Not the keyboard's: typing in the widget picker or the drawer must not squeeze the cells, which would
                // resize every app widget and the picker's sizes with them.
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .graphicsLayer {
                    alpha = 1f - progress.value
                    // Alpha per draw call instead of an off-screen copy of the whole home every frame (see DrawerLayer).
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged {
                        pagerWidthPx = it.width.toFloat()
                        pagerHeightPx = it.height.toFloat()
                    }
                    .graphicsLayer {
                        scaleX = gridScale.value
                        scaleY = gridScale.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .testTag(HomeTestTags.PAGER),
            ) { page ->
                DisposableEffect(page) { onDispose { dragState.unregisterPage(page) } }
                HomePage(
                    items = state.layout.pages.getOrNull(page)?.items ?: persistentListOf(),
                    engine = engine,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = GRID_PADDING)
                        .onPlaced { dragState.registerPage(page, it) },
                    itemContent = itemContent,
                )
            }
            PageIndicator(pagerState)
            Dock(
                items = state.layout.dock.items,
                slots = state.gridSpec.dockSlots,
                modifier = Modifier
                    .height(iconSize + DOCK_EXTRA_HEIGHT)
                    .padding(horizontal = GRID_PADDING)
                    .onPlaced(dragState::registerDock)
                    .testTag(HomeTestTags.DOCK),
                itemContent = itemContent,
            )
        }
        OpenFolder(
            folder = openFolder,
            dragState = dragState,
            cellHeight = iconSize + FOLDER_CELL_EXTRA_HEIGHT,
            onClose = { openFolderId = null },
            onRename = onRenameFolder,
        ) { item ->
            HomeItemView(
                item = item,
                apps = state.apps,
                iconSize = iconSize,
                showLabel = true,
                shortcutIcons = state.shortcutIcons,
                onClick = { clicked, bounds ->
                    openFolderId = null
                    onItemClick(clicked, bounds)
                },
                modifier = Modifier.graphicsLayer { alpha = if (dragState.dragging?.id == item.id) 0f else 1f },
                onLongClick = onItemLongPress,
            )
        }
        val shownPopup = popup
        val shownModel = popupModel
        if (shownPopup != null && shownModel != null) {
            ItemPopup(
                anchor = shownPopup.anchor,
                model = shownModel,
                onAction = { action ->
                    popup = null
                    val item = shownPopup.item
                    when {
                        // A folder takes its apps with it: ask first, as when it is dropped on Remove.
                        action == PopupAction.Remove && item is HomeItem.Folder -> folderToRemove = item
                        action == PopupAction.Resize -> item.position.let { p ->
                            resizing = ResizeTarget(item.id, CellRect(p.col, p.row, p.spanX, p.spanY), shownPopup.anchor)
                        }
                        else -> onPopupAction(item, action, shownPopup.windowBounds)
                    }
                },
                onDismiss = { popup = null },
                // Dragged out, a shortcut becomes a pinned shortcut item; the popup closes as the drag begins.
                onShortcutLongPress = { shortcut -> dragState.arm(newShortcutItem(shortcut), onDragStart = { popup = null }) },
            )
        }
        resizing?.let { target ->
            val widget = state.layout.pages.asSequence().flatMap { it.items }.firstOrNull { it.id == target.itemId }
            val range = widget?.let { resizeRange(it, target, state.gridSpec) }
            if (widget == null || range == null) {
                // Removed or moved off the grid meanwhile: nothing to resize.
                SideEffect { resizing = null }
            } else {
                ResizeHandles(widget, range, target, state.layout, engine, onResize = onResizeWidget, onDismiss = { resizing = null })
            }
        }
        RemoveZone(dragState, Modifier.testTag(HomeTestTags.REMOVE_ZONE))
        DragOverlay(dragState) { item ->
            HomeItemView(item, state.apps, iconSize, state.showLabels, onClick = null, shortcutIcons = state.shortcutIcons)
        }
        DrawerLayer(
            built = drawerBuilt,
            isShown = { isDrawerShown },
            progress = { progress.value },
            pull = drawerPull,
            // The open drawer's surface outside its list closes it by dragging.
            modifier = Modifier.draggable(
                state = drawerSurfaceDrag,
                orientation = Orientation.Vertical,
                enabled = isOpen,
                onDragStarted = { drawerDrag.floatValue = progress.value },
                onDragStopped = { velocity ->
                    val open = when {
                        velocity > flingPx -> false
                        velocity < -flingPx -> true
                        else -> drawerDrag.floatValue > 1f - OPEN_THRESHOLD
                    }
                    settle(open, -velocity / heightPx)
                },
            ),
        ) {
            drawerScope.drawer()
        }
    }
    folderToRemove?.let { folder ->
        RemoveFolderDialog(
            folder = folder,
            onConfirm = {
                folderToRemove = null
                onDrop(folder, DropAction.Remove(folder.id))
            },
            onDismiss = { folderToRemove = null },
        )
    }
}

/** The spans a widget may be resized between. */
private data class SpanRange(val min: Span, val max: Span)

/**
 * The spans [widget] may take: a native widget's from its spec (task 5.1), an app widget's from its
 * provider and the cell size (task 6.4). Null for anything else, or an app widget not yet drawn.
 */
@Composable
private fun resizeRange(widget: HomeItem, target: ResizeTarget, grid: GridSpec): SpanRange? = when (widget) {
    is HomeItem.NativeWidget -> LocalNativeWidgets.current[widget.kind]?.let { SpanRange(it.minSpan, it.maxSpan) }
    is HomeItem.AppWidget -> LocalAppWidgetHost.current?.cachedInfo(widget.appWidgetId)?.let { info ->
        val cellW = target.originBounds.width / target.originCell.spanX
        val cellH = target.originBounds.height / target.originCell.spanY
        if (cellW <= 0f || cellH <= 0f) return@let null
        AppWidgetSizing.spans(info.providerSize(), cellW, cellH, grid)?.let { SpanRange(it.min, it.max) }
    }
    else -> null
}

/** [WidgetResizeFrame] for [widget], where it is now, limited by [range] and the other items on its page. */
@Composable
private fun ResizeHandles(
    widget: HomeItem,
    range: SpanRange,
    target: ResizeTarget,
    layout: HomeLayout,
    engine: GridLayoutEngine,
    onResize: (HomeItem, CellRect) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = widget.position
    val cell = CellRect(p.col, p.row, p.spanX, p.spanY)
    val origin = target.originCell
    val cellW = target.originBounds.width / origin.spanX
    val cellH = target.originBounds.height / origin.spanY
    val bounds = Rect(
        left = target.originBounds.left + (cell.col - origin.col) * cellW,
        top = target.originBounds.top + (cell.row - origin.row) * cellH,
        right = target.originBounds.left + (cell.right - origin.col) * cellW,
        bottom = target.originBounds.top + (cell.bottom - origin.row) * cellH,
    )
    val others = layout.pages.getOrNull(p.page)?.items.orEmpty()
        .filter { it.id != widget.id }
        .map { it.position.let { q -> CellRect(q.col, q.row, q.spanX, q.spanY) } }
    val occupied = remember(others, engine) { engine.occupancy(others) }
    WidgetResizeFrame(
        bounds = bounds,
        cell = cell,
        horizontal = range.min.x != range.max.x,
        vertical = range.min.y != range.max.y,
        resize = { edge, cells -> WidgetResize.resized(engine, cell, edge, cells, range.min, range.max, occupied) },
        onResize = { onResize(widget, it) },
        onDismiss = onDismiss,
    )
}

/**
 * The [FolderSheet] of [folder], fading and scaling in and out. The last folder shown is kept for
 * the exit animation; its title is committed once the sheet has gone.
 */
@Composable
private fun OpenFolder(
    folder: HomeItem.Folder?,
    dragState: HomeDragState,
    cellHeight: Dp,
    onClose: () -> Unit,
    onRename: (HomeItem.Folder, String) -> Unit,
    itemContent: @Composable (HomeItem) -> Unit,
) {
    var shown by remember { mutableStateOf(folder) }
    if (folder != null) shown = folder
    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn() + scaleIn(initialScale = FOLDER_OPEN_SCALE),
        exit = fadeOut() + scaleOut(targetScale = FOLDER_OPEN_SCALE),
    ) {
        val current = shown ?: return@AnimatedVisibility
        key(current.id) {
            FolderSheet(
                folder = current,
                dragState = dragState,
                cellHeight = cellHeight,
                onClose = onClose,
                onTitleChange = { title -> if (title != current.title) onRename(current, title) },
                itemContent = itemContent,
            )
        }
    }
}

@Composable
private fun RemoveFolderDialog(folder: HomeItem.Folder, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.home_remove_folder_title)) },
        text = {
            Text(pluralStringResource(R.plurals.home_remove_folder_message, folder.items.size, folder.items.size))
        },
    )
}

/**
 * The drawer's container: one screen below while closed (off screen, so it takes no touches) and
 * hidden from accessibility services and tests, sliding and fading in with [progress]. Its own
 * composable, so the drawer opening or closing recomposes this and not the whole home screen.
 * Both this fade and the home grid's use [CompositingStrategy.ModulateAlpha]: the default copies
 * the whole full-screen layer off screen every frame of the animation, which made opening the
 * drawer GPU-bound (up to 20 ms a frame on the POCO F3).
 *
 * While closed, the content's own layer is not drawn at all. Its display list survives, but it is
 * out of the window's render tree. A hidden window loses the display lists of that tree, and
 * every return to the home screen re-recorded the hidden drawer before the home's first frame.
 */
@Composable
private fun DrawerLayer(
    built: Boolean,
    isShown: () -> Boolean,
    progress: () -> Float,
    pull: NestedScrollConnection,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown = isShown()
    if (!built && !shown) return
    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(pull)
            .graphicsLayer {
                val p = progress()
                translationY = (1f - p) * size.height
                alpha = p
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .then(
                if (shown) {
                    Modifier.testTag(HomeTestTags.DRAWER)
                } else {
                    Modifier.clearAndSetSemantics { }.drawWithContent { }
                },
            )
            // After the layer's translation, so it moves with the drawer.
            .then(modifier),
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer()) {
            content()
        }
    }
}

/**
 * Pull-down-to-close for the drawer's lists. A list takes every vertical drag, so the home swipe
 * never sees one that starts on it; this takes what the list leaves over once it is at its top
 * and pulls the drawer down with the finger. Release rules match the home swipe: a fling either
 * way, otherwise close once pulled past [OPEN_THRESHOLD] of the height.
 */
private class DrawerPullConnection(
    private val progress: () -> Float,
    private val heightPx: () -> Float,
    private val flingPx: () -> Float,
    private val snapTo: (Float) -> Unit,
    private val settle: (open: Boolean, velocity: Float) -> Unit,
) : NestedScrollConnection {
    /** Where the pulled drawer is, kept in step with each delta; null when not pulling. */
    private var pulled: Float? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val current = pulled ?: return Offset.Zero
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        // The finger goes back up while the drawer is pulled down: raise the drawer before the list scrolls.
        val target = (current - available.y / heightPx()).coerceAtMost(1f)
        pulled = target
        snapTo(target)
        return Offset(0f, -(target - current) * heightPx())
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        val target = ((pulled ?: progress()) - available.y / heightPx()).coerceAtLeast(0f)
        snapTo(target)
        if (target > 0f) {
            pulled = target
        } else {
            // Fully pulled away: settle now; the list may never report the release once the drawer is closed.
            pulled = null
            settle(false, 0f)
        }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val current = pulled ?: return Velocity.Zero
        pulled = null
        val open = when {
            available.y > flingPx() -> false
            available.y < -flingPx() -> true
            else -> current > 1f - OPEN_THRESHOLD
        }
        settle(open, -available.y / heightPx())
        // The list must not fling as well.
        return available
    }
}

/**
 * Flips to the neighbouring page while a dragged item rests at a side edge (task 2.4). Each flip
 * runs in its own job so leaving the edge never stops the pager between two pages.
 */
@Composable
private fun EdgeFlipEffect(dragState: HomeDragState, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { EDGE_FLIP_ZONE.toPx() }
    val edge by remember(edgePx) { derivedStateOf { dragState.edgeDirection(edgePx) } }
    LaunchedEffect(edge) {
        if (edge == 0) return@LaunchedEffect
        while (true) {
            delay(EDGE_FLIP_DWELL_MS)
            val next = pagerState.currentPage + edge
            if (next !in 0 until pagerState.pageCount) break
            scope.launch { pagerState.animateScrollToPage(next) }.join()
            // The finger may not have moved, but the page under it has.
            dragState.refresh()
        }
    }
}

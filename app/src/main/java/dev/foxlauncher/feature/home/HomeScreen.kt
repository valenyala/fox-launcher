package dev.foxlauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.feature.home.drag.DragOverlay
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.drag.DropHighlight
import dev.foxlauncher.feature.home.drag.HomeDragState
import dev.foxlauncher.feature.home.drag.REMOVE_ZONE_HEIGHT
import dev.foxlauncher.feature.home.drag.RemoveZone
import dev.foxlauncher.feature.home.drag.homeDragLayer
import dev.foxlauncher.feature.home.drag.newAppItem
import dev.foxlauncher.feature.home.drag.rememberHomeDragState
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Drag progress past which a released drawer snaps open instead of closed. */
private const val OPEN_THRESHOLD = 0.2f

/** Vertical fling speed (per second) that opens or closes the drawer regardless of progress. */
private val FLING_VELOCITY = 300.dp

private const val PILL_ALPHA = 0.35f

private const val DEFAULT_PAGE = 0

/** Dock height on top of the icon: room for a label and padding. */
private val DOCK_EXTRA_HEIGHT = 32.dp

/** Horizontal padding around each page's grid and the dock. */
private val GRID_PADDING = 8.dp

/** Room kept between the Remove strip and the shrunk pages while dragging. */
private val REMOVE_ZONE_GAP = 8.dp

/** The pages never shrink below this while dragging, however short the screen. */
private const val MIN_DRAG_GRID_SCALE = 0.7f

/** A dragged item held this close to a side edge flips the page... */
private val EDGE_FLIP_ZONE = 40.dp

/** ...after resting there this long, and again after each further wait. */
private const val EDGE_FLIP_DWELL_MS = 400L

object HomeTestTags {
    const val SEARCH_PILL = "home_search_pill"
    const val DRAWER = "home_drawer"
    const val PAGER = "home_pager"
    const val DOCK = "home_dock"
    const val REMOVE_ZONE = "home_remove_zone"
}

/** What the drawer slot can ask of the home screen. */
@Stable
fun interface DrawerScope {
    /**
     * A long press on [entry] in the drawer. If the finger then moves, the drawer gets out of the
     * way and the app is dragged onto the home screen (task 2.4).
     */
    fun onAppLongPress(entry: AppEntry)
}

/**
 * The home surface: wallpaper through the window, a "Search apps" pill, and the app drawer as an
 * overlay that follows the finger. [homeKeyPresses] increments on every Home key press while Fox
 * is in front, which closes the drawer. [drawer] receives whether the search field should focus.
 * Long-pressing an item drags it, and so does long-pressing then moving an app in the drawer
 * ([DrawerScope]); [onDrop] receives what the drop should do (task 2.4). Removing a folder asks first.
 */
@Composable
fun HomeScreen(
    homeKeyPresses: Int,
    onDrawerClosed: () -> Unit,
    modifier: Modifier = Modifier,
    state: HomeUiState = HomeUiState(),
    onItemClick: (HomeItem, Rect?) -> Unit = { _, _ -> },
    onDrop: (HomeItem, DropAction) -> Unit = { _, _ -> },
    drawer: @Composable DrawerScope.(autoFocusSearch: Boolean) -> Unit,
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
    // While dragging, the pages shrink up from their bottom edge so no cell sits under the Remove
    // strip (Launcher3's "spring-loaded" state). They stay small until the item has settled.
    var pagerHeightPx by remember { mutableFloatStateOf(0f) }
    val dragGridScale = with(LocalDensity.current) {
        if (pagerHeightPx <= 0f) 1f else (1f - (REMOVE_ZONE_HEIGHT + REMOVE_ZONE_GAP).toPx() / pagerHeightPx).coerceAtLeast(MIN_DRAG_GRID_SCALE)
    }
    val gridScale = animateFloatAsState(if (dragState.dragging != null) dragGridScale else 1f, label = "gridScale")
    val itemContent: @Composable (HomeItem) -> Unit = { item ->
        HomeItemView(
            item = item,
            apps = state.apps,
            iconSize = iconSize,
            showLabel = state.showLabels,
            onClick = onItemClick,
            // The overlay stands in for the dragged item until it has settled.
            modifier = Modifier.graphicsLayer { alpha = if (dragState.dragging?.id == item.id) 0f else 1f },
            onLongClick = dragState::start,
        )
    }
    // 0 = closed, 1 = fully open. The settled state is saved separately, since Animatable is not.
    val progress = remember { Animatable(0f) }
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var openedBySearch by rememberSaveable { mutableStateOf(false) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val isDrawerShown by remember { derivedStateOf { progress.value > 0f } }

    /** A drag leaving the drawer: drop it at once so the home screen is the drop surface. */
    fun closeDrawerNow() {
        if (isOpen) onDrawerClosed()
        isOpen = false
        scope.launch { progress.snapTo(0f) }
    }

    val drawerScope = DrawerScope { entry -> dragState.arm(newAppItem(entry), ::closeDrawerNow) }

    fun settle(open: Boolean, velocity: Float = 0f) {
        if (isOpen && !open) onDrawerClosed()
        isOpen = open
        scope.launch {
            progress.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = velocity,
            )
        }
    }

    LaunchedEffect(Unit) { if (isOpen) progress.snapTo(1f) }
    // Home while home: close the drawer and go back to the default page (0 until it is a setting).
    LaunchedEffect(homeKeyPresses) {
        if (homeKeyPresses > 0) {
            dragState.cancel()
            settle(open = false)
            pagerState.animateScrollToPage(DEFAULT_PAGE)
        }
    }
    WallpaperOffsetsEffect(pagerState)
    EdgeFlipEffect(dragState, pagerState)
    BackHandler(enabled = isOpen) { settle(open = false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                heightPx = it.height.toFloat().coerceAtLeast(1f)
                // Rotation or a resize mid-drag: every cell moved, so put the item back.
                dragState.cancel()
            }
            .homeDragLayer(dragState)
            // TODO(phase3): temporary. GestureArena replaces this detector in task 3.3.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                // Kept in step with each drag event. progress.value lags behind while snapTo calls are
                // still queued, and the last move and the up often arrive in the same frame.
                var dragProgress = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        dragProgress = progress.value
                        openedBySearch = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // Takes the batched historical samples too: a fast flick can be one or two events.
                        tracker.addPointerInputChange(change)
                        change.consume()
                        dragProgress = (dragProgress - dragAmount / heightPx).coerceIn(0f, 1f)
                        val target = dragProgress
                        scope.launch { progress.snapTo(target) }
                    },
                    onDragEnd = {
                        val velocityY = tracker.calculateVelocity().y
                        val fling = FLING_VELOCITY.toPx()
                        val open = when {
                            velocityY < -fling -> true
                            velocityY > fling -> false
                            else -> dragProgress > OPEN_THRESHOLD
                        }
                        settle(open, velocity = -velocityY / heightPx)
                    },
                    onDragCancel = { settle(dragProgress > OPEN_THRESHOLD) },
                )
            },
    ) {
        DropHighlight(dragState)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .graphicsLayer { alpha = 1f - progress.value },
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { pagerHeightPx = it.height.toFloat() }
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
            SearchPill(
                onClick = {
                    openedBySearch = true
                    settle(open = true)
                },
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
        RemoveZone(dragState, Modifier.testTag(HomeTestTags.REMOVE_ZONE))
        DragOverlay(dragState) { item ->
            HomeItemView(item, state.apps, iconSize, state.showLabels, onClick = null)
        }
        if (isDrawerShown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = (1f - progress.value) * size.height
                        alpha = progress.value
                    }
                    .testTag(HomeTestTags.DRAWER),
            ) {
                drawerScope.drawer(openedBySearch)
            }
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

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Translucent and white: it sits directly on an arbitrary wallpaper.
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = PILL_ALPHA),
        contentColor = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.SEARCH_PILL),
    ) {
        Text(
            text = stringResource(R.string.drawer_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
        )
    }
}

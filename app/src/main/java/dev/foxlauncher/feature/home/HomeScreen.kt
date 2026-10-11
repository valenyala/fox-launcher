package dev.foxlauncher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import kotlinx.coroutines.launch

/** Drag progress past which a released drawer snaps open instead of closed. */
private const val OPEN_THRESHOLD = 0.2f

/** Vertical fling speed (per second) that opens or closes the drawer regardless of progress. */
private val FLING_VELOCITY = 300.dp

private const val PILL_ALPHA = 0.35f

private const val DEFAULT_PAGE = 0

/** Dock height on top of the icon: room for a label and padding. */
private val DOCK_EXTRA_HEIGHT = 32.dp

object HomeTestTags {
    const val SEARCH_PILL = "home_search_pill"
    const val DRAWER = "home_drawer"
    const val PAGER = "home_pager"
}

/**
 * The home surface: wallpaper through the window, a "Search apps" pill, and the app drawer as an
 * overlay that follows the finger. [homeKeyPresses] increments on every Home key press while Fox
 * is in front, which closes the drawer. [drawer] receives whether the search field should focus.
 */
@Composable
fun HomeScreen(
    homeKeyPresses: Int,
    onDrawerClosed: () -> Unit,
    modifier: Modifier = Modifier,
    state: HomeUiState = HomeUiState(),
    onItemClick: (HomeItem, Rect?) -> Unit = { _, _ -> },
    drawer: @Composable (autoFocusSearch: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.layout.pages.size })
    val engine = remember(state.gridSpec) { GridLayoutEngine(state.gridSpec) }
    val iconSize = state.iconSizeDp.dp
    val itemContent: @Composable (HomeItem) -> Unit = { item ->
        HomeItemView(item, state.apps, iconSize, state.showLabels, onItemClick)
    }
    // 0 = closed, 1 = fully open. The settled state is saved separately, since Animatable is not.
    val progress = remember { Animatable(0f) }
    var isOpen by rememberSaveable { mutableStateOf(false) }
    var openedBySearch by rememberSaveable { mutableStateOf(false) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val isDrawerShown by remember { derivedStateOf { progress.value > 0f } }

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
            settle(open = false)
            pagerState.animateScrollToPage(DEFAULT_PAGE)
        }
    }
    WallpaperOffsetsEffect(pagerState)
    BackHandler(enabled = isOpen) { settle(open = false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
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
                key = { state.layout.pages[it].index },
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeTestTags.PAGER),
            ) { page ->
                HomePage(
                    items = state.layout.pages[page].items,
                    engine = engine,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    itemContent = itemContent,
                )
            }
            PageIndicator(pagerState)
            Dock(
                items = state.layout.dock.items,
                slots = state.gridSpec.dockSlots,
                modifier = Modifier
                    .height(iconSize + DOCK_EXTRA_HEIGHT)
                    .padding(horizontal = 8.dp),
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
                drawer(openedBySearch)
            }
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

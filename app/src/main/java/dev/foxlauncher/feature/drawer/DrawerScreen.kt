package dev.foxlauncher.feature.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.core.ui.launchSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Opacity of the drawer scrim over the wallpaper. */
private const val SCRIM_ALPHA = 0.9f

object DrawerTestTags {
    const val GRID = "drawer_grid"
    const val SEARCH = "drawer_search"
    const val RAIL = "drawer_rail"
}

/** The drawer wired to its ViewModel. [autoFocusSearch] is true only when opened by a search action. */
@Composable
fun DrawerScreen(
    viewModel: DrawerViewModel,
    autoFocusSearch: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    DrawerContent(
        state = state,
        searchState = viewModel.searchState,
        autoFocusSearch = autoFocusSearch,
        onAppClick = { entry, bounds ->
            val source = bounds?.let { launchSource(view, it) }
            viewModel.onAppClick(entry, source?.sourceBounds, source?.options)
        },
        onAppLongClick = viewModel::onAppLongClick,
        onDismissPopup = viewModel::dismissPopup,
        onAppInfo = { entry, bounds -> viewModel.onAppInfo(entry, bounds?.let { launchSource(view, it).sourceBounds }) },
        onUninstall = viewModel::onUninstall,
        modifier = modifier,
    )
}

/**
 * Stateless drawer: search field, letter-sectioned grid and a fast-scroll rail. Bounds passed to
 * the callbacks are the tapped icon's bounds in window coordinates.
 */
@Composable
fun DrawerContent(
    state: DrawerUiState,
    searchState: TextFieldState,
    autoFocusSearch: Boolean,
    onAppClick: (AppEntry, Rect?) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onDismissPopup: () -> Unit,
    onAppInfo: (AppEntry, Rect?) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, state.iconSizeDp.dp) },
) {
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) focusRequester.requestFocus()
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.filter { it }.collect {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    // A new query should show its best match, not wherever the list was scrolled to.
    LaunchedEffect(state.query) { gridState.scrollToItem(0) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            OutlinedTextField(
                state = searchState,
                placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .testTag(DrawerTestTags.SEARCH),
            )
            when {
                state.isLoading -> Unit
                state.sections.isEmpty() -> EmptyMessage(state.query.isNotBlank())
                else -> Row(Modifier.fillMaxSize()) {
                    AppGrid(
                        state = state,
                        gridState = gridState,
                        onAppClick = onAppClick,
                        onAppLongClick = onAppLongClick,
                        onDismissPopup = onDismissPopup,
                        onAppInfo = onAppInfo,
                        onUninstall = onUninstall,
                        iconContent = iconContent,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.query.isBlank()) {
                        FastScrollRail(state.sections, gridState)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(isSearching: Boolean) {
    Text(
        text = stringResource(if (isSearching) R.string.drawer_no_results else R.string.drawer_no_apps),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}

@Composable
private fun AppGrid(
    state: DrawerUiState,
    gridState: LazyGridState,
    onAppClick: (AppEntry, Rect?) -> Unit,
    onAppLongClick: (AppEntry) -> Unit,
    onDismissPopup: () -> Unit,
    onAppInfo: (AppEntry, Rect?) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    iconContent: @Composable (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.columns),
        state = gridState,
        contentPadding = WindowInsets.navigationBars.asPaddingValues().plusHorizontal(8.dp),
        modifier = modifier
            .fillMaxHeight()
            .testTag(DrawerTestTags.GRID),
    ) {
        state.sections.forEach { section ->
            if (section.letter.isNotEmpty()) {
                item(key = "header-${section.letter}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    Text(
                        text = section.letter,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
            }
            section.apps.forEach { app ->
                item(key = app.key, contentType = "app") {
                    DrawerItem(
                        entry = app,
                        popup = state.popup?.takeIf { it.entry.key == app.key },
                        onClick = onAppClick,
                        onLongClick = onAppLongClick,
                        onDismissPopup = onDismissPopup,
                        onAppInfo = onAppInfo,
                        onUninstall = onUninstall,
                        iconContent = iconContent,
                    )
                }
            }
        }
    }
}

/** Where an item's icon last was, without making every layout pass a recomposition. */
private class BoundsHolder {
    var bounds: Rect? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    entry: AppEntry,
    popup: DrawerPopup?,
    onClick: (AppEntry, Rect?) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    onDismissPopup: () -> Unit,
    onAppInfo: (AppEntry, Rect?) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    iconContent: @Composable (AppEntry) -> Unit,
) {
    val holder = remember { BoundsHolder() }
    val haptics = LocalHapticFeedback.current
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick(entry, holder.bounds) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(entry)
                    },
                )
                .padding(vertical = 8.dp),
        ) {
            Box(Modifier.onGloballyPositioned { holder.bounds = it.boundsInWindow() }) {
                iconContent(entry)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        DropdownMenu(expanded = popup != null, onDismissRequest = onDismissPopup) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.popup_app_info)) },
                onClick = { onAppInfo(entry, holder.bounds) },
            )
            if (popup?.canUninstall == true) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.popup_uninstall)) },
                    onClick = { onUninstall(entry) },
                )
            }
            // Placeholder for Phase 2; shown disabled so the menu's final shape is visible now.
            DropdownMenuItem(
                text = { Text(stringResource(R.string.popup_add_to_home)) },
                onClick = {},
                enabled = false,
            )
        }
    }
}

/**
 * The A–Z rail. Touching or dragging over a letter jumps the grid to that section's header.
 * Only shown without a query, since search results have no sections.
 */
@Composable
private fun FastScrollRail(sections: ImmutableList<DrawerSection>, gridState: LazyGridState) {
    val scope = rememberCoroutineScope()
    val letters = remember(sections) { sections.map { it.letter } }
    // Grid index of each section header: headers and apps are all items of the one grid.
    val headerIndices = remember(sections) {
        sections.runningFold(0) { index, section -> index + 1 + section.apps.size }.dropLast(1)
    }
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag(DrawerTestTags.RAIL)
            .pointerInput(letters) {
                var current = -1
                fun jumpTo(y: Float) {
                    val index = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    if (index == current) return
                    current = index
                    scope.launch { gridState.scrollToItem(headerIndices[index]) }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    current = -1
                    jumpTo(down.position.y)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        jumpTo(change.position.y)
                        change.consume()
                    }
                }
            },
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun PaddingValues.plusHorizontal(horizontal: Dp): PaddingValues =
    PaddingValues(
        start = horizontal,
        end = horizontal,
        top = calculateTopPadding(),
        bottom = calculateBottomPadding(),
    )

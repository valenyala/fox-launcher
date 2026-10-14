package dev.foxlauncher.feature.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.apps.AppSearch
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.widgets.app.AppWidgetCatalog
import dev.foxlauncher.feature.widgets.app.AppWidgetGroup
import dev.foxlauncher.feature.widgets.app.AppWidgetOption
import dev.foxlauncher.feature.widgets.app.AppWidgetSizing
import dev.foxlauncher.feature.widgets.app.WidgetSpans

object WidgetPickerTestTags {
    const val SHEET = "widget_picker_sheet"
    const val FOX_TAB = "widget_picker_tab_fox"
    const val APPS_TAB = "widget_picker_tab_apps"
    const val SEARCH = "widget_picker_search"

    /** Starts the tag of every app group header and app widget row, in list order. */
    const val APP_ENTRY = "widget_picker_entry_"

    fun preview(kind: String) = "widget_picker_preview_$kind"

    fun appGroup(key: String) = "${APP_ENTRY}group_$key"

    fun appWidget(key: String) = "${APP_ENTRY}widget_$key"

    fun appPreview(key: String) = "widget_picker_app_preview_$key"
}

/** The size of one grid cell in the picker's previews, roughly a phone's. */
private val PREVIEW_CELL_WIDTH = 64.dp
private val PREVIEW_CELL_HEIGHT = 80.dp
private const val PREVIEW_BACKGROUND_ALPHA = 0.55f
private val PREVIEW_SHAPE = RoundedCornerShape(20.dp)

private enum class PickerTab(@param:StringRes val labelRes: Int, val testTag: String) {
    FOX(R.string.widgets_tab_fox, WidgetPickerTestTags.FOX_TAB),
    APPS(R.string.widgets_tab_apps, WidgetPickerTestTags.APPS_TAB),
}

/**
 * Widgets to add, in two tabs. Fox's native widgets (task 5.1): each with a live preview at its
 * default size, its name, what it does and its size in cells; tapping one calls [onPick]. The app
 * widgets of every profile (task 6.2): [appWidgets] grouped by app (null while loading), each
 * sized in cells of [cellSize] on [grid] and drawn by [appWidgetPreview]; tapping one calls
 * [onPickAppWidget] with its cells. Widgets too large for the grid are left out. A search field above
 * the tabs narrows both by app, widget name or description (user request, 2026-10-14).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    specs: List<NativeWidgetSpec>,
    onPick: (NativeWidgetSpec) -> Unit,
    onDismiss: () -> Unit,
    appWidgets: List<AppWidgetGroup>? = emptyList(),
    cellSize: DpSize = DpSize(PREVIEW_CELL_WIDTH, PREVIEW_CELL_HEIGHT),
    grid: GridSpec = GridSpec.DEFAULT,
    onPickAppWidget: (AppWidgetOption, WidgetSpans) -> Unit = { _, _ -> },
    appWidgetPreview: @Composable (AppWidgetOption) -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(PickerTab.FOX) }
    var query by rememberSaveable { mutableStateOf("") }
    val resources = LocalResources.current
    val shownSpecs = remember(specs, query, resources) {
        specs.filter { AppSearch.match(query, resources.getString(it.nameRes)) || AppSearch.match(query, resources.getString(it.descriptionRes)) }
    }
    val shownAppWidgets = remember(appWidgets, query) { appWidgets?.let { AppWidgetCatalog.search(it, query) } }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(WidgetPickerTestTags.SHEET)) {
        Text(
            stringResource(R.string.widgets_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        // There can be hundreds of app widgets (user request, 2026-10-14). It searches both tabs.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.widgets_search_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag(WidgetPickerTestTags.SEARCH),
        )
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            PickerTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(stringResource(entry.labelRes)) },
                    modifier = Modifier.testTag(entry.testTag),
                )
            }
        }
        when (tab) {
            PickerTab.FOX -> if (shownSpecs.isEmpty()) {
                EmptyText(stringResource(R.string.widgets_search_empty, query))
            } else {
                LazyColumn(Modifier.navigationBarsPadding().imePadding()) {
                    items(shownSpecs, key = { it.kind }) { spec -> PickerRow(spec, onClick = { onPick(spec) }) }
                }
            }
            PickerTab.APPS -> {
                // Before the home has measured its cells (never seen), a phone's will do.
                val cell = if (cellSize.isSpecified && cellSize.width > 0.dp && cellSize.height > 0.dp) cellSize else DpSize(PREVIEW_CELL_WIDTH, PREVIEW_CELL_HEIGHT)
                AppWidgetList(shownAppWidgets, query, cell, grid, onPickAppWidget, appWidgetPreview)
            }
        }
    }
}

@Composable
private fun PickerRow(spec: NativeWidgetSpec, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        // The preview is drawn over a dark tile, as it would sit on a wallpaper.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PREVIEW_CELL_WIDTH * spec.defaultSpan.x, PREVIEW_CELL_HEIGHT * spec.defaultSpan.y)
                .clip(PREVIEW_SHAPE)
                .background(Color.Black.copy(alpha = PREVIEW_BACKGROUND_ALPHA))
                .testTag(WidgetPickerTestTags.preview(spec.kind)),
        ) {
            Box(Modifier.clearAndSetSemantics { }) {
                spec.content(NativeWidgetScope(spec.defaultSpan.x, spec.defaultSpan.y, isPreview = true))
            }
            // Over the preview, so a tap on it adds the widget: a widget's own button (Boost) would otherwise take the tap
            // and nothing would happen (found on the POCO).
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(interactionSource = null, indication = null, onClick = onClick),
            )
        }
        Text(stringResource(spec.nameRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(R.string.widget_size, spec.defaultSpan.x, spec.defaultSpan.y) + " · " + stringResource(spec.descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppWidgetList(
    groups: List<AppWidgetGroup>?,
    query: String,
    cellSize: DpSize,
    grid: GridSpec,
    onPick: (AppWidgetOption, WidgetSpans) -> Unit,
    preview: @Composable (AppWidgetOption) -> Unit,
) {
    if (groups == null) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val density = LocalDensity.current
    val sized = remember(groups, cellSize, grid, density) {
        val cellWidth = with(density) { cellSize.width.toPx() }
        val cellHeight = with(density) { cellSize.height.toPx() }
        groups.mapNotNull { group ->
            val widgets = group.widgets.mapNotNull { option ->
                AppWidgetSizing.spans(option.size, cellWidth, cellHeight, grid)?.let { option to it }
            }
            if (widgets.isEmpty()) null else group to widgets
        }
    }
    if (sized.isEmpty()) {
        EmptyText(if (query.isBlank()) stringResource(R.string.widgets_apps_empty) else stringResource(R.string.widgets_search_empty, query))
        return
    }
    LazyColumn(Modifier.navigationBarsPadding().imePadding()) {
        sized.forEach { (group, widgets) ->
            item(key = "group:${group.key}") { AppGroupHeader(group) }
            items(widgets, key = { (option, _) -> option.key }) { (option, spans) ->
                AppWidgetRow(option, spans, cellSize, onClick = { onPick(option, spans) }, preview)
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.navigationBarsPadding().imePadding().padding(24.dp),
    )
}

@Composable
private fun AppGroupHeader(group: AppWidgetGroup) {
    Text(
        if (group.isWorkProfile) stringResource(R.string.widgets_app_work, group.appLabel) else group.appLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() }
            .testTag(WidgetPickerTestTags.appGroup(group.key)),
    )
}

@Composable
private fun AppWidgetRow(
    option: AppWidgetOption,
    spans: WidgetSpans,
    cellSize: DpSize,
    onClick: () -> Unit,
    preview: @Composable (AppWidgetOption) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(WidgetPickerTestTags.appWidget(option.key)),
    ) {
        // At its size on the home grid, scaled down when wider than the sheet: preview layouts are made for real cells.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val full = DpSize(cellSize.width * spans.default.x, cellSize.height * spans.default.y)
            val scale = (maxWidth / full.width).coerceAtMost(1f)
            Box(
                Modifier
                    .size(full * scale)
                    .clip(PREVIEW_SHAPE)
                    .background(Color.Black.copy(alpha = PREVIEW_BACKGROUND_ALPHA))
                    .testTag(WidgetPickerTestTags.appPreview(option.key)),
            ) {
                Box(
                    Modifier
                        .requiredSize(full)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clearAndSetSemantics { },
                ) { preview(option) }
                // Over the preview, as for native widgets: a widget's own buttons must not take the tap.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(interactionSource = null, indication = null, onClick = onClick),
                )
            }
        }
        Text(option.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        val size = stringResource(R.string.widget_size, spans.default.x, spans.default.y)
        Text(
            option.description?.let { "$size · $it" } ?: size,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

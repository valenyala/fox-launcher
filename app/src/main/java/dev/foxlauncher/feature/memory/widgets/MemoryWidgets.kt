package dev.foxlauncher.feature.memory.widgets

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.memory.BoostLimit
import dev.foxlauncher.feature.memory.MemoryWidgetViewModel
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The three memory widgets (task 5.3, ADR-8): stateless content fed by [MemoryWidgetViewModel]. */
object MemoryWidgets {

    val gauge = NativeWidgetSpec(
        kind = "memory_gauge",
        nameRes = R.string.widget_memory_gauge,
        descriptionRes = R.string.widget_memory_gauge_description,
        minSpan = Span(1, 1),
        defaultSpan = Span(2, 1),
        maxSpan = Span(3, 2),
        content = { MemoryGauge(it) },
    )

    val boost = NativeWidgetSpec(
        kind = "memory_boost",
        nameRes = R.string.widget_memory_boost,
        descriptionRes = R.string.widget_memory_boost_description,
        minSpan = Span(1, 1),
        defaultSpan = Span(1, 1),
        maxSpan = Span(1, 1),
        content = { MemoryBoost(it) },
    )

    val detail = NativeWidgetSpec(
        kind = "memory_detail",
        nameRes = R.string.widget_memory_detail,
        descriptionRes = R.string.widget_memory_detail_description,
        minSpan = Span(2, 2),
        defaultSpan = Span(2, 2),
        maxSpan = Span(4, 4),
        content = { MemoryDetail(it) },
    )

    /** From this many rows the detail widget lists recent apps. */
    const val RECENT_APPS_FROM_ROWS = 3
}

@Composable
private fun MemoryGauge(scope: NativeWidgetScope) {
    val viewModel: MemoryWidgetViewModel = hiltViewModel()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    MemoryGaugeContent(snapshot, scope.spanX)
}

/** The boost runs at least [MIN_BOOST_MS], so it reads as an action; its result stays for [RESULT_MS]. */
@Composable
private fun MemoryBoost(scope: NativeWidgetScope) {
    val viewModel: MemoryWidgetViewModel = hiltViewModel()
    val coroutines = rememberCoroutineScope()
    var state by remember { mutableStateOf<BoostUiState>(BoostUiState.Idle) }
    var explaining by remember { mutableStateOf(false) }
    MemoryBoostContent(
        state = state,
        limit = viewModel.boostLimit,
        onBoost = {
            // In the picker the whole preview is the button that adds the widget.
            if (scope.isPreview) return@MemoryBoostContent
            coroutines.launch {
                state = BoostUiState.Boosting
                val started = System.currentTimeMillis()
                val result = viewModel.boost()
                delay((MIN_BOOST_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                state = BoostUiState.Done(result)
                delay(RESULT_MS)
                state = BoostUiState.Idle
            }
        },
        onInfo = { if (!scope.isPreview) explaining = true },
    )
    val limit = viewModel.boostLimit
    if (explaining && limit != null) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = {
                Text(stringResource(if (limit == BoostLimit.MIUI) R.string.memory_boost_limits_miui_title else R.string.memory_boost_limits_title))
            },
            text = { Text(stringResource(if (limit == BoostLimit.MIUI) R.string.memory_boost_limits_miui else R.string.memory_boost_limits)) },
            confirmButton = { TextButton(onClick = { explaining = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun MemoryDetail(scope: NativeWidgetScope) {
    val viewModel: MemoryWidgetViewModel = hiltViewModel()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val showRecent = scope.spanY >= MemoryWidgets.RECENT_APPS_FROM_ROWS && !scope.isPreview
    // Usage stats are read only while the list is on screen.
    val recent = if (showRecent) viewModel.recent.collectAsStateWithLifecycle().value else null
    MemoryDetailContent(
        snapshot = snapshot,
        history = history,
        recent = recent,
        showRecent = showRecent,
        onAppInfo = { viewModel.openAppInfo(it, null) },
        onUsageAccess = viewModel::openUsageAccess,
    )
}

private const val MIN_BOOST_MS = 1_200L
private const val RESULT_MS = 4_000L

package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.R
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.clock.ClockWidget
import dev.foxlauncher.feature.widgets.widgetBackground
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object WidgetBackgroundTestTags {
    const val PREVIEW = "widget_background_preview"
    const val OPACITY = "widget_background_opacity"
}

@HiltViewModel
class WidgetBackgroundViewModel @Inject constructor(private val settings: FoxSettings) : ViewModel() {

    val opacity: StateFlow<Int> = settings.widgetBackgroundOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FoxSettings.DEFAULT_WIDGET_BACKGROUND_OPACITY)

    fun setOpacity(percent: Int) {
        viewModelScope.launch { settings.setWidgetBackgroundOpacity(percent) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun WidgetBackgroundScreen(viewModel: WidgetBackgroundViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val opacity by viewModel.opacity.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    WidgetBackgroundContent(opacity = opacity, onBack = onBack, onOpacityChange = viewModel::setOpacity, modifier = modifier)
}

/**
 * Widget background (task 5.6): how opaque the rounded surface behind Fox's own widgets is, from
 * none to solid black in 5% steps, with a Clock over a wallpaper-like backdrop as the preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetBackgroundContent(
    opacity: Int,
    onBack: () -> Unit,
    onOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit = { ClockWidget.spec.content(NativeWidgetScope(PREVIEW_SPAN_X, 1, isPreview = true)) },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_background_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // A stand-in for the wallpaper: the settings cover the home, so the real one is not behind them.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clip(RoundedCornerShape(PREVIEW_CORNER))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondaryContainer),
                        ),
                    ),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(PREVIEW_WIDTH, PREVIEW_TILE_HEIGHT)
                        .clip(RoundedCornerShape(WIDGET_CORNER))
                        .background(widgetBackground(opacity))
                        .testTag(WidgetBackgroundTestTags.PREVIEW),
                ) { preview() }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_background_opacity)) },
                trailingContent = { Text(stringResource(R.string.widget_background_value, opacity)) },
            )
            Slider(
                value = opacity.toFloat(),
                onValueChange = { onOpacityChange(snap(it)) },
                valueRange = FoxSettings.WIDGET_BACKGROUND_OPACITIES.first.toFloat()..FoxSettings.WIDGET_BACKGROUND_OPACITIES.last.toFloat(),
                steps = (FoxSettings.WIDGET_BACKGROUND_OPACITIES.last - FoxSettings.WIDGET_BACKGROUND_OPACITIES.first) / STEP_PERCENT - 1,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(WidgetBackgroundTestTags.OPACITY),
            )
            Text(
                stringResource(R.string.widget_background_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** The slider's value on the nearest step. */
private fun snap(value: Float): Int {
    val first = FoxSettings.WIDGET_BACKGROUND_OPACITIES.first
    return first + ((value - first) / STEP_PERCENT).roundToInt() * STEP_PERCENT
}

private const val STEP_PERCENT = 5
private const val PREVIEW_SPAN_X = 4
private val PREVIEW_HEIGHT = 160.dp
private val PREVIEW_CORNER = 28.dp
private val PREVIEW_WIDTH = 280.dp
private val PREVIEW_TILE_HEIGHT = 88.dp
private val WIDGET_CORNER = 20.dp

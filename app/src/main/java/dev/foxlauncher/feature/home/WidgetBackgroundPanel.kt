package dev.foxlauncher.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.data.settings.FoxSettings
import kotlin.math.roundToInt

object WidgetBackgroundPanelTestTags {
    const val PANEL = "widget_background_panel"
    const val SAME_AS_OTHERS = "widget_background_same"
    const val SLIDER = "widget_background_slider"
    const val DONE = "widget_background_done"
}

private val PANEL_MARGIN = 16.dp

/**
 * One widget's background (task 5.8), over the home with no scrim so the widget shows each change
 * live: a card at the bottom of the screen, or at the top when the widget ([anchor], its bounds in
 * the drag layer) sits in the lower half. [own] is the widget's own opacity in percent, null for
 * none. A Fox widget ([followsSetting]) without one follows the setting, [defaultOpacity], which
 * the switch restores; an app widget without one has no backdrop. Each change goes to [onChange];
 * Done, a tap outside the card or Back ([onDone]) keep what is shown.
 */
@Composable
fun WidgetBackgroundPanel(
    anchor: Rect,
    own: Int?,
    defaultOpacity: Int,
    followsSetting: Boolean,
    onChange: (Int?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    // No swallowPointers: consuming the slider's moves in a parent makes its drag give up (as with the resize handles).
    // Being on top keeps touches off the pages; the home's own gestures are off meanwhile.
    Layout(
        content = { PanelCard(own, defaultOpacity, followsSetting, onChange, onDone) },
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDone() } }
            .testTag(WidgetBackgroundPanelTestTags.PANEL),
    ) { measurables, constraints ->
        val margin = PANEL_MARGIN.roundToPx()
        val width = (constraints.maxWidth - 2 * margin).coerceAtLeast(0)
        val card = measurables.single().measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0))
        val atTop = anchor.center.y > constraints.maxHeight / 2f
        val y = if (atTop) topInset + margin else constraints.maxHeight - bottomInset - margin - card.height
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(margin, y.coerceAtLeast(0)) }
    }
}

@Composable
private fun PanelCard(own: Int?, defaultOpacity: Int, followsSetting: Boolean, onChange: (Int?) -> Unit, onDone: () -> Unit) {
    val shown = own ?: if (followsSetting) defaultOpacity else 0
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        // A tap on the card itself is not a tap outside.
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.popup_background), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.widget_background_value, shown), style = MaterialTheme.typography.labelLarge)
            }
            if (followsSetting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = own == null, role = Role.Switch) { same -> onChange(if (same) null else shown) }
                        .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                        .testTag(WidgetBackgroundPanelTestTags.SAME_AS_OTHERS),
                ) {
                    Text(
                        stringResource(R.string.widget_background_same, defaultOpacity),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = own == null, onCheckedChange = null)
                }
            }
            Slider(
                value = shown.toFloat(),
                onValueChange = { onChange(snap(it)) },
                valueRange = FoxSettings.WIDGET_BACKGROUND_OPACITIES.first.toFloat()..FoxSettings.WIDGET_BACKGROUND_OPACITIES.last.toFloat(),
                steps = (FoxSettings.WIDGET_BACKGROUND_OPACITIES.last - FoxSettings.WIDGET_BACKGROUND_OPACITIES.first) / STEP_PERCENT - 1,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag(WidgetBackgroundPanelTestTags.SLIDER),
            )
            if (!followsSetting) {
                Text(
                    stringResource(R.string.widget_background_app_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(WidgetBackgroundPanelTestTags.DONE),
            ) { Text(stringResource(R.string.widget_resize_done)) }
        }
    }
}

/** The slider's value on the nearest 5% step, as in the setting (task 5.6). */
private fun snap(value: Float): Int {
    val first = FoxSettings.WIDGET_BACKGROUND_OPACITIES.first
    return first + ((value - first) / STEP_PERCENT).roundToInt() * STEP_PERCENT
}

private const val STEP_PERCENT = 5

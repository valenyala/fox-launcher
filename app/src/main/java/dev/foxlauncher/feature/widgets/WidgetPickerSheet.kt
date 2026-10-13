package dev.foxlauncher.feature.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R

object WidgetPickerTestTags {
    const val SHEET = "widget_picker_sheet"

    fun preview(kind: String) = "widget_picker_preview_$kind"
}

/** The size of one grid cell in the picker's previews, roughly a phone's. */
private val PREVIEW_CELL_WIDTH = 64.dp
private val PREVIEW_CELL_HEIGHT = 80.dp
private const val PREVIEW_BACKGROUND_ALPHA = 0.55f

/**
 * Fox's native widgets to add (task 5.1): each with a live preview at its default size, its name,
 * what it does and its size in cells. Tapping one calls [onPick]; the home places it. App widgets
 * join in phase 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(specs: List<NativeWidgetSpec>, onPick: (NativeWidgetSpec) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(WidgetPickerTestTags.SHEET)) {
        Text(
            stringResource(R.string.widgets_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(specs, key = { it.kind }) { spec -> PickerRow(spec, onClick = { onPick(spec) }) }
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
                .clip(RoundedCornerShape(20.dp))
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

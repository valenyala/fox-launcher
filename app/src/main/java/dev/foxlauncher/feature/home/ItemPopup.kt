package dev.foxlauncher.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppShortcut
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val POPUP_WIDTH = 220.dp
private val POPUP_GAP = 8.dp
private val POPUP_MARGIN = 8.dp

private val SHORTCUT_ICON_SIZE = 28.dp

/** One of the app's shortcuts in its popup, with its icon when one could be loaded. */
@Immutable
data class PopupShortcut(val shortcut: AppShortcut, val icon: ImageBitmap?)

/** What a home item's popup offers, loaded when it opens (task 2.6). Remove is always there. */
@Immutable
data class ItemPopupModel(
    val shortcuts: ImmutableList<PopupShortcut> = persistentListOf(),
    val canOpenAppInfo: Boolean = false,
    val canUninstall: Boolean = false,
)

/** A choice made in a home item's popup. */
sealed interface PopupAction {
    data object AppInfo : PopupAction
    data object Uninstall : PopupAction

    /** Off the home screen; the app stays installed. */
    data object Remove : PopupAction

    data class OpenShortcut(val shortcut: AppShortcut) : PopupAction
}

/**
 * A home item's long-press popup next to [anchor], the item's bounds in the home drag layer:
 * above it when there is room, otherwise below, and always on screen. It is drawn inside the drag
 * layer rather than in a window of its own, so a finger that moves on from the long press still
 * drags the item, and a shortcut row can be long-pressed and dragged out ([onShortcutLongPress]).
 * Any tap outside the card calls [onDismiss].
 */
@Composable
fun ItemPopup(
    anchor: Rect,
    model: ItemPopupModel,
    onAction: (PopupAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShortcutLongPress: (AppShortcut) -> Unit = {},
) {
    val topInset = WindowInsets.statusBars.getTop(LocalDensity.current)
    Layout(
        content = { PopupCard(model, onAction, onShortcutLongPress) },
        modifier = modifier
            .fillMaxSize()
            .swallowPointers()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag(HomeTestTags.ITEM_POPUP),
    ) { measurables, constraints ->
        val card = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = POPUP_MARGIN.roundToPx()
        val gap = POPUP_GAP.roundToPx()
        val maxX = (constraints.maxWidth - margin - card.width).coerceAtLeast(margin)
        val x = (anchor.center.x - card.width / 2f).roundToInt().coerceIn(margin, maxX)
        val above = anchor.top.roundToInt() - gap - card.height
        val y = if (above >= topInset + margin) {
            above
        } else {
            (anchor.bottom.roundToInt() + gap).coerceAtMost(constraints.maxHeight - margin - card.height).coerceAtLeast(margin)
        }
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(x, y) }
    }
}

@Composable
private fun PopupCard(model: ItemPopupModel, onAction: (PopupAction) -> Unit, onShortcutLongPress: (AppShortcut) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        // Taps on the card's own padding must not reach the dismiss detector behind it.
        modifier = Modifier
            .width(POPUP_WIDTH)
            .swallowPointers(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            // The app's own shortcuts first, then what Fox can do with the item, as in Launcher3.
            model.shortcuts.forEach { (shortcut, icon) ->
                ShortcutRow(shortcut, icon, onLongClick = { onShortcutLongPress(shortcut) }) {
                    onAction(PopupAction.OpenShortcut(shortcut))
                }
            }
            if (model.shortcuts.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (model.canOpenAppInfo) PopupRow(stringResource(R.string.popup_app_info)) { onAction(PopupAction.AppInfo) }
            PopupRow(stringResource(R.string.home_remove)) { onAction(PopupAction.Remove) }
            if (model.canUninstall) PopupRow(stringResource(R.string.popup_uninstall)) { onAction(PopupAction.Uninstall) }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: AppShortcut, icon: ImageBitmap?, onLongClick: () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(SHORTCUT_ICON_SIZE))
        } else {
            Spacer(Modifier.size(SHORTCUT_ICON_SIZE))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = shortcut.longLabel?.takeIf { it.isNotBlank() } ?: shortcut.shortLabel,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PopupRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

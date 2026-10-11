package dev.foxlauncher.feature.home

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.drag.HomeDragState

private const val SCRIM_ALPHA = 0.35f

/** Dark glass, so the white wallpaper-style labels stay readable whatever the theme. */
private const val CARD_ALPHA = 0.6f
private val CARD_MAX_WIDTH = 360.dp
private val CARD_MARGIN = 24.dp

/**
 * An open folder (task 2.5): a centred card over the dimmed home, with an editable title and the
 * folder's items in a grid. It is drawn inside the home drag layer, so its items drag like home
 * items: within the grid to reorder (the others slide aside), or off the card, which closes the
 * folder so the item can land on the home screen. Tapping the dimmed area calls [onClose];
 * [onTitleChange] receives the title when the sheet goes away, whatever closed it.
 */
@Composable
fun FolderSheet(
    folder: HomeItem.Folder,
    dragState: HomeDragState,
    cellHeight: Dp,
    onClose: () -> Unit,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (HomeItem) -> Unit,
) {
    val title = rememberTextFieldState(folder.title)
    val currentOnTitleChange by rememberUpdatedState(onTitleChange)
    DisposableEffect(Unit) { onDispose { currentOnTitleChange(title.text.toString().trim()) } }
    val focusManager = LocalFocusManager.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            // Outer, so the tap detector below it sees each event before it is swallowed.
            .swallowPointers()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .imePadding()
            .testTag(HomeTestTags.FOLDER_SHEET),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = CARD_ALPHA),
            contentColor = Color.White,
            modifier = Modifier
                .padding(CARD_MARGIN)
                .widthIn(max = CARD_MAX_WIDTH)
                .fillMaxWidth()
                .onPlaced(dragState::registerFolderCard)
                .swallowPointers()
                .testTag(HomeTestTags.FOLDER_CARD),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                BasicTextField(
                    state = title,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White, textAlign = TextAlign.Center),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    onKeyboardAction = { focusManager.clearFocus() },
                    cursorBrush = SolidColor(Color.White),
                    decorator = { innerTextField ->
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                            if (title.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.folder_untitled),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HomeTestTags.FOLDER_TITLE),
                )
                FolderGrid(folder, dragState, cellHeight, itemContent)
            }
        }
    }
}

/**
 * The folder's items, [folderColumns] wide. While one of them is dragged over the grid the others
 * slide aside to where they would be after the drop, and stay there while it settles.
 */
@Composable
private fun FolderGrid(
    folder: HomeItem.Folder,
    dragState: HomeDragState,
    cellHeight: Dp,
    itemContent: @Composable (HomeItem) -> Unit,
) {
    val items = folder.items
    val cols = folderColumns(items.size)
    val rows = (items.size + cols - 1) / cols
    val dragged = dragState.dragging
    val reorder = (dragState.preview?.action ?: dragState.droppedAction) as? DropAction.MoveInFolder
    val order = if (dragged != null && reorder != null) {
        val byId = items.associateBy { it.id }
        moveToRank(items.map { it.id }, dragged.id, reorder.rank).mapNotNull(byId::get)
    } else {
        items
    }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(cellHeight * rows)
            .onPlaced(dragState::registerFolderGrid)
            .testTag(HomeTestTags.FOLDER_GRID),
    ) {
        val cellWidth = maxWidth / cols
        order.forEachIndexed { index, item ->
            key(item.id) {
                val target = with(density) {
                    IntOffset((cellWidth * (index % cols)).roundToPx(), (cellHeight * (index / cols)).roundToPx())
                }
                val offset by animateIntOffsetAsState(target, label = "folderItem")
                Box(
                    Modifier
                        .offset { offset }
                        .size(cellWidth, cellHeight),
                ) {
                    itemContent(item)
                }
            }
        }
    }
}

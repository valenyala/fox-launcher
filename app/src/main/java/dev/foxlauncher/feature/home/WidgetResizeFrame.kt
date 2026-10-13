package dev.foxlauncher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.ResizeEdge
import kotlin.math.roundToInt

object WidgetResizeTestTags {
    const val FRAME = "widget_resize_frame"

    fun handle(edge: ResizeEdge) = "widget_resize_handle_$edge"
}

private val HANDLE_SIZE = 24.dp
private val HANDLE_TOUCH = 48.dp
private val FRAME_STROKE = 2.dp
private val FRAME_CORNER = 20.dp

/**
 * A widget's resize handles (task 5.1), over the home drag layer: a frame at [bounds] (the widget's
 * bounds in the layer, covering [cell]) with a handle in the middle of each edge that can move.
 * Dragging a handle moves that edge by whole cells; [resize] says how far it may go (see
 * `WidgetResize`), and the frame shows it live. Letting go commits it through [onResize]. A tap
 * anywhere else, like Back, calls [onDismiss]. The home under it takes no touches meanwhile.
 */
@Composable
fun WidgetResizeFrame(
    bounds: Rect,
    cell: CellRect,
    horizontal: Boolean,
    vertical: Boolean,
    resize: (ResizeEdge, Int) -> CellRect,
    onResize: (CellRect) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellW = bounds.width / cell.spanX
    val cellH = bounds.height / cell.spanY
    var preview by remember(cell) { mutableStateOf(cell) }
    val currentResize by rememberUpdatedState(resize)
    val currentOnResize by rememberUpdatedState(onResize)
    val frame = Rect(
        left = bounds.left + (preview.col - cell.col) * cellW,
        top = bounds.top + (preview.row - cell.row) * cellH,
        right = bounds.left + (preview.col - cell.col + preview.spanX) * cellW,
        bottom = bounds.top + (preview.row - cell.row + preview.spanY) * cellH,
    )
    val density = LocalDensity.current
    // No swallowPointers here: it would consume a handle's first small moves in the Main pass, and the handle's drag
    // detector, seeing them consumed before touch slop, would give up (found on fox-api33). Being on top already keeps
    // touches off the home, whose own gestures are off while resizing.
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag(WidgetResizeTestTags.FRAME),
    ) {
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                    .size(frame.width.toDp(), frame.height.toDp())
                    .border(FRAME_STROKE, MaterialTheme.colorScheme.primary, RoundedCornerShape(FRAME_CORNER)),
            )
        }
        val edges = buildList {
            if (horizontal) addAll(listOf(ResizeEdge.LEFT, ResizeEdge.RIGHT))
            if (vertical) addAll(listOf(ResizeEdge.TOP, ResizeEdge.BOTTOM))
        }
        edges.forEach { edge ->
            val center = when (edge) {
                ResizeEdge.LEFT -> Offset(frame.left, frame.center.y)
                ResizeEdge.RIGHT -> Offset(frame.right, frame.center.y)
                ResizeEdge.TOP -> Offset(frame.center.x, frame.top)
                ResizeEdge.BOTTOM -> Offset(frame.center.x, frame.bottom)
            }
            val touch = with(density) { HANDLE_TOUCH.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((center.x - touch / 2).roundToInt(), (center.y - touch / 2).roundToInt()) }
                    .size(HANDLE_TOUCH)
                    // A light tap on a handle is not a tap outside: it must not dismiss the frame.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .pointerInput(edge, cell) {
                        var dragged = Offset.Zero
                        detectDragGestures(
                            onDragStart = { dragged = Offset.Zero },
                            onDragEnd = {
                                FoxLog.d("Widget resized: $cell → $preview")
                                if (preview != cell) currentOnResize(preview)
                            },
                            onDragCancel = { preview = cell },
                        ) { change, delta ->
                            change.consume()
                            dragged += delta
                            val cells = when (edge) {
                                ResizeEdge.RIGHT -> dragged.x / cellW
                                ResizeEdge.LEFT -> -dragged.x / cellW
                                ResizeEdge.BOTTOM -> dragged.y / cellH
                                ResizeEdge.TOP -> -dragged.y / cellH
                            }
                            preview = currentResize(edge, cells.roundToInt())
                        }
                    }
                    .testTag(WidgetResizeTestTags.handle(edge)),
            ) {
                Box(
                    Modifier
                        .offset((HANDLE_TOUCH - HANDLE_SIZE) / 2, (HANDLE_TOUCH - HANDLE_SIZE) / 2)
                        .size(HANDLE_SIZE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(FRAME_STROKE, MaterialTheme.colorScheme.onPrimary, CircleShape),
                )
            }
        }
    }
}

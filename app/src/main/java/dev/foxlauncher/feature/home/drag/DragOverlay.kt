package dev.foxlauncher.feature.home.drag

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.foxlauncher.core.model.HomeItem

private const val DRAG_SCALE = 1.1f
private const val MOVE_FILL_ALPHA = 0.18f
private const val MERGE_FILL_ALPHA = 0.3f
private val HIGHLIGHT_INSET = 4.dp
private val HIGHLIGHT_CORNER = 16.dp
private val MERGE_STROKE = 2.dp

/**
 * The dragged item, following the finger slightly enlarged, then gliding into place at its
 * normal size. [content] draws it exactly like the grid does.
 */
@Composable
fun DragOverlay(state: HomeDragState, content: @Composable (HomeItem) -> Unit) {
    val item = state.dragging ?: return
    val scale by animateFloatAsState(if (state.isSettling) 1f else DRAG_SCALE, label = "dragScale")
    val size = with(LocalDensity.current) { state.itemSize.toDpSize() }
    Box(
        Modifier
            .offset { state.overlayTopLeft.round() }
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        content(item)
    }
}

/**
 * Marks where a release would land: a soft tile on the destination cell or dock slot, or an
 * outlined, stronger tile on the item a drop would merge into a folder. Drawn below the grid,
 * so the merge target's icon sits on top of it.
 */
@Composable
fun DropHighlight(state: HomeDragState, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                val bounds = state.highlightBounds() ?: return@drawBehind
                val inset = HIGHLIGHT_INSET.toPx()
                val topLeft = Offset(bounds.left + inset, bounds.top + inset)
                val size = bounds.size.copy(width = bounds.width - 2 * inset, height = bounds.height - 2 * inset)
                val corner = CornerRadius(HIGHLIGHT_CORNER.toPx())
                val merge = state.preview?.action.let { it is DropAction.CreateFolder || it is DropAction.AddToFolder }
                val fill = if (merge) MERGE_FILL_ALPHA else MOVE_FILL_ALPHA
                drawRoundRect(Color.White.copy(alpha = fill), topLeft, size, corner)
                if (merge) drawRoundRect(Color.White, topLeft, size, corner, style = Stroke(MERGE_STROKE.toPx()))
            },
    )
}

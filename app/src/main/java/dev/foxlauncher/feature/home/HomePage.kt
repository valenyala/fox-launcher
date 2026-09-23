package dev.foxlauncher.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import kotlin.math.roundToInt

/**
 * One home page: a custom [Layout] that places each item at its absolute cell (PLATFORM-NOTES C:
 * no lazy grid, items have fixed cells and spans). Cell size is the available size divided by the
 * grid; placement comes from [GridLayoutEngine.pxOf].
 */
@Composable
fun HomePage(
    items: List<HomeItem>,
    engine: GridLayoutEngine,
    modifier: Modifier = Modifier,
    itemContent: @Composable (HomeItem) -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            items.forEach { item ->
                key(item.id) {
                    val p = item.position
                    Box(Modifier.layoutId(CellRect(p.col, p.row, p.spanX, p.spanY))) {
                        itemContent(item)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val cellW = width.toFloat() / engine.spec.cols
        val cellH = height.toFloat() / engine.spec.rows
        val placeables = measurables.map { measurable ->
            val cell = measurable.layoutId as CellRect
            val px = engine.pxOf(cell, cellW, cellH)
            val w = px.width.roundToInt()
            val h = px.height.roundToInt()
            measurable.measure(Constraints.fixed(w, h)) to px
        }
        layout(width, height) {
            placeables.forEach { (placeable, px) -> placeable.place(px.left.roundToInt(), px.top.roundToInt()) }
        }
    }
}

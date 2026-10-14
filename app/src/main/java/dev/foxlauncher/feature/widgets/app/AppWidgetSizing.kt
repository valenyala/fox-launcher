package dev.foxlauncher.feature.widgets.app

import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.Span
import kotlin.math.ceil

/**
 * A provider's sizes as `AppWidgetProviderInfo` gives them: lengths in px for this display, the
 * target cells and max resize sizes from API 31 (0 when unset or older), and its `resizeMode`.
 */
data class ProviderSize(
    val minWidth: Int,
    val minHeight: Int,
    val minResizeWidth: Int = minWidth,
    val minResizeHeight: Int = minHeight,
    val maxResizeWidth: Int = 0,
    val maxResizeHeight: Int = 0,
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val resizesHorizontally: Boolean = false,
    val resizesVertically: Boolean = false,
)

/** An app widget's size in cells when added ([default]) and the range it may be resized within. */
data class WidgetSpans(val default: Span, val min: Span, val max: Span)

/** Cells for a third-party widget on Fox's grid (task 6.2). Pure, so it is unit tested. */
object AppWidgetSizing {

    /**
     * [size] in cells of [cellWidth] × [cellHeight] px on [grid], or null when even its smallest
     * size does not fit the grid. Launcher3's rules, with no gap between Fox's cells: a length
     * takes the cells it needs, at least one; the target cells (API 31) win when they lie within
     * the resize range; the minimum never exceeds the default (the documented `minResize*`
     * behaviour); a direction that `resizeMode` does not allow keeps the default.
     */
    fun spans(size: ProviderSize, cellWidth: Float, cellHeight: Float, grid: GridSpec): WidgetSpans? {
        require(cellWidth > 0f && cellHeight > 0f) { "Cell size must be positive: $cellWidth × $cellHeight" }
        val x = Axis(
            min = cells(size.minResizeWidth.takeIf { it > 0 } ?: size.minWidth, cellWidth),
            max = if (size.maxResizeWidth > 0) cells(size.maxResizeWidth, cellWidth) else grid.cols,
            default = cells(size.minWidth, cellWidth),
            target = size.targetCellWidth,
            resizable = size.resizesHorizontally,
            limit = grid.cols,
        )
        val y = Axis(
            min = cells(size.minResizeHeight.takeIf { it > 0 } ?: size.minHeight, cellHeight),
            max = if (size.maxResizeHeight > 0) cells(size.maxResizeHeight, cellHeight) else grid.rows,
            default = cells(size.minHeight, cellHeight),
            target = size.targetCellHeight,
            resizable = size.resizesVertically,
            limit = grid.rows,
        )
        // Both target cells or neither, as Launcher3 does.
        val useTarget = x.targetFits && y.targetFits
        val (defaultX, minX, maxX) = x.resolve(useTarget) ?: return null
        val (defaultY, minY, maxY) = y.resolve(useTarget) ?: return null
        return WidgetSpans(Span(defaultX, defaultY), Span(minX, minY), Span(maxX, maxY))
    }

    private fun cells(px: Int, cell: Float): Int = ceil(px / cell).toInt().coerceAtLeast(1)

    private class Axis(val min: Int, max: Int, val default: Int, val target: Int, val resizable: Boolean, val limit: Int) {
        val max = max.coerceAtLeast(min)
        val targetFits = target in min..this.max

        /** Default, min and max, within the grid; null when the smallest size is wider than the grid. */
        fun resolve(useTarget: Boolean): Triple<Int, Int, Int>? {
            val wanted = if (useTarget) target else default
            val smallest = minOf(wanted, min)
            if (smallest > limit) return null
            val chosen = wanted.coerceAtMost(limit)
            if (!resizable) return Triple(chosen, chosen, chosen)
            val largest = max.coerceIn(chosen, limit)
            return Triple(chosen, smallest, largest)
        }
    }
}

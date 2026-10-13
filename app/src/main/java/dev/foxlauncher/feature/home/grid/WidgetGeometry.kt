package dev.foxlauncher.feature.home.grid

import kotlin.math.abs

/** A widget's size in cells: [x] columns by [y] rows. */
data class Span(val x: Int, val y: Int) {
    init {
        require(x > 0 && y > 0) { "Span must be positive: $this" }
    }
}

/** The edge of a widget being dragged to resize it. */
enum class ResizeEdge { LEFT, TOP, RIGHT, BOTTOM }

/** Resizing a widget on the grid (task 5.1). Pure, so it is unit tested. */
object WidgetResize {

    /**
     * [rect] with its [edge] moved by [cells] (positive grows it, negative shrinks it) while the
     * opposite edge stays put. It moves a cell at a time and stops before the first step that would
     * leave [min]..[max], leave the grid, or cover a taken cell of [occupied], which must not
     * include the widget itself. So the result is always a valid placement, possibly [rect] itself.
     */
    fun resized(
        engine: GridLayoutEngine,
        rect: CellRect,
        edge: ResizeEdge,
        cells: Int,
        min: Span,
        max: Span,
        occupied: Array<BooleanArray>,
    ): CellRect {
        val step = if (cells >= 0) 1 else -1
        var result = rect
        for (n in 1..abs(cells)) {
            val candidate = rect.withEdgeMoved(edge, n * step)
            val withinSpan = candidate.spanX in min.x..max.x && candidate.spanY in min.y..max.y
            if (!withinSpan || !engine.isFree(candidate, occupied)) break
            result = candidate
        }
        return result
    }

    private fun CellRect.withEdgeMoved(edge: ResizeEdge, by: Int): CellRect = when (edge) {
        ResizeEdge.LEFT -> copy(col = col - by, spanX = spanX + by)
        ResizeEdge.TOP -> copy(row = row - by, spanY = spanY + by)
        ResizeEdge.RIGHT -> copy(spanX = spanX + by)
        ResizeEdge.BOTTOM -> copy(spanY = spanY + by)
    }
}

/** Where a newly added widget goes (task 5.1). Pure, so it is unit tested. */
object WidgetPlacement {

    /**
     * The page and cells for a new [span] widget: the first free block on [preferredPage], else on
     * the other pages in order, else on a new page after the last. [pages] lists each existing
     * page's taken blocks, by index; [pageCount] is how many pages there are. A widget larger than
     * the grid is shrunk to fit it.
     */
    fun place(engine: GridLayoutEngine, pages: Map<Int, List<CellRect>>, pageCount: Int, preferredPage: Int, span: Span): Pair<Int, CellRect> {
        val spanX = span.x.coerceAtMost(engine.spec.cols)
        val spanY = span.y.coerceAtMost(engine.spec.rows)
        val order = listOf(preferredPage) + (0 until pageCount).filter { it != preferredPage }
        for (page in order.filter { it in 0 until pageCount }) {
            engine.firstFree(spanX, spanY, engine.occupancy(pages[page].orEmpty()))?.let { return page to it }
        }
        return pageCount to CellRect(0, 0, spanX, spanY)
    }
}

package dev.foxlauncher.feature.home.grid

/** Home grid dimensions: [cols] × [rows] cells per page and [dockSlots] 1×1 dock slots. */
data class GridSpec(val cols: Int, val rows: Int, val dockSlots: Int) {
    init {
        require(cols > 0 && rows > 0 && dockSlots > 0) { "GridSpec dimensions must be positive: $this" }
    }

    companion object {
        /** Phone default (5 columns × 6 rows, 5 dock slots). */
        val DEFAULT = GridSpec(cols = 5, rows = 6, dockSlots = 5)
    }
}

/** A block of cells: top-left at ([col], [row]), [spanX] wide, [spanY] tall. */
data class CellRect(val col: Int, val row: Int, val spanX: Int = 1, val spanY: Int = 1) {
    val right: Int get() = col + spanX
    val bottom: Int get() = row + spanY

    fun overlaps(other: CellRect): Boolean =
        col < other.right && other.col < right && row < other.bottom && other.row < bottom
}

/** A rectangle in pixels relative to the page's top-left corner. */
data class PxRect(val left: Float, val top: Float, val width: Float, val height: Float)

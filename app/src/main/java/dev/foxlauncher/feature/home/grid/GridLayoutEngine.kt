package dev.foxlauncher.feature.home.grid

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * All home-grid placement math. Pure and stateless so drag and drop, first-run layout and
 * rendering agree on one definition of "where things go". Occupancy is indexed `[row][col]`.
 */
class GridLayoutEngine(val spec: GridSpec) {

    /** Which cells [items] cover. Parts of an item outside the grid are ignored. */
    fun occupancy(items: List<CellRect>): Array<BooleanArray> {
        val occ = Array(spec.rows) { BooleanArray(spec.cols) }
        for (item in items) {
            for (r in max(item.row, 0) until minOf(item.bottom, spec.rows)) {
                for (c in max(item.col, 0) until minOf(item.right, spec.cols)) occ[r][c] = true
            }
        }
        return occ
    }

    fun fits(rect: CellRect): Boolean =
        rect.spanX > 0 && rect.spanY > 0 && rect.col >= 0 && rect.row >= 0 &&
            rect.right <= spec.cols && rect.bottom <= spec.rows

    /** True when [rect] lies inside the grid and none of its cells is taken. */
    fun isFree(rect: CellRect, occ: Array<BooleanArray>): Boolean {
        if (!fits(rect)) return false
        for (r in rect.row until rect.bottom) {
            for (c in rect.col until rect.right) if (occ[r][c]) return false
        }
        return true
    }

    /** The first free block in reading order (row by row, left to right), or null when none fits. */
    fun firstFree(spanX: Int, spanY: Int, occ: Array<BooleanArray>): CellRect? {
        for (r in 0..spec.rows - spanY) {
            for (c in 0..spec.cols - spanX) {
                val rect = CellRect(c, r, spanX, spanY)
                if (isFree(rect, occ)) return rect
            }
        }
        return null
    }

    /** The 1×1 cell under a point, or null when the point is outside the grid. */
    fun cellAt(px: Float, py: Float, cellW: Float, cellH: Float): CellRect? {
        if (px < 0f || py < 0f) return null
        val col = floor(px / cellW).toInt()
        val row = floor(py / cellH).toInt()
        return if (col < spec.cols && row < spec.rows) CellRect(col, row) else null
    }

    fun pxOf(cell: CellRect, cellW: Float, cellH: Float): PxRect =
        PxRect(cell.col * cellW, cell.row * cellH, cell.spanX * cellW, cell.spanY * cellH)

    /**
     * The free [spanX]×[spanY] block whose top-left is closest to [target]'s, searching outward
     * ring by ring (a square spiral). Within a ring the straight-line nearest wins, then the
     * upper, then the left one, so the result is deterministic. Null when nothing fits anywhere.
     */
    fun nearestFreeTo(target: CellRect, spanX: Int, spanY: Int, occ: Array<BooleanArray>): CellRect? {
        val maxRing = max(spec.cols, spec.rows)
        for (ring in 0..maxRing) {
            var best: CellRect? = null
            var bestDist = Int.MAX_VALUE
            for (r in target.row - ring..target.row + ring) {
                for (c in target.col - ring..target.col + ring) {
                    if (max(abs(r - target.row), abs(c - target.col)) != ring) continue
                    val rect = CellRect(c, r, spanX, spanY)
                    if (!isFree(rect, occ)) continue
                    val dist = (r - target.row) * (r - target.row) + (c - target.col) * (c - target.col)
                    // Rows then columns are scanned in increasing order, so strict < keeps the upper-left on ties.
                    if (dist < bestDist) {
                        best = rect
                        bestDist = dist
                    }
                }
            }
            if (best != null) return best
        }
        return null
    }

    /** The dock slot under [px] for a dock [dockWidth] wide, or null when outside it. */
    fun dockSlotAt(px: Float, dockWidth: Float): Int? {
        if (px < 0f || px >= dockWidth) return null
        return floor(px / (dockWidth / spec.dockSlots)).toInt().coerceIn(0, spec.dockSlots - 1)
    }
}

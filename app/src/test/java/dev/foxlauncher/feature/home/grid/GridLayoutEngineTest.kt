package dev.foxlauncher.feature.home.grid

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class GridLayoutEngineTest {

    private val engine = GridLayoutEngine(GridSpec(cols = 4, rows = 5, dockSlots = 4))

    @Test
    fun emptyGridIsAllFree() {
        val occ = engine.occupancy(emptyList())
        assertTrue(occ.all { row -> row.none { it } })
        assertEquals(CellRect(0, 0), engine.firstFree(1, 1, occ))
    }

    @Test
    fun occupancyCoversSpans() {
        val occ = engine.occupancy(listOf(CellRect(1, 2, spanX = 2, spanY = 2)))
        val taken = occ.indices.flatMap { r -> occ[r].indices.filter { occ[r][it] }.map { c -> c to r } }
        assertEquals(listOf(1 to 2, 2 to 2, 1 to 3, 2 to 3), taken)
    }

    @Test
    fun occupancyClipsItemsHangingOffTheGrid() {
        val occ = engine.occupancy(listOf(CellRect(3, 4, spanX = 3, spanY = 3)))
        assertTrue(occ[4][3])
        assertEquals(1, occ.sumOf { row -> row.count { it } })
    }

    @Test
    fun fullGridHasNoFreeCell() {
        val occ = engine.occupancy(listOf(CellRect(0, 0, spanX = 4, spanY = 5)))
        assertNull(engine.firstFree(1, 1, occ))
        assertNull(engine.nearestFreeTo(CellRect(2, 2), 1, 1, occ))
    }

    @Test
    fun isFreeRejectsOverlapAndOutOfBounds() {
        val occ = engine.occupancy(listOf(CellRect(1, 1)))
        assertFalse(engine.isFree(CellRect(0, 0, spanX = 2, spanY = 2), occ))
        assertTrue(engine.isFree(CellRect(2, 0, spanX = 2, spanY = 2), occ))
        assertFalse(engine.isFree(CellRect(3, 0, spanX = 2), occ), "past the right edge")
        assertFalse(engine.isFree(CellRect(0, 4, spanY = 2), occ), "past the bottom edge")
        assertFalse(engine.isFree(CellRect(-1, 0), occ), "negative column")
    }

    @Test
    fun firstFreeScansInReadingOrder() {
        val occ = engine.occupancy(listOf(CellRect(0, 0), CellRect(1, 0)))
        assertEquals(CellRect(2, 0), engine.firstFree(1, 1, occ))
    }

    @Test
    fun firstFreeFindsRoomForASpan() {
        // Row 0 has only single gaps, so a 2×2 has to start on row 1.
        val occ = engine.occupancy(listOf(CellRect(1, 0), CellRect(3, 0)))
        assertEquals(CellRect(0, 1, 2, 2), engine.firstFree(2, 2, occ))
    }

    @Test
    fun firstFreeReturnsNullWhenASpanCannotFit() {
        assertNull(engine.firstFree(5, 1, engine.occupancy(emptyList())))
    }

    @Test
    fun cellAtMapsPixelsToCells() {
        assertEquals(CellRect(0, 0), engine.cellAt(0f, 0f, 100f, 120f))
        assertEquals(CellRect(2, 1), engine.cellAt(250f, 130f, 100f, 120f))
        assertEquals(CellRect(3, 4), engine.cellAt(399.9f, 599.9f, 100f, 120f))
    }

    @Test
    fun cellAtIsNullOutsideTheGrid() {
        assertNull(engine.cellAt(-1f, 10f, 100f, 120f))
        assertNull(engine.cellAt(400f, 10f, 100f, 120f))
        assertNull(engine.cellAt(10f, 600f, 100f, 120f))
    }

    @Test
    fun pxOfIsTheInverseOfCellAt() {
        val rect = engine.pxOf(CellRect(2, 3, spanX = 2), 100f, 120f)
        assertEquals(PxRect(200f, 360f, 200f, 120f), rect)
        assertEquals(CellRect(2, 3), engine.cellAt(rect.left, rect.top, 100f, 120f))
    }

    @Test
    fun nearestFreeReturnsTheTargetWhenItIsFree() {
        assertEquals(CellRect(2, 2), engine.nearestFreeTo(CellRect(2, 2), 1, 1, engine.occupancy(emptyList())))
    }

    @Test
    fun nearestFreePrefersOrthogonalNeighboursOverDiagonals() {
        // Target taken; ring 1 has free orthogonal and diagonal cells. Orthogonal is nearer.
        val occ = engine.occupancy(listOf(CellRect(2, 2), CellRect(2, 1)))
        assertEquals(CellRect(1, 2), engine.nearestFreeTo(CellRect(2, 2), 1, 1, occ))
    }

    @Test
    fun nearestFreeSearchesOutwardRingByRing() {
        // Everything within ring 1 of (1,1) is taken; the answer must come from ring 2, where
        // (3,1) and (1,3) are equally near and the upper one wins the tie.
        val occ = engine.occupancy(listOf(CellRect(0, 0, spanX = 3, spanY = 3)))
        assertEquals(CellRect(3, 1), engine.nearestFreeTo(CellRect(1, 1), 1, 1, occ))
    }

    @Test
    fun nearestFreeRespectsSpans() {
        val occ = engine.occupancy(listOf(CellRect(0, 0), CellRect(3, 3)))
        val found = engine.nearestFreeTo(CellRect(0, 0), 2, 2, occ)
        assertEquals(CellRect(1, 0, 2, 2), found)
    }

    @Test
    fun dockSlotAtSplitsTheDockEvenly() {
        assertEquals(0, engine.dockSlotAt(0f, 400f))
        assertEquals(1, engine.dockSlotAt(150f, 400f))
        assertEquals(3, engine.dockSlotAt(399f, 400f))
        assertNull(engine.dockSlotAt(-1f, 400f))
        assertNull(engine.dockSlotAt(400f, 400f))
    }

    @Test
    fun overlapsIsSymmetricAndExclusiveOfEdges() {
        val a = CellRect(0, 0, 2, 2)
        assertTrue(a.overlaps(CellRect(1, 1)) && CellRect(1, 1).overlaps(a))
        assertFalse(a.overlaps(CellRect(2, 0)), "touching edges do not overlap")
    }

    @Test
    fun specRejectsEmptyDimensions() {
        assertFailsWith<IllegalArgumentException> { GridSpec(cols = 0, rows = 5, dockSlots = 4) }
    }
}

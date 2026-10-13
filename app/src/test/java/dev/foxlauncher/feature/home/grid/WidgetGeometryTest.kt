package dev.foxlauncher.feature.home.grid

import kotlin.test.assertEquals
import org.junit.Test

class WidgetGeometryTest {

    private val engine = GridLayoutEngine(GridSpec(cols = 5, rows = 6, dockSlots = 5))
    private val min = Span(2, 1)
    private val max = Span(5, 2)
    private val widget = CellRect(col = 1, row = 2, spanX = 3, spanY = 1)

    private fun resize(edge: ResizeEdge, cells: Int, others: List<CellRect> = emptyList(), rect: CellRect = widget) =
        WidgetResize.resized(engine, rect, edge, cells, min, max, engine.occupancy(others))

    // --- resize ---

    @Test
    fun eachEdgeGrowsAndTheOppositeOneStaysPut() {
        assertEquals(CellRect(1, 2, 4, 1), resize(ResizeEdge.RIGHT, 1))
        assertEquals(CellRect(0, 2, 4, 1), resize(ResizeEdge.LEFT, 1))
        assertEquals(CellRect(1, 2, 3, 2), resize(ResizeEdge.BOTTOM, 1))
        assertEquals(CellRect(1, 1, 3, 2), resize(ResizeEdge.TOP, 1))
    }

    @Test
    fun shrinkingStopsAtTheSmallestSize() {
        assertEquals(CellRect(1, 2, 2, 1), resize(ResizeEdge.RIGHT, -5))
        assertEquals(CellRect(2, 2, 2, 1), resize(ResizeEdge.LEFT, -5))
    }

    @Test
    fun growingStopsAtTheLargestSize() {
        assertEquals(CellRect(1, 2, 3, 2), resize(ResizeEdge.BOTTOM, 4), "two rows at most")
    }

    @Test
    fun growingStopsAtTheGridEdge() {
        assertEquals(CellRect(1, 2, 4, 1), resize(ResizeEdge.RIGHT, 3), "column 4 is the last")
        assertEquals(CellRect(0, 2, 4, 1), resize(ResizeEdge.LEFT, 3))
    }

    @Test
    fun growingStopsBeforeATakenCell() {
        val app = CellRect(col = 2, row = 3)

        assertEquals(widget, resize(ResizeEdge.BOTTOM, 1, others = listOf(app)), "the row below is partly taken")
        assertEquals(CellRect(1, 2, 3, 2), resize(ResizeEdge.BOTTOM, 1, others = listOf(CellRect(col = 4, row = 3))))
    }

    @Test
    fun noMoveIsNoChange() {
        assertEquals(widget, resize(ResizeEdge.TOP, 0))
    }

    // --- placement ---

    @Test
    fun aNewWidgetGoesOnThePreferredPageWhenItFits() {
        val pages = mapOf(0 to listOf(CellRect(0, 0)), 1 to emptyList())

        assertEquals(1 to CellRect(0, 0, 4, 1), WidgetPlacement.place(engine, pages, pageCount = 2, preferredPage = 1, span = Span(4, 1)))
        assertEquals(0 to CellRect(1, 0, 4, 1), WidgetPlacement.place(engine, pages, pageCount = 2, preferredPage = 0, span = Span(4, 1)), "beside the app, in reading order")
    }

    @Test
    fun aFullPreferredPageSendsItToAnotherPage() {
        val full = (0 until 6).map { CellRect(0, it, 5, 1) }
        val pages = mapOf(0 to emptyList(), 1 to full)

        assertEquals(0 to CellRect(0, 0, 2, 2), WidgetPlacement.place(engine, pages, pageCount = 2, preferredPage = 1, span = Span(2, 2)))
    }

    @Test
    fun withNoRoomAnywhereItStartsANewPage() {
        val full = (0 until 6).map { CellRect(0, it, 5, 1) }

        assertEquals(1 to CellRect(0, 0, 3, 1), WidgetPlacement.place(engine, mapOf(0 to full), pageCount = 1, preferredPage = 0, span = Span(3, 1)))
    }

    @Test
    fun aWidgetLargerThanTheGridIsShrunkToIt() {
        assertEquals(0 to CellRect(0, 0, 5, 6), WidgetPlacement.place(engine, emptyMap(), pageCount = 1, preferredPage = 0, span = Span(9, 9)))
    }
}

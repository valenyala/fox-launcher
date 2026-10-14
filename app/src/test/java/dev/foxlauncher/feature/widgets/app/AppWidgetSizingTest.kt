package dev.foxlauncher.feature.widgets.app

import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.Span
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test

class AppWidgetSizingTest {

    /** The POCO F3's cells, roughly: 5 × 6 on a 1080 px wide screen. */
    private val grid = GridSpec(cols = 5, rows = 6, dockSlots = 5)
    private val cellW = 200f
    private val cellH = 300f

    private fun spans(size: ProviderSize) = AppWidgetSizing.spans(size, cellW, cellH, grid)

    @Test
    fun aLengthTakesTheCellsItNeeds() {
        val s = spans(ProviderSize(minWidth = 401, minHeight = 300))!!
        assertEquals(Span(3, 1), s.default, "401 px is just over two cells; 300 px is exactly one")
    }

    @Test
    fun anyLengthTakesAtLeastOneCell() {
        assertEquals(Span(1, 1), spans(ProviderSize(minWidth = 0, minHeight = 1))!!.default)
    }

    @Test
    fun aWidgetThatCannotResizeKeepsItsSize() {
        val s = spans(ProviderSize(minWidth = 400, minHeight = 300, minResizeWidth = 100, minResizeHeight = 100))!!
        assertEquals(Span(2, 1), s.min)
        assertEquals(Span(2, 1), s.max)
    }

    @Test
    fun aResizableWidgetRangesFromItsMinResizeSizeToTheGrid() {
        val s = spans(
            ProviderSize(
                minWidth = 800,
                minHeight = 600,
                minResizeWidth = 200,
                minResizeHeight = 300,
                resizesHorizontally = true,
                resizesVertically = true,
            ),
        )!!
        assertEquals(WidgetSpans(default = Span(4, 2), min = Span(1, 1), max = Span(5, 6)), s)
    }

    @Test
    fun resizingOneWayOnlyLeavesTheOtherFixed() {
        val s = spans(ProviderSize(minWidth = 800, minHeight = 600, minResizeWidth = 200, minResizeHeight = 300, resizesHorizontally = true))!!
        assertEquals(Span(1, 2), s.min)
        assertEquals(Span(5, 2), s.max)
    }

    @Test
    fun maxResizeSizesCapTheRange() {
        val s = spans(ProviderSize(minWidth = 400, minHeight = 300, maxResizeWidth = 600, maxResizeHeight = 900, resizesHorizontally = true, resizesVertically = true))!!
        assertEquals(Span(3, 3), s.max)
    }

    @Test
    fun theMinimumNeverExceedsTheDefault() {
        // minResizeWidth larger than minWidth: the documentation says minResize* is ignored then.
        val s = spans(ProviderSize(minWidth = 200, minHeight = 300, minResizeWidth = 800, resizesHorizontally = true))!!
        assertEquals(1, s.min.x)
        assertEquals(1, s.default.x)
    }

    @Test
    fun targetCellsWinWithinTheResizeRange() {
        val size = ProviderSize(
            minWidth = 400,
            minHeight = 300,
            minResizeWidth = 200,
            minResizeHeight = 300,
            targetCellWidth = 4,
            targetCellHeight = 2,
            resizesHorizontally = true,
            resizesVertically = true,
        )
        assertEquals(Span(4, 2), spans(size)!!.default)
    }

    @Test
    fun targetCellsOutsideTheRangeAreIgnored() {
        // Not resizable vertically → the range is 1..1 rows, so a 2-row target does not apply in either direction.
        val size = ProviderSize(minWidth = 400, minHeight = 300, maxResizeHeight = 300, targetCellWidth = 3, targetCellHeight = 2, resizesHorizontally = true)
        assertEquals(Span(2, 1), spans(size)!!.default)
    }

    @Test
    fun aWidgetLargerThanTheGridShrinksToIt() {
        val s = spans(ProviderSize(minWidth = 1400, minHeight = 300, minResizeWidth = 600, resizesHorizontally = true))!!
        assertEquals(Span(5, 1), s.default)
        assertEquals(Span(3, 1), s.min)
        assertEquals(Span(5, 1), s.max)
    }

    @Test
    fun aWidgetWhoseSmallestSizeIsLargerThanTheGridDoesNotFit() {
        assertNull(spans(ProviderSize(minWidth = 1400, minHeight = 300)))
        assertNull(spans(ProviderSize(minWidth = 200, minHeight = 2100, minResizeHeight = 1900, resizesVertically = true)))
    }

    @Test
    fun cellsMustHaveASize() {
        assertFailsWith<IllegalArgumentException> { AppWidgetSizing.spans(ProviderSize(1, 1), 0f, cellH, grid) }
    }
}

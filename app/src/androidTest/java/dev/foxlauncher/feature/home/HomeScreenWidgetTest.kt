package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.foxlauncher.R
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePage
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.ResizeEdge
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.LocalNativeWidgets
import dev.foxlauncher.feature.widgets.NativeWidgetRegistry
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native widgets on the home grid (task 5.1): drawn across their cells, resized from the popup. */
@RunWith(AndroidJUnit4::class)
class HomeScreenWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val grid = GridSpec.DEFAULT
    private val testSpec = NativeWidgetSpec(
        kind = "test",
        nameRes = R.string.widget_clock,
        descriptionRes = R.string.widget_clock_description,
        minSpan = Span(2, 1),
        defaultSpan = Span(2, 1),
        maxSpan = Span(4, 2),
        content = { Text("widget ${it.spanX}x${it.spanY}") },
    )
    private val widget = HomeItem.NativeWidget(1, HomePosition(HomeContainer.PAGE, 0, col = 1, row = 1, spanX = 2, spanY = 1), "test")
    private val resizes = mutableListOf<Pair<HomeItem, CellRect>>()

    @Test
    fun aNativeWidgetDrawsItsContentAcrossItsCells() {
        setHome(widget)

        composeRule.onNodeWithText("widget 2x1").assertIsDisplayed()
    }

    @Test
    fun aWidgetKindFoxNoLongerHasSaysSo() {
        setHome(widget.copy(kind = "gone"))

        composeRule.onNodeWithText(context.getString(R.string.widget_missing)).assertIsDisplayed()
    }

    @Test
    fun resizeFromThePopupGrowsItByWholeCells() {
        setHome(widget)

        openResize()
        dragHandle(ResizeEdge.RIGHT, Offset(cellWidth() * 1.1f, 0f))

        assertEquals(listOf<Pair<HomeItem, CellRect>>(widget to CellRect(1, 1, 3, 1)), resizes)
    }

    @Test
    fun resizingStopsAtAnotherItem() {
        val app = HomeItem.App(2, HomePosition(HomeContainer.PAGE, 0, col = 3, row = 1), "com.example/.App", 0)
        setHome(widget, app)

        openResize()
        dragHandle(ResizeEdge.RIGHT, Offset(cellWidth() * 2.1f, 0f))

        assertTrue(resizes.isEmpty(), "the app's cell is taken, so nothing changed: $resizes")
    }

    @Test
    fun aLightTapOnAHandleKeepsTheFrame() {
        setHome(widget)
        openResize()

        composeRule.onNodeWithTag(WidgetResizeTestTags.handle(ResizeEdge.RIGHT)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WidgetResizeTestTags.FRAME).assertIsDisplayed()
    }

    @Test
    fun aTapOutsideEndsResizing() {
        setHome(widget)
        openResize()

        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, height * 0.8f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WidgetResizeTestTags.FRAME).assertDoesNotExist()
        assertTrue(resizes.isEmpty())
    }

    private fun setHome(vararg items: HomeItem) {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, persistentListOf(*items))))
        composeRule.setContent {
            FoxTheme {
                CompositionLocalProvider(LocalNativeWidgets provides NativeWidgetRegistry(setOf(testSpec))) {
                    HomeScreen(
                        homeKeyPresses = 0,
                        onDrawerClosed = {},
                        state = HomeUiState(layout = layout, gridSpec = grid),
                        loadPopup = { ItemPopupModel(canResize = true) },
                        onResizeWidget = { item, cell -> resizes += item to cell },
                    ) { Text("drawer") }
                }
            }
        }
    }

    /** Long-presses the widget and picks Resize in its popup. */
    private fun openResize() {
        composeRule.onRoot().performTouchInput { down(cellCenter(2, 1)) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.popup_resize)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WidgetResizeTestTags.FRAME).assertIsDisplayed()
    }

    private fun dragHandle(edge: ResizeEdge, by: Offset) {
        val handle = with(composeRule.density) {
            val bounds = composeRule.onNodeWithTag(WidgetResizeTestTags.handle(edge)).getBoundsInRoot()
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
        composeRule.onRoot().performTouchInput {
            down(handle)
            repeat(STEPS) { moveBy(Offset(by.x / STEPS, by.y / STEPS)) }
            up()
        }
        composeRule.waitForIdle()
    }

    private fun cellWidth(): Float = with(composeRule.density) {
        val pager = composeRule.onNodeWithTag(HomeTestTags.PAGER).getBoundsInRoot()
        (pager.right - pager.left - GRID_PADDING * 2).toPx() / grid.cols
    }

    /** Centre of a grid cell, in root px. The page grid is the pager minus its side padding. */
    private fun cellCenter(col: Int, row: Int): Offset = with(composeRule.density) {
        val pager = composeRule.onNodeWithTag(HomeTestTags.PAGER).getBoundsInRoot()
        val left = (pager.left + GRID_PADDING).toPx()
        val width = (pager.right - pager.left - GRID_PADDING * 2).toPx()
        val top = pager.top.toPx()
        val height = (pager.bottom - pager.top).toPx()
        Offset(left + width / grid.cols * (col + 0.5f), top + height / grid.rows * (row + 0.5f))
    }

    private companion object {
        const val LONG_PRESS_WAIT_MS = 1_000L
        /** Small steps, like a finger: each well under touch slop (the frame once broke on exactly that). */
        const val STEPS = 60
        val GRID_PADDING = 8.dp
    }
}

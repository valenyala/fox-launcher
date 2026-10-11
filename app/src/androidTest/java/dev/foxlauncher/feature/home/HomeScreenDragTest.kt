package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePage
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Long-press drag on the home grid (task 2.4), down to the [DropAction] handed to the caller. */
@RunWith(AndroidJUnit4::class)
class HomeScreenDragTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val spec = GridSpec.DEFAULT
    private val first = app(id = 1, col = 0, row = 0)
    private val second = app(id = 2, col = 1, row = 0)
    private val drops = mutableListOf<Pair<HomeItem, DropAction>>()
    private val clicks = mutableListOf<HomeItem>()

    @Test
    fun dragToAFreeCellMovesTheItemThere() {
        setHome()

        drag(from = cellCenter(0, 0), to = cellCenter(2, 3))

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.MoveToPage(0, CellRect(2, 3))), drops)
    }

    @Test
    fun dropOnAnotherAppCreatesAFolder() {
        setHome()

        drag(from = cellCenter(0, 0), to = cellCenter(1, 0))

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.CreateFolder(second.id)), drops)
    }

    @Test
    fun dropOnADockSlotMovesTheItemToTheDock() {
        setHome()

        drag(from = cellCenter(0, 0), to = dockSlotCenter(2))

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.MoveToDock(2)), drops)
    }

    @Test
    fun aTapStillOpensTheItem() {
        setHome()

        composeRule.onRoot().performTouchInput { click(cellCenter(0, 0)) }

        assertEquals(listOf<HomeItem>(first), clicks)
        assertTrue(drops.isEmpty())
    }

    private fun setHome() {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, persistentListOf(first, second))))
        composeRule.setContent {
            FoxTheme {
                HomeScreen(
                    homeKeyPresses = 0,
                    onDrawerClosed = {},
                    state = HomeUiState(layout = layout, gridSpec = spec),
                    onItemClick = { item, _ -> clicks += item },
                    onDrop = { item, action -> drops += item to action },
                ) { Text("drawer") }
            }
        }
    }

    /** Long press at [from], then move to [to] in a few steps and release. */
    private fun drag(from: Offset, to: Offset) {
        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput {
            for (step in 1..STEPS) moveTo(from + (to - from) * (step.toFloat() / STEPS))
            up()
        }
        composeRule.waitForIdle()
    }

    /** Centre of a grid cell, in root px. The page grid is the pager minus its side padding. */
    private fun cellCenter(col: Int, row: Int): Offset = with(composeRule.density) {
        val pager = composeRule.onNodeWithTag(HomeTestTags.PAGER).getBoundsInRoot()
        val left = (pager.left + GRID_PADDING).toPx()
        val width = (pager.right - pager.left - GRID_PADDING * 2).toPx()
        val top = pager.top.toPx()
        val height = (pager.bottom - pager.top).toPx()
        Offset(left + width / spec.cols * (col + 0.5f), top + height / spec.rows * (row + 0.5f))
    }

    private fun dockSlotCenter(slot: Int): Offset = with(composeRule.density) {
        val dock = composeRule.onNodeWithTag(HomeTestTags.DOCK).getBoundsInRoot()
        val slotWidth = (dock.right - dock.left).toPx() / spec.dockSlots
        Offset(dock.left.toPx() + slotWidth * (slot + 0.5f), ((dock.top + dock.bottom) / 2).toPx())
    }

    private companion object {
        const val LONG_PRESS_WAIT_MS = 1_000L
        const val STEPS = 8
        val GRID_PADDING = 8.dp

        fun app(id: Long, col: Int, row: Int) = HomeItem.App(
            id = id,
            position = HomePosition(HomeContainer.PAGE, page = 0, col = col, row = row),
            component = "com.example/.App$id",
            userSerial = 0,
        )
    }
}

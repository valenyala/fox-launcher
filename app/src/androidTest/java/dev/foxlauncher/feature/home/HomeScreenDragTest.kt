package dev.foxlauncher.feature.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePage
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.drag.NEW_ITEM_ID
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
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

        drag(from = cellCenter(0, 0)) { cellCenter(2, 3) }

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.MoveToPage(0, CellRect(2, 3))), drops)
    }

    @Test
    fun dropOnAnotherAppCreatesAFolder() {
        setHome()

        drag(from = cellCenter(0, 0)) { cellCenter(1, 0) }

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.CreateFolder(second.id)), drops)
    }

    @Test
    fun dropOnADockSlotMovesTheItemToTheDock() {
        setHome()

        drag(from = cellCenter(0, 0)) { dockSlotCenter(2) }

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.MoveToDock(2)), drops)
    }

    @Test
    fun dropOnTheRemoveZoneRemovesTheItem() {
        setHome()

        // The zone only exists once the drag has started, so aim at it then.
        drag(from = cellCenter(0, 0)) { center(HomeTestTags.REMOVE_ZONE) }

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.Remove(first.id)), drops)
    }

    @Test
    fun removingAFolderAsksFirst() {
        val folder = HomeItem.Folder(
            id = 3,
            position = HomePosition(HomeContainer.PAGE, page = 0, col = 2, row = 0),
            folderId = 1,
            title = "",
            items = persistentListOf(app(id = 4, col = 0, row = 0), app(id = 5, col = 1, row = 0)),
        )
        setHome(persistentListOf(first, second, folder))

        drag(from = cellCenter(2, 0)) { center(HomeTestTags.REMOVE_ZONE) }
        assertTrue(drops.isEmpty())
        composeRule.onNodeWithText("Remove").performClick()

        assertEquals(listOf<Pair<HomeItem, DropAction>>(folder to DropAction.Remove(folder.id)), drops)
    }

    @Test
    fun restingAtTheRightEdgeOfTheLastPageOpensANewOne() {
        setHome()
        val from = cellCenter(0, 0)
        val edgeY = cellCenter(0, 2).y

        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput { moveTo(Offset(rootWidth() - 4f, edgeY)) }
        // Dwell, flip, and let the flip animation finish; only one new page is ever offered.
        composeRule.mainClock.advanceTimeBy(EDGE_WAIT_MS)
        val target = cellCenter(2, 2)
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<HomeItem, DropAction>>(first to DropAction.MoveToPage(1, CellRect(2, 2))), drops)
    }

    @Test
    fun longPressThenMoveInTheDrawerDragsTheAppHome() {
        val entry = AppEntry("com.example/.Drawer", 0, "Drawer app", "com.example", false, false, true, 1)
        setHome { Box(Modifier.fillMaxSize().combinedClickable(onLongClick = { onAppLongPress(entry) }) {}) }
        composeRule.onNodeWithTag(HomeTestTags.SEARCH_PILL).performClick()

        drag(from = center(HomeTestTags.DRAWER)) { cellCenter(3, 4) }

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
        val (item, action) = drops.single()
        assertEquals(NEW_ITEM_ID, item.id)
        assertEquals(entry.component, (item as HomeItem.App).component)
        assertEquals(DropAction.MoveToPage(0, CellRect(3, 4)), action)
    }

    @Test
    fun aTapStillOpensTheItem() {
        setHome()

        composeRule.onRoot().performTouchInput { click(cellCenter(0, 0)) }

        assertEquals(listOf<HomeItem>(first), clicks)
        assertTrue(drops.isEmpty())
    }

    private fun setHome(
        items: ImmutableList<HomeItem> = persistentListOf(first, second),
        drawer: @Composable DrawerScope.(Boolean) -> Unit = { Text("drawer") },
    ) {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, items)))
        composeRule.setContent {
            FoxTheme {
                HomeScreen(
                    homeKeyPresses = 0,
                    onDrawerClosed = {},
                    state = HomeUiState(layout = layout, gridSpec = spec),
                    onItemClick = { item, _ -> clicks += item },
                    onDrop = { item, action -> drops += item to action },
                    drawer = drawer,
                )
            }
        }
    }

    /**
     * Long press at [from], then move to [to] in a few steps and release. [to] is read once the drag
     * has started, because the pages shrink while dragging.
     */
    private fun drag(from: Offset, to: () -> Offset) {
        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        // One small step starts a drag that waits for movement (the drawer's).
        composeRule.onRoot().performTouchInput { moveBy(FIRST_STEP) }
        val target = to()
        composeRule.onRoot().performTouchInput {
            val start = from + FIRST_STEP
            for (step in 1..STEPS) moveTo(start + (target - start) * (step.toFloat() / STEPS))
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

    private fun center(tag: String): Offset = with(composeRule.density) {
        val bounds = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
    }

    private fun rootWidth(): Float = with(composeRule.density) {
        val root = composeRule.onRoot().getBoundsInRoot()
        (root.right - root.left).toPx()
    }

    private fun dockSlotCenter(slot: Int): Offset = with(composeRule.density) {
        val dock = composeRule.onNodeWithTag(HomeTestTags.DOCK).getBoundsInRoot()
        val slotWidth = (dock.right - dock.left).toPx() / spec.dockSlots
        Offset(dock.left.toPx() + slotWidth * (slot + 0.5f), ((dock.top + dock.bottom) / 2).toPx())
    }

    private companion object {
        const val LONG_PRESS_WAIT_MS = 1_000L
        const val EDGE_WAIT_MS = 2_000L
        val FIRST_STEP = Offset(0f, -40f)
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

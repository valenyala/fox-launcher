package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
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
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening, reordering, leaving and renaming a folder (task 2.5). */
@RunWith(AndroidJUnit4::class)
class HomeScreenFolderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val spec = GridSpec.DEFAULT
    private val children = (0 until 3).map { rank ->
        HomeItem.App(
            id = 10L + rank,
            position = HomePosition(HomeContainer.FOLDER, page = 0, col = rank, row = 0),
            component = "com.example/.App$rank",
            userSerial = 0,
        )
    }
    private val folder = HomeItem.Folder(
        id = 1,
        position = HomePosition(HomeContainer.PAGE, page = 0, col = 0, row = 0),
        folderId = 7,
        title = "Tools",
        items = persistentListOf(*children.toTypedArray()),
    )
    private val drops = mutableListOf<Pair<HomeItem, DropAction>>()
    private val renames = mutableListOf<Pair<HomeItem.Folder, String>>()

    @Test
    fun tappingAFolderOpensItsSheetAndTappingOutsideClosesIt() {
        setHome()

        openFolder()
        composeRule.onNodeWithTag(HomeTestTags.FOLDER_SHEET).assertIsDisplayed()
        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, height - 4f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.FOLDER_SHEET).assertDoesNotExist()
    }

    @Test
    fun draggingInsideTheFolderReordersIt() {
        setHome()
        openFolder()

        drag(from = folderCellCenter(0)) { folderCellCenter(2) }

        assertEquals(listOf<Pair<HomeItem, DropAction>>(children[0] to DropAction.MoveInFolder(2)), drops)
    }

    @Test
    fun draggingOffTheCardClosesTheFolderAndDropsOnTheHome() {
        setHome()
        openFolder()
        val from = folderCellCenter(1)
        // In the margin under the card: off it in either orientation, and nowhere near the Remove strip.
        val belowCard = Offset(center(HomeTestTags.FOLDER_CARD).x, (bottomOf(HomeTestTags.FOLDER_CARD) + bottomOf(HomeTestTags.FOLDER_SHEET)) / 2)

        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput { moveTo(belowCard) }
        composeRule.mainClock.advanceTimeBy(EXIT_WAIT_MS)
        composeRule.onNodeWithTag(HomeTestTags.FOLDER_SHEET).assertDoesNotExist()
        val target = cellCenter(3, 3)
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<HomeItem, DropAction>>(children[1] to DropAction.MoveToPage(0, CellRect(3, 3))), drops)
    }

    @Test
    fun aNewTitleIsReportedWhenTheFolderCloses() {
        setHome()
        openFolder()

        composeRule.onNodeWithTag(HomeTestTags.FOLDER_TITLE).performTextReplacement("Games")
        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, height - 4f)) }
        composeRule.waitForIdle()

        assertEquals(listOf(folder to "Games"), renames)
    }

    private fun setHome() {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, persistentListOf(folder))))
        composeRule.setContent {
            FoxTheme {
                HomeScreen(
                    homeKeyPresses = 0,
                    onDrawerClosed = {},
                    state = HomeUiState(layout = layout, gridSpec = spec),
                    onDrop = { item, action -> drops += item to action },
                    onRenameFolder = { renamed, title -> renames += renamed to title },
                ) { Text("drawer") }
            }
        }
    }

    private fun openFolder() {
        composeRule.onRoot().performTouchInput { click(cellCenter(0, 0)) }
        composeRule.waitForIdle()
    }

    /** Long press at [from], then move to [to] (read once the drag has started) in a few steps and release. */
    private fun drag(from: Offset, to: () -> Offset) {
        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        val target = to()
        composeRule.onRoot().performTouchInput {
            for (step in 1..STEPS) moveTo(from + (target - from) * (step.toFloat() / STEPS))
            up()
        }
        composeRule.waitForIdle()
    }

    /** Centre of position [rank] in the open folder's grid, in root px. */
    private fun folderCellCenter(rank: Int): Offset = with(composeRule.density) {
        val grid = composeRule.onNodeWithTag(HomeTestTags.FOLDER_GRID).getBoundsInRoot()
        val cols = folderColumns(children.size)
        val rows = (children.size + cols - 1) / cols
        val cellW = (grid.right - grid.left).toPx() / cols
        val cellH = (grid.bottom - grid.top).toPx() / rows
        Offset(grid.left.toPx() + cellW * (rank % cols + 0.5f), grid.top.toPx() + cellH * (rank / cols + 0.5f))
    }

    /** Centre of a home grid cell, in root px. The page grid is the pager minus its side padding. */
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

    private fun bottomOf(tag: String): Float = with(composeRule.density) {
        composeRule.onNodeWithTag(tag).getBoundsInRoot().bottom.toPx()
    }

    private companion object {
        const val LONG_PRESS_WAIT_MS = 1_000L
        const val EXIT_WAIT_MS = 1_000L
        const val STEPS = 8
        val GRID_PADDING = 8.dp
    }
}

package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
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
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The long-press popup on home items (task 2.6). */
@RunWith(AndroidJUnit4::class)
class HomeScreenPopupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val spec = GridSpec.DEFAULT
    private val app = HomeItem.App(1, HomePosition(HomeContainer.PAGE, 0, col = 1, row = 2), "com.example/.App", 0)
    private val folder = HomeItem.Folder(
        id = 2,
        position = HomePosition(HomeContainer.PAGE, 0, col = 3, row = 2),
        folderId = 9,
        title = "",
        items = persistentListOf(
            HomeItem.App(3, HomePosition(HomeContainer.FOLDER, 0, 0, 0), "com.example/.A", 0),
            HomeItem.App(4, HomePosition(HomeContainer.FOLDER, 0, 1, 0), "com.example/.B", 0),
        ),
    )
    private val actions = mutableListOf<Pair<HomeItem, PopupAction>>()
    private val drops = mutableListOf<Pair<HomeItem, DropAction>>()

    @Test
    fun aLongPressInPlaceOpensThePopupAndATapOutsideClosesIt() {
        setHome()

        longPress(cellCenter(1, 2))
        composeRule.onNodeWithTag(HomeTestTags.ITEM_POPUP).assertIsDisplayed()
        composeRule.onRoot().performTouchInput { click(Offset(width / 2f, height - 4f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.ITEM_POPUP).assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    fun thePopupOffersWhatTheModelAllows() {
        setHome(ItemPopupModel(canOpenAppInfo = true, canUninstall = false))

        longPress(cellCenter(1, 2))

        composeRule.onNodeWithText(context.getString(R.string.popup_app_info)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.home_remove)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.popup_uninstall)).assertDoesNotExist()
    }

    @Test
    fun choosingAnActionReportsItAndClosesThePopup() {
        setHome(ItemPopupModel(canOpenAppInfo = true, canUninstall = true))

        longPress(cellCenter(1, 2))
        composeRule.onNodeWithText(context.getString(R.string.popup_uninstall)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<HomeItem, PopupAction>>(app to PopupAction.Uninstall), actions)
        composeRule.onNodeWithTag(HomeTestTags.ITEM_POPUP).assertDoesNotExist()
    }

    @Test
    fun removingAFolderFromItsPopupAsksFirst() {
        setHome()

        longPress(cellCenter(3, 2))
        composeRule.onNodeWithText(context.getString(R.string.home_remove)).performClick()
        composeRule.waitForIdle()
        assertTrue(actions.isEmpty())
        composeRule.onNodeWithText(context.getString(R.string.home_remove)).performClick()

        assertEquals(listOf<Pair<HomeItem, DropAction>>(folder to DropAction.Remove(folder.id)), drops)
    }

    @Test
    fun movingOnFromTheLongPressDragsAndClosesThePopup() {
        setHome()
        val from = cellCenter(1, 2)

        composeRule.onRoot().performTouchInput { down(from) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput { moveBy(Offset(0f, 40f)) }
        composeRule.onNodeWithTag(HomeTestTags.ITEM_POPUP).assertDoesNotExist()
        val target = cellCenter(2, 4)
        composeRule.onRoot().performTouchInput {
            moveTo(target)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf<Pair<HomeItem, DropAction>>(app to DropAction.MoveToPage(0, CellRect(2, 4))), drops)
    }

    private fun setHome(model: ItemPopupModel = ItemPopupModel()) {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, persistentListOf(app, folder))))
        composeRule.setContent {
            FoxTheme {
                HomeScreen(
                    homeKeyPresses = 0,
                    onDrawerClosed = {},
                    state = HomeUiState(layout = layout, gridSpec = spec),
                    onDrop = { item, action -> drops += item to action },
                    loadPopup = { model },
                    onPopupAction = { item, action, _ -> actions += item to action },
                ) { Text("drawer") }
            }
        }
    }

    private fun longPress(at: Offset) {
        composeRule.onRoot().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_WAIT_MS)
        composeRule.onRoot().performTouchInput { up() }
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

    private companion object {
        const val LONG_PRESS_WAIT_MS = 1_000L
        val GRID_PADDING = 8.dp
    }
}

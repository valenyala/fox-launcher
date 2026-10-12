package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePage
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.home.grid.GridSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The gesture arena on the real home (task 3.3), next to the drawer swipe, items and the pager. */
@RunWith(AndroidJUnit4::class)
class HomeScreenGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val spec = GridSpec.DEFAULT
    private val item = HomeItem.App(
        id = 1,
        position = HomePosition(HomeContainer.PAGE, page = 0, col = 0, row = 0),
        component = "com.example/.App",
        userSerial = 0,
    )
    private val gestures = mutableListOf<GestureType>()

    @Test
    fun aSwipeUpStillOpensTheDrawerAndIsReported() {
        setHome()

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithText("drawer").assertIsDisplayed()
        assertEquals(listOf(GestureType.SWIPE_UP), gestures)
    }

    @Test
    fun aTwoFingerSwipeDownIsReportedAndLeavesTheDrawerClosed() {
        setHome()

        composeRule.onRoot().performTouchInput {
            down(0, Offset(width * 0.4f, height * 0.3f))
            down(1, Offset(width * 0.6f, height * 0.3f))
            repeat(10) {
                updatePointerBy(0, Offset(0f, height * 0.04f))
                updatePointerBy(1, Offset(0f, height * 0.04f))
                move(10)
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(GestureType.SWIPE_DOWN_2F), gestures)
        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
    }

    @Test
    fun aLongPressOnAnEmptyCellIsReported() {
        setHome()

        composeRule.onRoot().performTouchInput { longClick(cellCenter(3, 3)) }

        assertEquals(listOf(GestureType.LONG_PRESS_EMPTY), gestures)
    }

    @Test
    fun aLongPressOnAnItemOpensItsPopupInstead() {
        setHome()

        composeRule.onRoot().performTouchInput { longClick(cellCenter(0, 0)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.ITEM_POPUP).assertIsDisplayed()
        assertTrue(gestures.isEmpty())
    }

    @Test
    fun nothingIsReportedWhileTheDrawerIsOpen() {
        setHome()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        gestures.clear()

        composeRule.onRoot().performTouchInput { doubleClick() }

        assertTrue(gestures.isEmpty())
    }

    private fun setHome() {
        val layout = HomeLayout(pages = persistentListOf(HomePage(0, persistentListOf(item))))
        composeRule.setContent {
            FoxTheme {
                HomeScreen(
                    homeKeyPresses = 0,
                    onDrawerClosed = {},
                    state = HomeUiState(layout = layout, gridSpec = spec),
                    onGesture = { gestures += it },
                ) { Text("drawer") }
            }
        }
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
        val GRID_PADDING = 8.dp
    }
}

package dev.foxlauncher.feature.home

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun drawerIsClosedInitially() {
        setHome()

        composeRule.onNodeWithTag(HomeTestTags.SEARCH_PILL).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
    }

    @Test
    fun tappingThePillOpensTheDrawerWithSearchFocused() {
        setHome()

        composeRule.onNodeWithTag(HomeTestTags.SEARCH_PILL).performClick()

        composeRule.onNodeWithText("drawer focus=true").assertIsDisplayed()
    }

    @Test
    fun swipingUpOpensTheDrawerWithoutFocus() {
        setHome()

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithText("drawer focus=false").assertIsDisplayed()
    }

    @Test
    fun shortFastFlickOpensTheDrawer() {
        setHome()

        // 10 % of the height, well under the open threshold: only the fling speed can open it.
        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.65f, durationMillis = 60)
        }

        composeRule.onNodeWithText("drawer focus=false").assertIsDisplayed()
    }

    @Test
    fun shortSlowDragLeavesTheDrawerClosed() {
        setHome()

        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.65f, durationMillis = 1_500)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
    }

    @Test
    fun homeKeyClosesTheDrawer() {
        var presses by mutableIntStateOf(0)
        var closed = 0
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = presses, onDrawerClosed = { closed++ }) { Text("drawer") }
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.SEARCH_PILL).performClick()
        composeRule.onNodeWithText("drawer").assertIsDisplayed()

        presses++
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
        assertEquals(1, closed)
    }

    private fun setHome() {
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}) { focus -> Text("drawer focus=$focus") }
            }
        }
    }
}

package dev.foxlauncher.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import dev.foxlauncher.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.ui.theme.FoxTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun drawerIsClosedInitially() {
        setHome()

        composeRule.onNodeWithTag(HomeTestTags.PAGER).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
    }

    @Test
    fun theHomeHasNoSearchBar() {
        setHome()

        // Search lives only in the drawer (user request, 2026-10-12).
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.drawer_search_hint)).assertDoesNotExist()
    }

    @Test
    fun swipingUpOpensTheDrawer() {
        setHome()

        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithText("drawer").assertIsDisplayed()
    }

    @Test
    fun shortFastFlickOpensTheDrawer() {
        setHome()

        // 10 % of the height, well under the open threshold: only the fling speed can open it.
        composeRule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.75f, endY = height * 0.65f, durationMillis = 60)
        }

        composeRule.onNodeWithText("drawer").assertIsDisplayed()
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
    fun theDrawerIsBuiltBeforeItsFirstOpening() {
        var built = false
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}) {
                    built = true
                    Text("drawer")
                }
            }
        }
        composeRule.waitForIdle()

        assertTrue(built)
        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
        composeRule.onNodeWithText("drawer").assertDoesNotExist()
    }

    @Test
    fun comingBackHomeDoesNotRedrawTheClosedDrawer() {
        var draws = 0
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}) {
                    Text("drawer", Modifier.drawBehind { draws++ })
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()
        val drawsBefore = draws

        // Another activity covers the home, then goes: the hidden window lost its display lists.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        composeRule.onRoot().captureToImage()

        assertEquals(drawsBefore, draws)
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithText("drawer").assertIsDisplayed()
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
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithText("drawer").assertIsDisplayed()

        presses++
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
        assertEquals(1, closed)
    }

    @Test
    fun theDrawerReportsOpeningAndClosingByBack() {
        val reports = mutableListOf<Boolean>()
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}, onDrawerOpenChanged = { reports += it }) { Text("drawer") }
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(false), reports)

        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertEquals(listOf(false, true), reports)

        // Back from the navigation gestures' strip arrives as any Back does (task 3.8).
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
        assertEquals(listOf(false, true, false), reports)
    }

    @Test
    fun swipingDownOnTheDrawerListAtItsTopClosesTheDrawer() {
        setHomeWithList(LazyListState())
        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithTag(LIST).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertDoesNotExist()
    }

    @Test
    fun swipingDownOnAScrolledDrawerListScrollsItInstead() {
        val listState = LazyListState(firstVisibleItemIndex = 40)
        setHomeWithList(listState)
        composeRule.onRoot().performTouchInput { swipeUp() }

        composeRule.onNodeWithTag(LIST).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + height / 4f, durationMillis = 600)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.DRAWER).assertIsDisplayed()
        assertTrue(listState.firstVisibleItemIndex in 1 until 40)
    }

    /** A drawer that is one long scrollable list, like the real one. */
    private fun setHomeWithList(listState: LazyListState) {
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LIST)) {
                        items(60) { Text("app $it", Modifier.height(48.dp)) }
                    }
                }
            }
        }
    }

    private fun setHome() {
        composeRule.setContent {
            FoxTheme {
                HomeScreen(homeKeyPresses = 0, onDrawerClosed = {}) { Text("drawer") }
            }
        }
    }

    private companion object {
        const val LIST = "drawer_list"
    }
}

package dev.foxlauncher.feature.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.GestureType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The gesture arena (task 3.3) on plain content and on a real pager. */
@RunWith(AndroidJUnit4::class)
class GestureArenaTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val gestures = mutableListOf<GestureType>()
    private var enabled = true
    private var onItem = false

    @Test
    fun aSwipeUpIsReported() {
        setArena()

        arena().performTouchInput { swipeUp() }

        assertEquals(listOf(GestureType.SWIPE_UP), gestures)
    }

    @Test
    fun aSwipeDownIsReported() {
        setArena()

        arena().performTouchInput { swipeDown() }

        assertEquals(listOf(GestureType.SWIPE_DOWN), gestures)
    }

    @Test
    fun aDoubleTapIsReported() {
        setArena()

        arena().performTouchInput { doubleClick() }

        assertEquals(listOf(GestureType.DOUBLE_TAP), gestures)
    }

    @Test
    fun aLongPressOnEmptySpaceIsReported() {
        setArena()

        arena().performTouchInput { longClick() }

        assertEquals(listOf(GestureType.LONG_PRESS_EMPTY), gestures)
    }

    @Test
    fun aLongPressOnAnItemIsLeftToTheItem() {
        onItem = true
        setArena()

        arena().performTouchInput { longClick() }

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun aTapReachesTheContent() {
        var clicks = 0
        setArena { Box(Modifier.fillMaxSize().clickable { clicks++ }) }

        arena().performTouchInput { click() }

        assertEquals(1, clicks)
        assertTrue(gestures.isEmpty())
    }

    @Test
    fun nothingIsReportedWhileDisabled() {
        enabled = false
        setArena()

        arena().performTouchInput { swipeUp() }
        arena().performTouchInput { doubleClick() }

        assertTrue(gestures.isEmpty())
    }

    @Test
    fun aSwipeLeftStillPagesThePager() {
        val pager = setPagerArena()

        arena().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, pager.currentPage)
        assertTrue(gestures.isEmpty())
    }

    @Test
    fun aSwipeLeftOnTheLastPageIsReported() {
        val pager = setPagerArena()
        arena().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        arena().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(1, pager.currentPage)
        assertEquals(listOf(GestureType.SWIPE_LEFT), gestures)
    }

    @Test
    fun twoFingersSwipingLeftDoNotPage() {
        val pager = setPagerArena()

        arena().performTouchInput {
            down(0, Offset(width * 0.8f, height * 0.4f))
            down(1, Offset(width * 0.8f, height * 0.6f))
            repeat(10) {
                updatePointerBy(0, Offset(-width * 0.06f, 0f))
                updatePointerBy(1, Offset(-width * 0.06f, 0f))
                move(10)
            }
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        assertEquals(0, pager.currentPage)
        assertEquals(listOf(GestureType.SWIPE_LEFT_2F), gestures)
    }

    @Test
    fun pinchingInIsReported() {
        setArena()

        arena().performTouchInput {
            down(0, Offset(width * 0.2f, centerY))
            down(1, Offset(width * 0.8f, centerY))
            repeat(10) {
                updatePointerBy(0, Offset(width * 0.02f, 0f))
                updatePointerBy(1, Offset(-width * 0.02f, 0f))
                move(16)
            }
            up(0)
            up(1)
        }

        assertEquals(listOf(GestureType.PINCH_IN), gestures)
    }

    private fun arena() = composeRule.onNodeWithTag(ARENA)

    private fun setArena(content: @Composable () -> Unit = {}) {
        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(ARENA)
                    .gestureArena(enabled = { enabled }, isOnItem = { onItem }, onGesture = { gestures += it }),
            ) { content() }
        }
    }

    private fun setPagerArena(): PagerState {
        lateinit var pager: PagerState
        composeRule.setContent {
            pager = rememberPagerState { 2 }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(ARENA)
                    .gestureArena(
                        enabled = { enabled },
                        isOnItem = { onItem },
                        onGesture = { gestures += it },
                        pagerTakesSwipeLeft = { pager.canScrollForward },
                        pagerTakesSwipeRight = { pager.canScrollBackward },
                    ),
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { Text("page $it") }
            }
        }
        return pager
    }

    private companion object {
        const val ARENA = "arena"
    }
}

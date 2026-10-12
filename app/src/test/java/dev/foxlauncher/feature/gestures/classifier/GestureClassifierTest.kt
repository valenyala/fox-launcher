package dev.foxlauncher.feature.gestures.classifier

import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.feature.gestures.classifier.GestureResult.Cancelled
import dev.foxlauncher.feature.gestures.classifier.GestureResult.Claimed
import dev.foxlauncher.feature.gestures.classifier.GestureResult.LongPressPending
import dev.foxlauncher.feature.gestures.classifier.GestureResult.None
import dev.foxlauncher.feature.gestures.classifier.GestureResult.Recognized
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GestureClassifierTest {

    /** Whether the pager could page for a single-finger swipe left / right (it can on a middle page). */
    private var pagerTakesLeft = true
    private var pagerTakesRight = true

    private val classifier = GestureClassifier(
        GestureConfig(
            touchSlopPx = SLOP,
            minFlingVelocityPxPerS = 300f,
            longPressMs = LONG_PRESS_MS,
            doubleTapMs = DOUBLE_TAP_MS,
            doubleTapSlopPx = 100f,
            pinchThresholdPx = 60f,
            arenaWidthPx = 1000f,
            arenaHeightPx = 2000f,
            dockTopPx = 1800f,
            pagerTakesSwipeLeft = { pagerTakesLeft },
            pagerTakesSwipeRight = { pagerTakesRight },
        ),
    )

    private var clock = 0L

    // --- single-finger swipes ---

    @Test
    fun aFastSwipeUpIsSwipeUp() {
        assertEquals(recognized(GestureType.SWIPE_UP), swipe(dx = 0f, dy = -400f).last())
    }

    @Test
    fun aFastSwipeDownIsSwipeDown() {
        assertEquals(recognized(GestureType.SWIPE_DOWN), swipe(dx = 0f, dy = 400f).last())
    }

    @Test
    fun swipingLeftOnTheLastPageIsSwipeLeft() {
        pagerTakesLeft = false

        assertEquals(recognized(GestureType.SWIPE_LEFT), swipe(dx = -400f, dy = 0f).last())
    }

    @Test
    fun swipingRightOnTheFirstPageIsSwipeRight() {
        pagerTakesRight = false

        assertEquals(recognized(GestureType.SWIPE_RIGHT), swipe(dx = 400f, dy = 0f).last())
    }

    @Test
    fun aHorizontalSwipeOnAMiddlePageIsLeftToThePager() {
        assertTrue(swipe(dx = -400f, dy = 0f).none { it is Recognized || it == Claimed })
        assertTrue(swipe(dx = 400f, dy = 0f).none { it is Recognized || it == Claimed })
    }

    @Test
    fun whetherThePagerPagesIsReadWhenTheFingerGoesDown() {
        // Paging onto the last page during the swipe does not make it a home gesture.
        pagerTakesLeft = true
        down(0, 500f, 1000f)
        pagerTakesLeft = false
        moveBy(0, from = 500f to 1000f, dx = -400f, dy = 0f, durationMs = 100)

        assertEquals(None, up(0, 100f, 1000f))
    }

    @Test
    fun aSlowDragPastAQuarterOfTheArenaStillCounts() {
        // 600 px of a 2000 px arena over 3 s: 200 px/s, under the fling speed.
        assertEquals(recognized(GestureType.SWIPE_UP), swipe(dx = 0f, dy = -600f, durationMs = 3_000).last())
    }

    @Test
    fun aSlowShortDragIsNothing() {
        assertEquals(None, swipe(dx = 0f, dy = -200f, durationMs = 2_000).last())
    }

    @Test
    fun aFlickInsideTouchSlopIsNothing() {
        assertEquals(None, swipe(dx = 0f, dy = -15f, durationMs = 10).last())
    }

    @Test
    fun aFingerThatStopsBeforeLiftingIsNoFling() {
        down(0, 500f, 1000f)
        moveBy(0, from = 500f to 1000f, dx = 0f, dy = -300f, durationMs = 100)
        clock += 300
        move(0, 500f, 700f)

        assertEquals(None, up(0, 500f, 700f))
    }

    @Test
    fun aVerticalDragIsClaimedOncePastTouchSlop() {
        assertEquals(LongPressPending, down(0, 500f, 1000f))
        assertEquals(None, move(0, 500f, 990f))

        assertEquals(Claimed, move(0, 500f, 970f))
        assertEquals(None, move(0, 500f, 900f))
    }

    @Test
    fun aHorizontalDragIsNotClaimed() {
        down(0, 500f, 1000f)

        assertEquals(None, move(0, 450f, 1000f))
    }

    // --- dock ---

    @Test
    fun aSwipeUpFromTheDockIsDockSwipeUp() {
        assertEquals(recognized(GestureType.DOCK_SWIPE_UP), swipe(dx = 0f, dy = -400f, from = 500f to 1900f).last())
    }

    @Test
    fun aSwipeDownFromTheDockIsDockSwipeDown() {
        assertEquals(recognized(GestureType.DOCK_SWIPE_DOWN), swipe(dx = 0f, dy = 150f, from = 500f to 1820f, durationMs = 50).last())
    }

    @Test
    fun aSwipeEndingOnTheDockIsAnOrdinarySwipe() {
        assertEquals(recognized(GestureType.SWIPE_DOWN), swipe(dx = 0f, dy = 400f, from = 500f to 1600f).last())
    }

    // --- two fingers ---

    @Test
    fun aSecondFingerClaimsTheGesture() {
        down(0, 400f, 1000f)
        clock += 30

        assertEquals(Claimed, down(1, 600f, 1000f))
    }

    @Test
    fun twoFingersSwipingUpIsSwipeUp2f() {
        assertEquals(recognized(GestureType.SWIPE_UP_2F), twoFingerSwipe(dx = 0f, dy = -400f))
    }

    @Test
    fun twoFingersSwipingDownIsSwipeDown2f() {
        assertEquals(recognized(GestureType.SWIPE_DOWN_2F), twoFingerSwipe(dx = 0f, dy = 400f))
    }

    @Test
    fun twoFingersSwipingLeftIsSwipeLeft2fEvenOnAMiddlePage() {
        assertEquals(recognized(GestureType.SWIPE_LEFT_2F), twoFingerSwipe(dx = -300f, dy = 0f))
    }

    @Test
    fun twoFingersSwipingRightIsSwipeRight2f() {
        assertEquals(recognized(GestureType.SWIPE_RIGHT_2F), twoFingerSwipe(dx = 300f, dy = 0f))
    }

    @Test
    fun theSecondFingerMayLandUpTo60msLater() {
        assertEquals(recognized(GestureType.SWIPE_UP_2F), twoFingerSwipe(dx = 0f, dy = -400f, secondFingerDelayMs = 60))
    }

    @Test
    fun aTwoFingerTapIsNothing() {
        down(0, 400f, 1000f)
        down(1, 600f, 1000f)
        clock += 50

        assertEquals(None, up(1, 600f, 1000f))
        assertEquals(None, up(0, 400f, 1000f))
    }

    // --- pinch ---

    @Test
    fun fingersMovingTogetherArePinchIn() {
        assertEquals(recognized(GestureType.PINCH_IN), pinch(aDx = 100f, bDx = -100f))
    }

    @Test
    fun fingersMovingApartArePinchOut() {
        assertEquals(recognized(GestureType.PINCH_OUT), pinch(aDx = -150f, bDx = 150f))
    }

    @Test
    fun aPinchWithOneFingerHeldStillIsStillAPinch() {
        assertEquals(recognized(GestureType.PINCH_IN), pinch(aDx = 0f, bDx = -150f))
    }

    @Test
    fun aSmallChangeInSpacingIsNoPinch() {
        assertEquals(None, pinch(aDx = 20f, bDx = -20f))
    }

    // --- taps ---

    @Test
    fun aSingleTapIsNothing() {
        assertEquals(None, tap(500f, 1000f))
    }

    @Test
    fun aSecondTapJustInsideTheTimeoutIsADoubleTap() {
        tap(500f, 1000f)
        clock += DOUBLE_TAP_MS - 1

        assertEquals(recognized(GestureType.DOUBLE_TAP), tap(510f, 1010f))
    }

    @Test
    fun aSecondTapJustAfterTheTimeoutIsNot() {
        tap(500f, 1000f)
        clock += DOUBLE_TAP_MS + 1

        assertEquals(None, tap(500f, 1000f))
    }

    @Test
    fun aSecondTapTooFarAwayIsNot() {
        tap(500f, 1000f)
        clock += 100

        assertEquals(None, tap(700f, 1000f))
    }

    @Test
    fun aThirdTapStartsAfresh() {
        tap(500f, 1000f)
        clock += 100
        tap(500f, 1000f)
        clock += 100

        assertEquals(None, tap(500f, 1000f))
    }

    @Test
    fun aSwipeBetweenTapsBreaksTheDoubleTap() {
        tap(500f, 1000f)
        clock += 50
        swipe(dx = 0f, dy = -400f, durationMs = 50)
        clock += 50

        assertEquals(None, tap(500f, 1000f))
    }

    // --- long press ---

    @Test
    fun aStillFingerBecomesALongPress() {
        down(0, 500f, 1000f)
        move(0, 510f, 1005f) // jitter under slop

        assertEquals(None, classifier.onTimer(clock + LONG_PRESS_MS - 1))
        assertEquals(recognized(GestureType.LONG_PRESS_EMPTY), classifier.onTimer(clock + LONG_PRESS_MS))
    }

    @Test
    fun jitterPastSlopCancelsTheLongPress() {
        down(0, 500f, 1000f)
        move(0, 530f, 1000f)

        assertEquals(None, classifier.onTimer(clock + LONG_PRESS_MS + 100))
    }

    @Test
    fun movingOnAfterALongPressIsNoSwipe() {
        down(0, 500f, 1000f)
        classifier.onTimer(clock + LONG_PRESS_MS)
        clock += LONG_PRESS_MS

        val results = moveBy(0, from = 500f to 1000f, dx = 0f, dy = -600f, durationMs = 100) + up(0, 500f, 400f)

        assertTrue(results.all { it == None })
    }

    @Test
    fun aHeldFingerLiftedWithoutTheTimerIsNoTap() {
        down(0, 500f, 1000f)
        clock += LONG_PRESS_MS
        up(0, 500f, 1000f)
        clock += 50

        assertEquals(None, tap(500f, 1000f))
    }

    @Test
    fun twoFingersNeverLongPress() {
        down(0, 400f, 1000f)
        down(1, 600f, 1000f)

        assertEquals(None, classifier.onTimer(clock + LONG_PRESS_MS * 2))
    }

    // --- three fingers, cancel ---

    @Test
    fun threeFingersAreNothing() {
        down(0, 300f, 1000f)
        down(1, 500f, 1000f)
        assertEquals(Cancelled, down(2, 700f, 1000f))

        val results = (0..2).flatMap { id -> moveBy(id, from = (300f + id * 200) to 1000f, dx = 0f, dy = -500f, durationMs = 60) } +
            (0..2).map { id -> up(id, 300f + id * 200, 500f) }

        assertTrue(results.none { it is Recognized })
    }

    @Test
    fun cancelDropsTheGestureAndTheNextOneStartsClean() {
        down(0, 500f, 1000f)
        move(0, 500f, 900f)

        assertEquals(Cancelled, classifier.feed(PointerSample(0, 500f, 900f, clock, PointerPhase.CANCEL)))
        assertEquals(recognized(GestureType.SWIPE_UP), swipe(dx = 0f, dy = -400f).last())
    }

    @Test
    fun cancelAlsoForgetsAFirstTap() {
        tap(500f, 1000f)
        down(0, 500f, 1000f)
        classifier.feed(PointerSample(0, 500f, 1000f, clock, PointerPhase.CANCEL))
        clock += 50

        assertEquals(None, tap(500f, 1000f))
    }

    // --- helpers ---

    private fun recognized(type: GestureType) = Recognized(type)

    private fun down(id: Int, x: Float, y: Float) = classifier.feed(PointerSample(id, x, y, clock, PointerPhase.DOWN))

    private fun move(id: Int, x: Float, y: Float) = classifier.feed(PointerSample(id, x, y, clock, PointerPhase.MOVE))

    private fun up(id: Int, x: Float, y: Float) = classifier.feed(PointerSample(id, x, y, clock, PointerPhase.UP))

    /** Moves pointer [id] from [from] by ([dx], [dy]) in ten even steps over [durationMs]. */
    private fun moveBy(id: Int, from: Pair<Float, Float>, dx: Float, dy: Float, durationMs: Long): List<GestureResult> {
        val start = clock
        return (1..STEPS).map { i ->
            clock = start + durationMs * i / STEPS
            move(id, from.first + dx * i / STEPS, from.second + dy * i / STEPS)
        }
    }

    /** Down, ten moves, up; returns every result. */
    private fun swipe(dx: Float, dy: Float, durationMs: Long = 100, from: Pair<Float, Float> = 500f to 1000f): List<GestureResult> {
        val results = listOf(down(0, from.first, from.second)) + moveBy(0, from, dx, dy, durationMs)
        return results + up(0, from.first + dx, from.second + dy)
    }

    private fun tap(x: Float, y: Float): GestureResult {
        down(0, x, y)
        clock += 50
        return up(0, x, y)
    }

    /** Two fingers 200 px apart move together; returns the result of the first lift. */
    private fun twoFingerSwipe(dx: Float, dy: Float, secondFingerDelayMs: Long = 30): GestureResult {
        down(0, 400f, 1000f)
        clock += secondFingerDelayMs
        down(1, 600f, 1000f)
        val start = clock
        repeat(STEPS) { i ->
            clock = start + 100L * (i + 1) / STEPS
            move(0, 400f + dx * (i + 1) / STEPS, 1000f + dy * (i + 1) / STEPS)
            move(1, 600f + dx * (i + 1) / STEPS, 1000f + dy * (i + 1) / STEPS)
        }
        val result = up(0, 400f + dx, 1000f + dy)
        up(1, 600f + dx, 1000f + dy)
        return result
    }

    /** Fingers at x 300 and 700 move horizontally by [aDx] and [bDx]; returns the result of the first lift. */
    private fun pinch(aDx: Float, bDx: Float): GestureResult {
        down(0, 300f, 1000f)
        clock += 20
        down(1, 700f, 1000f)
        val start = clock
        repeat(STEPS) { i ->
            clock = start + 200L * (i + 1) / STEPS
            move(0, 300f + aDx * (i + 1) / STEPS, 1000f)
            move(1, 700f + bDx * (i + 1) / STEPS, 1000f)
        }
        val result = up(0, 300f + aDx, 1000f)
        up(1, 700f + bDx, 1000f)
        return result
    }

    private companion object {
        const val SLOP = 20f
        const val LONG_PRESS_MS = 400L
        const val DOUBLE_TAP_MS = 300L
        const val STEPS = 10
    }
}

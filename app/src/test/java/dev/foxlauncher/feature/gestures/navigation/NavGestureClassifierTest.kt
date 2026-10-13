package dev.foxlauncher.feature.gestures.navigation

import dev.foxlauncher.feature.gestures.classifier.PointerPhase
import dev.foxlauncher.feature.gestures.classifier.PointerSample
import dev.foxlauncher.feature.gestures.navigation.NavResult.Armed
import dev.foxlauncher.feature.gestures.navigation.NavResult.None
import dev.foxlauncher.feature.gestures.navigation.NavResult.Recognized
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NavGestureClassifierTest {

    private val config = NavGestureConfig(
        touchSlopPx = SLOP,
        backDistancePx = BACK_DISTANCE,
        homeDistancePx = HOME_DISTANCE,
        flingVelocityPxPerS = 1000f,
        holdMs = HOLD_MS,
    )

    private var edge = NavEdge.LEFT
    private var classifier = NavGestureClassifier(edge, config)
    private var clock = 0L

    /** Where each pointer last was, for [moveTo]. */
    private val last = mutableMapOf<Int, Pair<Float, Float>>()

    // --- Back ---

    @Test
    fun aSwipeInFromTheLeftIsBack() {
        on(NavEdge.LEFT)

        assertEquals(Recognized(NavAction.BACK), swipe(dx = 150f, dy = 0f).last())
    }

    @Test
    fun aSwipeInFromTheRightIsBack() {
        on(NavEdge.RIGHT)

        assertEquals(Recognized(NavAction.BACK), swipe(dx = -150f, dy = 0f).last())
    }

    @Test
    fun crossingTheBackDistanceArmsOnce() {
        on(NavEdge.LEFT)

        val results = swipe(dx = 200f, dy = 0f, durationMs = 1000)

        assertEquals(1, results.count { it == Armed })
    }

    @Test
    fun aSlowSwipeInFromTheLeftIsBack() {
        on(NavEdge.LEFT)

        assertEquals(Recognized(NavAction.BACK), swipe(dx = 150f, dy = 0f, durationMs = 2000).last())
    }

    @Test
    fun aShortQuickFlickInIsBack() {
        on(NavEdge.LEFT)

        assertEquals(Recognized(NavAction.BACK), swipe(dx = 60f, dy = 0f, durationMs = 30).last())
    }

    @Test
    fun aShortSlowSwipeInIsNothing() {
        on(NavEdge.LEFT)

        assertEquals(None, swipe(dx = 60f, dy = 0f, durationMs = 600).last())
    }

    @Test
    fun aSwipeOutwardsIsNothing() {
        on(NavEdge.LEFT)

        assertEquals(None, swipe(dx = -150f, dy = 0f).last())
    }

    @Test
    fun aMostlyVerticalSwipeOnASideIsNothing() {
        on(NavEdge.RIGHT)

        assertEquals(None, swipe(dx = -110f, dy = 300f).last())
    }

    @Test
    fun draggingBackOutBeforeLiftingCancelsBack() {
        on(NavEdge.LEFT)
        down(0, 0f, 500f)
        moveTo(0, 150f, 500f, durationMs = 300)

        moveTo(0, 10f, 500f, durationMs = 300)

        assertEquals(None, up(0, 10f, 500f))
    }

    @Test
    fun aSideSwipeHasNoHold() {
        on(NavEdge.LEFT)
        down(0, 0f, 500f)
        moveTo(0, 150f, 500f, durationMs = 300)

        assertNull(classifier.holdDeadline())
        clock += 1000
        assertEquals(None, classifier.onTimer(clock))
        assertEquals(Recognized(NavAction.BACK), up(0, 150f, 500f))
    }

    // --- Home ---

    @Test
    fun aSwipeUpFromTheBottomIsHome() {
        on(NavEdge.BOTTOM)

        assertEquals(Recognized(NavAction.HOME), swipe(dx = 0f, dy = -200f).last())
    }

    @Test
    fun aSlowSteadySwipeUpIsHomeNotRecents() {
        on(NavEdge.BOTTOM)

        // 200 px/s: the finger never stays within slop for the hold, so the timer finds nothing due.
        val results = swipe(dx = 0f, dy = -400f, durationMs = 2000, runTimers = true)

        assertEquals(Recognized(NavAction.HOME), results.last())
        assertTrue(Recognized(NavAction.RECENTS) !in results)
    }

    @Test
    fun aDiagonalSwipeUpWithin45DegreesIsHome() {
        on(NavEdge.BOTTOM)

        assertEquals(Recognized(NavAction.HOME), swipe(dx = 150f, dy = -200f).last())
    }

    @Test
    fun aSidewaysSwipeAlongTheBottomIsNothing() {
        on(NavEdge.BOTTOM)

        assertEquals(None, swipe(dx = 300f, dy = -150f).last())
    }

    @Test
    fun aSwipeDownOnTheBottomIsNothing() {
        on(NavEdge.BOTTOM)

        assertEquals(None, swipe(dx = 0f, dy = 40f).last())
    }

    @Test
    fun aShortSlowSwipeUpIsNothing() {
        on(NavEdge.BOTTOM)

        assertEquals(None, swipe(dx = 0f, dy = -60f, durationMs = 600).last())
    }

    @Test
    fun goingBackDownBeforeLiftingCancelsHome() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 200)

        moveTo(0, 500f, 0f, durationMs = 200)

        assertEquals(None, up(0, 500f, 0f))
    }

    @Test
    fun aTapIsNothing() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        clock += 50

        assertEquals(None, up(0, 500f, 30f))
    }

    // --- Recents ---

    @Test
    fun swipingUpAndHoldingIsRecentsBeforeTheLift() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)

        val deadline = assertNotNull(classifier.holdDeadline())
        assertEquals(clock + HOLD_MS, deadline)
        assertEquals(None, classifier.onTimer(deadline - 1))
        assertEquals(Recognized(NavAction.RECENTS), classifier.onTimer(deadline))
    }

    @Test
    fun theLiftAfterRecentsDoesNothing() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        clock += HOLD_MS
        classifier.onTimer(clock)

        assertNull(classifier.holdDeadline())
        assertEquals(None, up(0, 500f, -200f))
    }

    @Test
    fun liftingBeforeTheHoldIsHome() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        clock += HOLD_MS - 50
        assertEquals(None, classifier.onTimer(clock))

        assertEquals(Recognized(NavAction.HOME), up(0, 500f, -200f))
    }

    @Test
    fun jitterWithinSlopStillHolds() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        val deadline = assertNotNull(classifier.holdDeadline())

        clock += 100
        move(0, 510f, -195f)
        clock += 100
        move(0, 495f, -210f)

        assertEquals(deadline, classifier.holdDeadline())
        assertEquals(Recognized(NavAction.RECENTS), classifier.onTimer(deadline))
    }

    @Test
    fun movingOnPastSlopRestartsTheHold() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        val first = assertNotNull(classifier.holdDeadline())

        clock += 100
        move(0, 500f, -260f)

        assertEquals(clock + HOLD_MS, classifier.holdDeadline())
        assertEquals(None, classifier.onTimer(first))
    }

    @Test
    fun noHoldBeforeTheHomeDistance() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -50f, durationMs = 150)

        assertNull(classifier.holdDeadline())
        clock += 1000
        assertEquals(None, classifier.onTimer(clock))
    }

    // --- progress, for the visual feedback ---

    @Test
    fun noProgressWithoutAGesture() {
        on(NavEdge.LEFT)

        assertNull(classifier.progress())
        assertFalse(classifier.isArmed)
    }

    @Test
    fun progressFollowsTheSwipeAndArmsAtOne() {
        on(NavEdge.LEFT)
        down(0, 5f, 500f)

        move(0, 55f, 500f)
        assertEquals(0.5f, classifier.progress())
        assertFalse(classifier.isArmed)

        move(0, 155f, 500f)
        assertEquals(1.5f, classifier.progress())
        assertTrue(classifier.isArmed)
    }

    @Test
    fun progressCountsUpwardsFromTheBottom() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)

        move(0, 500f, -30f)

        assertEquals(0.5f, classifier.progress())
    }

    @Test
    fun progressIsZeroOffTheAxisOrOutwards() {
        on(NavEdge.RIGHT)
        down(0, 35f, 500f)

        move(0, 5f, 700f)
        assertEquals(0f, classifier.progress())

        move(0, 60f, 500f)
        assertEquals(0f, classifier.progress())
    }

    @Test
    fun progressEndsWithTheLift() {
        on(NavEdge.LEFT)
        swipe(dx = 150f, dy = 0f)

        assertNull(classifier.progress())
        assertFalse(classifier.isArmed)
    }

    @Test
    fun progressEndsWhenRecentsFires() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        clock += HOLD_MS
        classifier.onTimer(clock)

        assertNull(classifier.progress())
        assertFalse(classifier.isArmed)
    }

    @Test
    fun progressEndsWhenASecondFingerComes() {
        on(NavEdge.LEFT)
        down(0, 5f, 500f)
        move(0, 155f, 500f)

        down(1, 5f, 800f)

        assertNull(classifier.progress())
    }

    // --- fingers and cancel ---

    @Test
    fun aSecondFingerDropsTheGesture() {
        on(NavEdge.LEFT)
        down(0, 0f, 500f)
        moveTo(0, 60f, 500f, durationMs = 100)
        down(1, 5f, 700f)
        moveTo(0, 200f, 500f, durationMs = 100)

        assertEquals(None, up(0, 200f, 500f))
    }

    @Test
    fun aSecondFingerStopsAPendingHold() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        down(1, 700f, 30f)

        assertNull(classifier.holdDeadline())
    }

    @Test
    fun otherFingersDoNotMoveTheGesture() {
        on(NavEdge.LEFT)
        down(0, 0f, 500f)
        move(1, 300f, 500f)

        assertEquals(None, up(1, 300f, 500f))
        assertEquals(Recognized(NavAction.BACK), up(0, 150f, 500f))
    }

    @Test
    fun cancelEndsTheGesture() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)

        assertEquals(None, feed(0, 500f, -200f, PointerPhase.CANCEL))
        assertNull(classifier.holdDeadline())
        assertEquals(None, up(0, 500f, -200f))
    }

    @Test
    fun aGestureWhoseLiftNeverCameDoesNotBlockTheNext() {
        on(NavEdge.BOTTOM)
        down(0, 500f, 30f)
        moveTo(0, 500f, -200f, durationMs = 150)
        clock += HOLD_MS
        assertEquals(Recognized(NavAction.RECENTS), classifier.onTimer(clock))

        assertEquals(Recognized(NavAction.HOME), swipe(dx = 0f, dy = -200f).last())
    }

    @Test
    fun theNextGestureStartsFresh() {
        on(NavEdge.LEFT)
        down(0, 0f, 500f)
        down(1, 5f, 700f)
        up(0, 150f, 500f)
        up(1, 5f, 700f)

        assertEquals(Recognized(NavAction.BACK), swipe(dx = 150f, dy = 0f).last())
    }

    private fun on(edge: NavEdge) {
        this.edge = edge
        classifier = NavGestureClassifier(edge, config)
    }

    private fun feed(id: Int, x: Float, y: Float, phase: PointerPhase): NavResult {
        last[id] = x to y
        return classifier.feed(PointerSample(id, x, y, clock, phase))
    }

    private fun down(id: Int, x: Float, y: Float) = feed(id, x, y, PointerPhase.DOWN)

    private fun move(id: Int, x: Float, y: Float) = feed(id, x, y, PointerPhase.MOVE)

    private fun up(id: Int, x: Float, y: Float) = feed(id, x, y, PointerPhase.UP)

    /** Moves pointer [id] from where it last was to ([x], [y]) in ten even steps over [durationMs]. */
    private fun moveTo(id: Int, x: Float, y: Float, durationMs: Long, runTimers: Boolean = false): List<NavResult> {
        val (fromX, fromY) = last.getValue(id)
        val start = clock
        return (1..STEPS).flatMap { i ->
            clock = start + durationMs * i / STEPS
            val moved = move(id, fromX + (x - fromX) * i / STEPS, fromY + (y - fromY) * i / STEPS)
            listOfNotNull(moved, if (runTimers) classifier.onTimer(clock) else null)
        }
    }

    /** Down on the edge's strip, ten moves by ([dx], [dy]), up; returns every result. */
    private fun swipe(dx: Float, dy: Float, durationMs: Long = 100, runTimers: Boolean = false): List<NavResult> {
        val (x, y) = when (edge) {
            NavEdge.LEFT -> 5f to 500f
            NavEdge.RIGHT -> 35f to 500f
            NavEdge.BOTTOM -> 500f to 30f
        }
        val results = listOf(down(0, x, y)) + moveTo(0, x + dx, y + dy, durationMs, runTimers)
        return results + up(0, x + dx, y + dy)
    }

    private companion object {
        const val SLOP = 20f
        const val BACK_DISTANCE = 100f
        const val HOME_DISTANCE = 120f
        const val HOLD_MS = 250L
        const val STEPS = 10
    }
}

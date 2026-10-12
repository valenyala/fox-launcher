package dev.foxlauncher.feature.gestures.classifier

import kotlin.test.assertEquals
import org.junit.Test

class VelocityTrackerTest {

    private val tracker = VelocityTracker()

    @Test
    fun steadyMovementGivesItsSpeedOnBothAxes() {
        // 10 px right and 5 px up every 10 ms.
        for (i in 0..10) tracker.add(i * 10L, i * 10f, 1000f - i * 5f)

        val velocity = tracker.velocity()

        assertEquals(1000f, velocity.xPerS, 0.5f)
        assertEquals(-500f, velocity.yPerS, 0.5f)
    }

    @Test
    fun aFingerThatStoppedHasNoVelocity() {
        for (i in 0..10) tracker.add(i * 10L, 0f, i * 20f)
        tracker.add(250, 0f, 200f)

        assertEquals(Velocity(0f, 0f), tracker.velocity())
    }

    @Test
    fun onlyTheLast100msCount() {
        // Slow for half a second, then fast: the slow part has left the window.
        for (i in 0..50) tracker.add(i * 10L, i * 1f, 0f)
        for (i in 1..10) tracker.add(500 + i * 10L, 50f + i * 20f, 0f)

        assertEquals(2000f, tracker.velocity().xPerS, 1f)
    }

    @Test
    fun oneSampleHasNoVelocity() {
        tracker.add(0, 100f, 100f)

        assertEquals(Velocity(0f, 0f), tracker.velocity())
    }

    @Test
    fun samplesAtOneInstantHaveNoVelocity() {
        tracker.add(5, 0f, 0f)
        tracker.add(5, 50f, 0f)

        assertEquals(Velocity(0f, 0f), tracker.velocity())
    }

    @Test
    fun clearForgetsEverything() {
        for (i in 0..10) tracker.add(i * 10L, i * 10f, 0f)
        tracker.clear()
        tracker.add(200, 0f, 0f)

        assertEquals(Velocity(0f, 0f), tracker.velocity())
    }
}

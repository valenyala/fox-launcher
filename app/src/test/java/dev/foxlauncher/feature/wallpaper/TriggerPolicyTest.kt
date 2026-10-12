package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.RotationTrigger
import dev.foxlauncher.core.model.RotationTriggers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class TriggerPolicyTest {

    private val now = 10_000_000L
    private val everyFifteen = RotationTriggers(intervalMinutes = 15)

    @Test
    fun gesturesAndNextNowAlwaysChangeIt() {
        val none = RotationTriggers()

        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.GESTURE, none, lastChangeAt = now, now = now))
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.MANUAL, none, lastChangeAt = now, now = now))
    }

    @Test
    fun noIntervalMeansTheIntervalTriggerDoesNothing() {
        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, RotationTriggers(), lastChangeAt = null, now = now))
    }

    @Test
    fun anIntervalFiresOnceItsTimeHasPassed() {
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, everyFifteen, lastChangeAt = now - minutes(16), now = now))
        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, everyFifteen, lastChangeAt = now - minutes(5), now = now))
    }

    @Test
    fun aSlightlyEarlyIntervalRunStillCounts() {
        // WorkManager may run periodic work a little before the period ends.
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, everyFifteen, lastChangeAt = now - minutes(14), now = now))
    }

    @Test
    fun anIntervalFiresWhenTheWallpaperNeverChanged() {
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, everyFifteen, lastChangeAt = null, now = now))
    }

    @Test
    fun unlockingChangesItOnlyWhenEnabled() {
        val onUnlock = RotationTriggers(onUnlock = true)

        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.UNLOCK, onUnlock, lastChangeAt = now - 60_000, now = now))
        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.UNLOCK, RotationTriggers(), lastChangeAt = now - 60_000, now = now))
    }

    @Test
    fun unlocksInQuickSuccessionChangeItOnce() {
        val onUnlock = RotationTriggers(onUnlock = true)

        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.UNLOCK, onUnlock, lastChangeAt = now - 4_000, now = now))
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.UNLOCK, onUnlock, lastChangeAt = now - 5_000, now = now))
    }

    @Test
    fun pageChangesChangeItOnlyWhenEnabledAndNotTooOften() {
        val onPage = RotationTriggers(onPageChange = true)

        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.PAGE_CHANGE, RotationTriggers(), lastChangeAt = null, now = now))
        assertFalse(TriggerPolicy.shouldAdvance(RotationTrigger.PAGE_CHANGE, onPage, lastChangeAt = now - 500, now = now))
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.PAGE_CHANGE, onPage, lastChangeAt = now - 1_500, now = now))
    }

    @Test
    fun aClockSetBackCountsAsLongAgo() {
        assertTrue(TriggerPolicy.shouldAdvance(RotationTrigger.INTERVAL, everyFifteen, lastChangeAt = now + minutes(60), now = now))
        assertEquals(0L, TriggerPolicy.untilNextInterval(5, lastChangeAt = now + minutes(60), now = now))
    }

    @Test
    fun theShortIntervalTimerWaitsForWhatIsLeft() {
        assertEquals(minutes(3), TriggerPolicy.untilNextInterval(5, lastChangeAt = now - minutes(2), now = now))
        assertEquals(0L, TriggerPolicy.untilNextInterval(5, lastChangeAt = now - minutes(9), now = now))
        assertEquals(0L, TriggerPolicy.untilNextInterval(5, lastChangeAt = null, now = now))
    }

    @Test
    fun theOfferedIntervalsSplitAtWorkManagersMinimum() {
        assertTrue(TriggerPolicy.INTERVALS.any { it < TriggerPolicy.WORK_MANAGER_MIN_MINUTES })
        assertTrue(TriggerPolicy.INTERVALS.any { it >= TriggerPolicy.WORK_MANAGER_MIN_MINUTES })
        assertEquals(TriggerPolicy.INTERVALS.sorted(), TriggerPolicy.INTERVALS)
    }

    private fun minutes(n: Int) = n * 60_000L
}

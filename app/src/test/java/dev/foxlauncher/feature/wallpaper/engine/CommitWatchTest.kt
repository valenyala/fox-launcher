package dev.foxlauncher.feature.wallpaper.engine

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CommitWatchTest {

    private val watch = CommitWatch()

    /** Hands [count] frames to the renderer that never come back committed. */
    private fun dropFrames(count: Int) = repeat(count) {
        assertFalse(watch.stalled())
        watch.shown()
    }

    @Test
    fun framesThatCommitNeverStall() {
        repeat(500) {
            assertFalse(watch.stalled())
            watch.committed(watch.shown())
        }
    }

    @Test
    fun aFrameStillInFlightIsNormal() {
        watch.committed(watch.shown())
        watch.shown()
        assertFalse(watch.stalled())
    }

    @Test
    fun fourFramesNeverCommittedStall() {
        dropFrames(4)
        assertTrue(watch.stalled())
    }

    @Test
    fun whileBindingAgainDoesNotHelpTheWaitDoubles() {
        dropFrames(4)
        assertTrue(watch.stalled())
        watch.bound()
        dropFrames(8)
        assertTrue(watch.stalled())
        watch.bound()
        dropFrames(16)
        assertTrue(watch.stalled())
    }

    @Test
    fun aFrameCommittedAfterTheBindingStartsOver() {
        dropFrames(4)
        assertTrue(watch.stalled())
        watch.bound()
        watch.committed(watch.shown())
        dropFrames(4)
        assertTrue(watch.stalled())
    }

    @Test
    fun aLateCommitFromBeforeTheBindingDoesNotCount() {
        dropFrames(4)
        assertTrue(watch.stalled())
        watch.bound()
        watch.committed(3)
        dropFrames(8)
        assertTrue(watch.stalled())
    }

    @Test
    fun theWaitStopsGrowing() {
        repeat(20) {
            while (!watch.stalled()) watch.shown()
            watch.bound()
        }
        dropFrames(CommitWatch.MAX_WAIT.toInt())
        assertTrue(watch.stalled())
    }
}

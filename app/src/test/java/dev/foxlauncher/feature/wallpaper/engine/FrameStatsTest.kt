package dev.foxlauncher.feature.wallpaper.engine

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class FrameStatsTest {

    private val vsync = 8_333_333L // 120 Hz

    @Test
    fun steadyFramesAreNeverLate() {
        val stats = FrameStats()
        repeat(97) { stats.frame(it * vsync, 2_000_000L) }

        val summary = checkNotNull(stats.summary())

        assertEquals(97, summary.frames)
        assertEquals(0, summary.late)
        assertEquals(8.3f, summary.intervalMedianMs, 0.05f)
        assertEquals(800f, summary.durationMs, 0.1f)
    }

    @Test
    fun aMissedVsyncCountsAsLate() {
        val stats = FrameStats()
        listOf(0L, 1L, 2L, 4L, 5L, 8L).forEach { stats.frame(it * vsync, 1_000_000L) }

        val summary = checkNotNull(stats.summary())

        assertEquals(2, summary.late, "the gaps of two and three vsyncs")
        assertEquals(25f, summary.intervalMaxMs, 0.05f)
    }

    @Test
    fun workIsAveragedAndItsWorstKept() {
        val stats = FrameStats()
        listOf(1_000_000L, 3_000_000L, 8_000_000L).forEachIndexed { i, work -> stats.frame(i * vsync, work) }

        val summary = checkNotNull(stats.summary())

        assertEquals(4f, summary.workAvgMs, 0.01f)
        assertEquals(8f, summary.workMaxMs, 0.01f)
    }

    @Test
    fun nothingRecordedMeansNoSummaryAndResetStartsOver() {
        val stats = FrameStats()
        assertNull(stats.summary())

        stats.frame(0L, 1L)
        stats.reset()

        assertNull(stats.summary())
    }

    @Test
    fun theSummaryReadsInMilliseconds() {
        val stats = FrameStats()
        stats.frame(0L, 2_500_000L)
        stats.frame(vsync, 1_500_000L)

        assertEquals(
            "2 frames in 8.3 ms, work avg 2.0 / max 2.5 ms, intervals median 8.3 / max 8.3 ms, 0 late",
            stats.summary().toString(),
        )
    }
}

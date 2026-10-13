package dev.foxlauncher.feature.wallpaper.engine

import java.util.Locale
import kotlin.math.roundToLong

/** One crossfade's frames (task 4.8): how far apart they came and how long each took to prepare. */
data class FrameSummary(
    val frames: Int,
    val durationMs: Float,
    val workAvgMs: Float,
    val workMaxMs: Float,
    val intervalMedianMs: Float,
    val intervalMaxMs: Float,
    /** Frames that came more than half a frame late: at least one vsync was missed before them. */
    val late: Int,
) {
    override fun toString(): String =
        "$frames frames in ${ms(durationMs)} ms, work avg ${ms(workAvgMs)} / max ${ms(workMaxMs)} ms, " +
            "intervals median ${ms(intervalMedianMs)} / max ${ms(intervalMaxMs)} ms, $late late"

    private fun ms(value: Float) = String.format(Locale.ROOT, "%.1f", value)
}

/**
 * Collects the frame timing of one crossfade (task 4.8): the Choreographer frame times, so the
 * gaps show missed vsyncs whatever the refresh rate, and the time each frame took to record and
 * hand to the renderer. Pure, so it is unit tested.
 */
class FrameStats {

    private val intervalsNs = mutableListOf<Long>()
    private val workNs = mutableListOf<Long>()
    private var firstFrameNs = NONE
    private var lastFrameNs = NONE

    fun frame(frameTimeNs: Long, workNs: Long) {
        if (lastFrameNs == NONE) firstFrameNs = frameTimeNs else intervalsNs += frameTimeNs - lastFrameNs
        lastFrameNs = frameTimeNs
        this.workNs += workNs
    }

    /** The frames so far; null before the first one. */
    fun summary(): FrameSummary? {
        if (workNs.isEmpty()) return null
        val median = intervalsNs.sorted().let { if (it.isEmpty()) 0L else it[it.size / 2] }
        return FrameSummary(
            frames = workNs.size,
            durationMs = ms(lastFrameNs - firstFrameNs),
            workAvgMs = ms(workNs.average().roundToLong()),
            workMaxMs = ms(workNs.max()),
            intervalMedianMs = ms(median),
            intervalMaxMs = ms(intervalsNs.maxOrNull() ?: 0L),
            late = intervalsNs.count { it * 2 > median * LATE_FACTOR_X2 },
        )
    }

    fun reset() {
        intervalsNs.clear()
        workNs.clear()
        firstFrameNs = NONE
        lastFrameNs = NONE
    }

    private fun ms(ns: Long): Float = ns / NS_PER_MS

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val NS_PER_MS = 1_000_000f

        /** Late means more than 1.5 × the usual gap (compared doubled, to stay in whole numbers). */
        const val LATE_FACTOR_X2 = 3
    }
}

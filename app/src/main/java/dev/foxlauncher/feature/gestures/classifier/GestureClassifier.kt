package dev.foxlauncher.feature.gestures.classifier

import dev.foxlauncher.core.model.GestureType
import kotlin.math.abs
import kotlin.math.hypot

enum class PointerPhase { DOWN, MOVE, UP, CANCEL }

/** One pointer event, in arena pixels (y grows downwards). */
data class PointerSample(val pointerId: Int, val x: Float, val y: Float, val timeMs: Long, val phase: PointerPhase)

/**
 * Everything the classifier does not decide itself (ADR-10): system thresholds (the arena reads
 * them from `ViewConfiguration`), the arena's size, where the dock starts, and whether the pager
 * would page for a single-finger swipe to the left (towards the next page) or to the right. The
 * lambdas are read when a gesture's first finger goes down.
 */
class GestureConfig(
    val touchSlopPx: Float,
    val minFlingVelocityPxPerS: Float,
    val longPressMs: Long,
    val doubleTapMs: Long,
    val doubleTapSlopPx: Float,
    val pinchThresholdPx: Float,
    val arenaWidthPx: Float,
    val arenaHeightPx: Float,
    val dockTop: () -> Float = { Float.POSITIVE_INFINITY },
    val pagerTakesSwipeLeft: () -> Boolean = { false },
    val pagerTakesSwipeRight: () -> Boolean = { false },
)

/** What [GestureClassifier.feed] and [GestureClassifier.onTimer] report. */
sealed interface GestureResult {
    data object None : GestureResult

    /** One still finger is down: [GestureClassifier.onTimer] recognizes a long press if it stays so. */
    data object LongPressPending : GestureResult

    /**
     * The gesture is a home gesture, not the pager's or an item's (a vertical drag, a second
     * finger): consume its events from now on. Its type, if any, follows when a finger lifts.
     */
    data object Claimed : GestureResult

    data class Recognized(val type: GestureType) : GestureResult

    /** A CANCEL, or a third finger: the gesture is dropped. */
    data object Cancelled : GestureResult
}

/**
 * Turns pointer samples into [GestureType]s (task 3.2). Pure state machine; one gesture runs from
 * the first finger down to the last finger up.
 *
 * - **Swipe**, decided when a finger lifts: along the dominant axis, past touch slop and faster
 *   than a fling the same way, or past a quarter of the arena whatever the speed. Two fingers down
 *   together make the `_2F` variants; a swipe starting on the dock makes the `DOCK_` variants.
 *   A single-finger horizontal swipe belongs to the pager unless the pager could not page that way
 *   when the finger went down (the first or last page).
 * - **Pinch**: two fingers whose spacing changed by more than the pinch threshold and by more
 *   than their common movement.
 * - **Double tap**: a second tap down within [GestureConfig.doubleTapMs] of the first tap's lift
 *   and [GestureConfig.doubleTapSlopPx] of its position; recognized when it lifts.
 * - **Long press**: one finger, never past slop, for [GestureConfig.longPressMs]; the arena drives
 *   [onTimer]. Afterwards the gesture is over: moving on is an item drag, not a swipe.
 */
class GestureClassifier(private val config: GestureConfig) {

    private val pointers = LinkedHashMap<Int, Track>()
    private var maxPointers = 0
    private var startTimeMs = 0L
    private var startedOnDock = false
    private var pagerTakesLeft = false
    private var pagerTakesRight = false
    private var movedPastSlop = false
    private var claimed = false
    /** Recognized, evaluated or dropped: the rest of the gesture is ignored. */
    private var decided = false
    private var pinchStartSpacing = 0f
    private var isSecondTap = false
    private var lastTap: Tap? = null

    fun feed(sample: PointerSample): GestureResult = when (sample.phase) {
        PointerPhase.DOWN -> onDown(sample)
        PointerPhase.MOVE -> onMove(sample)
        PointerPhase.UP -> onUp(sample)
        PointerPhase.CANCEL -> cancel()
    }

    /** Called by the arena's timer; recognizes a long press once it has lasted long enough. */
    fun onTimer(nowMs: Long): GestureResult {
        if (decided || maxPointers != 1 || pointers.size != 1 || movedPastSlop) return GestureResult.None
        if (nowMs - startTimeMs < config.longPressMs) return GestureResult.None
        decided = true
        return GestureResult.Recognized(GestureType.LONG_PRESS_EMPTY)
    }

    private fun onDown(sample: PointerSample): GestureResult {
        if (pointers.isEmpty()) start(sample)
        // A new finger starts its displacement here, and so does every finger already down.
        pointers.values.forEach { it.rebase() }
        pointers[sample.pointerId] = Track(sample)
        maxPointers = maxOf(maxPointers, pointers.size)
        if (decided) return GestureResult.None
        return when (pointers.size) {
            1 -> GestureResult.LongPressPending
            2 -> {
                pinchStartSpacing = spacing()
                claim()
            }
            else -> {
                decided = true
                GestureResult.Cancelled
            }
        }
    }

    private fun onMove(sample: PointerSample): GestureResult {
        val track = pointers[sample.pointerId] ?: return GestureResult.None
        track.moveTo(sample)
        if (decided || movedPastSlop || hypot(track.dx, track.dy) <= config.touchSlopPx) return GestureResult.None
        movedPastSlop = true
        // A vertical drag is never the pager's; a horizontal one is, until it lifts.
        return if (pointers.size == 1 && abs(track.dy) > abs(track.dx)) claim() else GestureResult.None
    }

    private fun onUp(sample: PointerSample): GestureResult {
        val track = pointers[sample.pointerId] ?: return GestureResult.None
        track.moveTo(sample)
        val result = if (decided) {
            GestureResult.None
        } else {
            decided = true
            when (maxPointers) {
                1 -> evaluateOneFinger(track, sample.timeMs)
                2 -> evaluateTwoFingers()
                else -> GestureResult.None
            }
        }
        pointers.remove(sample.pointerId)
        return result
    }

    private fun cancel(): GestureResult {
        val wasActive = pointers.isNotEmpty() && !decided
        pointers.clear()
        decided = true
        lastTap = null
        return if (wasActive) GestureResult.Cancelled else GestureResult.None
    }

    private fun start(sample: PointerSample) {
        maxPointers = 0
        startTimeMs = sample.timeMs
        startedOnDock = sample.y >= config.dockTop()
        // Read now: by the time the finger lifts, the pager may have paged onto its last page.
        pagerTakesLeft = config.pagerTakesSwipeLeft()
        pagerTakesRight = config.pagerTakesSwipeRight()
        movedPastSlop = false
        claimed = false
        decided = false
        isSecondTap = lastTap?.let { tap ->
            sample.timeMs - tap.upTimeMs <= config.doubleTapMs &&
                hypot(sample.x - tap.x, sample.y - tap.y) <= config.doubleTapSlopPx
        } == true
        lastTap = null
    }

    private fun claim(): GestureResult {
        if (claimed) return GestureResult.None
        claimed = true
        return GestureResult.Claimed
    }

    private fun evaluateOneFinger(track: Track, upTimeMs: Long): GestureResult {
        if (!movedPastSlop) return tap(track, upTimeMs)
        val velocity = track.velocity.velocity()
        val type = if (abs(track.dx) > abs(track.dy)) {
            if (!isSwipe(track.dx, velocity.xPerS, config.arenaWidthPx)) return GestureResult.None
            when {
                track.dx < 0 && !pagerTakesLeft -> GestureType.SWIPE_LEFT
                track.dx > 0 && !pagerTakesRight -> GestureType.SWIPE_RIGHT
                else -> return GestureResult.None
            }
        } else {
            if (!isSwipe(track.dy, velocity.yPerS, config.arenaHeightPx)) return GestureResult.None
            when {
                track.dy < 0 -> if (startedOnDock) GestureType.DOCK_SWIPE_UP else GestureType.SWIPE_UP
                else -> if (startedOnDock) GestureType.DOCK_SWIPE_DOWN else GestureType.SWIPE_DOWN
            }
        }
        return GestureResult.Recognized(type)
    }

    /** A still finger lifted: the second tap of a double tap, or a first tap to remember. */
    private fun tap(track: Track, upTimeMs: Long): GestureResult {
        if (upTimeMs - track.downTimeMs >= config.longPressMs) return GestureResult.None
        if (isSecondTap) return GestureResult.Recognized(GestureType.DOUBLE_TAP)
        lastTap = Tap(upTimeMs, track.downX, track.downY)
        return GestureResult.None
    }

    private fun evaluateTwoFingers(): GestureResult {
        val (a, b) = pointers.values.take(2).takeIf { it.size == 2 } ?: return GestureResult.None
        val spread = spacing() - pinchStartSpacing
        val dx = (a.dx + b.dx) / 2
        val dy = (a.dy + b.dy) / 2
        if (abs(spread) > config.pinchThresholdPx && abs(spread) > hypot(dx, dy)) {
            return GestureResult.Recognized(if (spread < 0) GestureType.PINCH_IN else GestureType.PINCH_OUT)
        }
        val va = a.velocity.velocity()
        val vb = b.velocity.velocity()
        val type = if (abs(dx) > abs(dy)) {
            if (!isSwipe(dx, (va.xPerS + vb.xPerS) / 2, config.arenaWidthPx)) return GestureResult.None
            if (dx < 0) GestureType.SWIPE_LEFT_2F else GestureType.SWIPE_RIGHT_2F
        } else {
            if (!isSwipe(dy, (va.yPerS + vb.yPerS) / 2, config.arenaHeightPx)) return GestureResult.None
            if (dy < 0) GestureType.SWIPE_UP_2F else GestureType.SWIPE_DOWN_2F
        }
        return GestureResult.Recognized(type)
    }

    /** [distance] along one axis is a swipe: past slop and flung the same way, or past a quarter of [extent]. */
    private fun isSwipe(distance: Float, velocity: Float, extent: Float): Boolean {
        val flung = abs(velocity) > config.minFlingVelocityPxPerS && (velocity < 0) == (distance < 0)
        return (abs(distance) > config.touchSlopPx && flung) || abs(distance) > extent * SWIPE_FRACTION
    }

    private fun spacing(): Float {
        val (a, b) = pointers.values.take(2).takeIf { it.size == 2 } ?: return 0f
        return hypot(a.x - b.x, a.y - b.y)
    }

    private class Track(down: PointerSample) {
        val downX = down.x
        val downY = down.y
        val downTimeMs = down.timeMs
        var x = down.x
            private set
        var y = down.y
            private set
        /** Where this finger's current displacement starts: its down, or where it was when another finger landed. */
        private var fromX = down.x
        private var fromY = down.y
        val velocity = VelocityTracker().apply { add(down.timeMs, down.x, down.y) }

        val dx: Float get() = x - fromX
        val dy: Float get() = y - fromY

        fun moveTo(sample: PointerSample) {
            x = sample.x
            y = sample.y
            velocity.add(sample.timeMs, sample.x, sample.y)
        }

        fun rebase() {
            fromX = x
            fromY = y
        }
    }

    private data class Tap(val upTimeMs: Long, val x: Float, val y: Float)

    private companion object {
        /** A slow swipe still counts once it covers this much of the arena. */
        const val SWIPE_FRACTION = 0.25f
    }
}

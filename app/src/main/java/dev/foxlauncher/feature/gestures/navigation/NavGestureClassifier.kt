package dev.foxlauncher.feature.gestures.navigation

import dev.foxlauncher.feature.gestures.classifier.PointerPhase
import dev.foxlauncher.feature.gestures.classifier.PointerSample
import dev.foxlauncher.feature.gestures.classifier.VelocityTracker
import kotlin.math.abs
import kotlin.math.hypot

/** The screen edge a navigation gesture starts on (task 3.8). */
enum class NavEdge { LEFT, RIGHT, BOTTOM }

/** What a navigation gesture asks the system for. */
enum class NavAction { BACK, HOME, RECENTS }

/** Thresholds for [NavGestureClassifier], in pixels; the overlay converts them from dp. */
class NavGestureConfig(
    val touchSlopPx: Float,
    /** A side swipe this far inwards is Back when it lifts. */
    val backDistancePx: Float,
    /** A bottom swipe this far up is Home when it lifts, or Recents once it holds still. */
    val homeDistancePx: Float,
    /** A shorter swipe, past slop, still counts when it lifts at least this fast its way. */
    val flingVelocityPxPerS: Float,
    /** How long a bottom swipe past [homeDistancePx] must stay within slop to be Recents. */
    val holdMs: Long,
)

/** What [NavGestureClassifier.feed] and [NavGestureClassifier.onTimer] report. */
sealed interface NavResult {
    data object None : NavResult

    /** The swipe just went far enough that lifting performs it (once per crossing): a haptic tick. */
    data object Armed : NavResult

    data class Recognized(val action: NavAction) : NavResult
}

/**
 * Turns one edge strip's pointer samples into [NavAction]s (task 3.8). Pure state machine; samples
 * are in the strip's own pixels (y grows downwards), since only movement from the first DOWN counts.
 *
 * - **Back** ([NavEdge.LEFT], [NavEdge.RIGHT]): a mostly horizontal swipe inwards, decided when it
 *   lifts: past [NavGestureConfig.backDistancePx], or past slop and fast. Dragging back out cancels.
 * - **Home** ([NavEdge.BOTTOM]): a swipe up within 45° of vertical, decided the same way with
 *   [NavGestureConfig.homeDistancePx].
 * - **Recents** ([NavEdge.BOTTOM]): past the Home distance, the finger stays within slop for
 *   [NavGestureConfig.holdMs]; the overlay drives [onTimer] at [holdDeadline]. It fires while the
 *   finger is still down, and the lift that follows does nothing.
 *
 * One finger: a second one going down drops the gesture.
 */
class NavGestureClassifier(private val edge: NavEdge, private val config: NavGestureConfig) {

    private val velocity = VelocityTracker()

    /** The finger being followed; null between gestures. */
    private var pointerId: Int? = null

    /** A second finger came, or Recents already fired: the rest of the gesture does nothing. */
    private var finished = false
    private var armed = false
    private var startX = 0f
    private var startY = 0f

    /** Where and when the finger last moved past slop while armed: the hold is measured from here. */
    private var anchorX = 0f
    private var anchorY = 0f
    private var anchorMs = 0L

    fun feed(sample: PointerSample): NavResult {
        val tracked = pointerId
        if (tracked == null) {
            if (sample.phase == PointerPhase.DOWN) start(sample)
            return NavResult.None
        }
        if (sample.pointerId != tracked) {
            if (sample.phase == PointerPhase.DOWN) finished = true
            return NavResult.None
        }
        return when (sample.phase) {
            // The lift never came (the strip was taken down mid-swipe): this is a new gesture.
            PointerPhase.DOWN -> {
                start(sample)
                NavResult.None
            }
            PointerPhase.MOVE -> move(sample)
            PointerPhase.UP -> lift(sample)
            PointerPhase.CANCEL -> {
                pointerId = null
                NavResult.None
            }
        }
    }

    /**
     * When [onTimer] should run to see a held bottom swipe: [NavGestureConfig.holdMs] after the
     * finger last moved, while it is past the Home distance. Null when nothing is pending.
     */
    fun holdDeadline(): Long? =
        if (pointerId != null && !finished && armed && edge == NavEdge.BOTTOM) anchorMs + config.holdMs else null

    fun onTimer(nowMs: Long): NavResult {
        val deadline = holdDeadline() ?: return NavResult.None
        if (nowMs < deadline) return NavResult.None
        finished = true
        return NavResult.Recognized(NavAction.RECENTS)
    }

    private fun start(sample: PointerSample) {
        pointerId = sample.pointerId
        finished = false
        armed = false
        startX = sample.x
        startY = sample.y
        velocity.clear()
        velocity.add(sample.timeMs, sample.x, sample.y)
    }

    private fun move(sample: PointerSample): NavResult {
        velocity.add(sample.timeMs, sample.x, sample.y)
        if (finished) return NavResult.None
        val wasArmed = armed
        armed = travel(sample) >= distance() && isOnAxis(sample)
        return when {
            armed && !wasArmed -> {
                anchor(sample)
                NavResult.Armed
            }
            armed && hypot(sample.x - anchorX, sample.y - anchorY) > config.touchSlopPx -> {
                anchor(sample)
                NavResult.None
            }
            else -> NavResult.None
        }
    }

    private fun lift(sample: PointerSample): NavResult {
        velocity.add(sample.timeMs, sample.x, sample.y)
        pointerId = null
        if (finished || !isOnAxis(sample)) return NavResult.None
        val travel = travel(sample)
        val fast = travel > config.touchSlopPx && inwardVelocity() >= config.flingVelocityPxPerS
        if (travel < distance() && !fast) return NavResult.None
        return NavResult.Recognized(if (edge == NavEdge.BOTTOM) NavAction.HOME else NavAction.BACK)
    }

    private fun anchor(sample: PointerSample) {
        anchorX = sample.x
        anchorY = sample.y
        anchorMs = sample.timeMs
    }

    private fun distance() = if (edge == NavEdge.BOTTOM) config.homeDistancePx else config.backDistancePx

    /** How far the finger went away from its edge, into the screen. */
    private fun travel(sample: PointerSample): Float = when (edge) {
        NavEdge.LEFT -> sample.x - startX
        NavEdge.RIGHT -> startX - sample.x
        NavEdge.BOTTOM -> startY - sample.y
    }

    /** Back: more sideways than vertical. Home: within 45° of straight up. */
    private fun isOnAxis(sample: PointerSample): Boolean {
        val dx = abs(sample.x - startX)
        val dy = abs(sample.y - startY)
        return if (edge == NavEdge.BOTTOM) dy >= dx else dx > dy
    }

    private fun inwardVelocity(): Float {
        val v = velocity.velocity()
        return when (edge) {
            NavEdge.LEFT -> v.xPerS
            NavEdge.RIGHT -> -v.xPerS
            NavEdge.BOTTOM -> -v.yPerS
        }
    }
}

package dev.foxlauncher.feature.gestures.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.gestures.classifier.PointerPhase
import dev.foxlauncher.feature.gestures.classifier.PointerSample

/**
 * The navigation gestures' touch zones over other apps (task 3.8, ADR-20): three invisible strips
 * along the left, right and bottom edges, each an accessibility-overlay window. Only an
 * accessibility service may add those, so [context] is Fox's service; they need no other
 * permission and sit above apps and the system bars. Each strip feeds its own
 * [NavGestureClassifier] and hands what it recognizes to [perform].
 *
 * A touch that starts on a strip never reaches the app below, so the strips are thin, and the side
 * ones start below the status bar and app bar. They draw nothing; a fourth, full-screen window that
 * takes no touches shows each gesture as it runs ([NavFeedbackView]).
 *
 * All four windows live on a thread of their own: their touches, the feedback's frames and adding
 * and removing them never wait for Fox's main thread, nor hold it up. Home from another app brings
 * the home back while the strips come down, and removing them on the main thread cost the home
 * 25–56 ms before its first frame (POCO F3). [show], [hide] and [release] post to that thread.
 */
class NavigationOverlay(private val context: Context, private val perform: (NavAction) -> Unit) {

    private val thread = HandlerThread("FoxNavigation", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private val config = NavGestureConfig(
        touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        backDistancePx = BACK_DISTANCE_DP * density,
        homeDistancePx = HOME_DISTANCE_DP * density,
        flingVelocityPxPerS = FLING_DP_PER_S * density,
        holdMs = HOLD_MS,
    )

    private val feedback = NavFeedbackView(context, HOLD_MS).also { it.layoutParams = feedbackParams() }

    // The bottom strip goes last, so it is on top where the side strips reach down to it.
    private val strips = listOf(NavEdge.LEFT, NavEdge.RIGHT, NavEdge.BOTTOM).map { edge ->
        EdgeStrip(context, edge, NavGestureClassifier(edge, config), ::onResult, ::onTouched).also { it.layoutParams = params(edge) }
    }

    fun show() {
        handler.post(::addWindows)
    }

    fun hide() {
        handler.post(::removeWindows)
    }

    /** Takes the windows down for good and ends their thread. */
    fun release() {
        handler.post {
            removeWindows()
            thread.quitSafely()
        }
    }

    // A strip's parent is its window's ViewRootImpl from addView to removeView. isAttachedToWindow
    // only turns true at the first traversal, too late for a hide that follows a show at once.

    private fun addWindows() {
        for (view in listOf(feedback) + strips) {
            if (view.parent != null) continue
            try {
                windowManager.addView(view, view.layoutParams)
            } catch (e: WindowManager.BadTokenException) {
                // The service is being unbound: the system takes its windows with it.
                FoxLog.w("Navigation strip not added", e)
                return
            }
        }
    }

    private fun removeWindows() {
        feedback.reset()
        for (view in strips + feedback) {
            if (view.parent != null) windowManager.removeViewImmediate(view)
        }
    }

    /** A tick as a Back swipe goes far enough, a long-press buzz as a hold turns the lift into Recents. */
    private fun onResult(strip: EdgeStrip, result: NavResult) {
        when (result) {
            NavResult.None -> Unit
            NavResult.Armed -> if (strip.edge != NavEdge.BOTTOM) strip.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            NavResult.Held -> {
                strip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                feedback.hold()
            }
            is NavResult.Recognized -> {
                FoxLog.d("Navigation gesture: ${result.action}")
                feedback.end(result.action)
                perform(result.action)
            }
        }
    }

    /** After each touch event on [strip], its classifier fed: the bubble follows, or goes. */
    private fun onTouched(strip: EdgeStrip, event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) feedback.begin(strip.edge)
        val classifier = strip.classifier
        val progress = classifier.progress()
        if (progress == null) {
            feedback.end(null)
        } else {
            // The feedback window covers the screen from its corner, so raw coordinates are its own.
            feedback.update(event.rawX, event.rawY, progress, classifier.isArmed, classifier.holdDeadline(), classifier.isHeld)
        }
    }

    private fun feedbackParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        title = "Fox navigation feedback"
    }

    private fun params(edge: NavEdge): WindowManager.LayoutParams {
        val side = (SIDE_WIDTH_DP * density).toInt()
        val bottom = (BOTTOM_HEIGHT_DP * density).toInt()
        return WindowManager.LayoutParams(
            if (edge == NavEdge.BOTTOM) WindowManager.LayoutParams.MATCH_PARENT else side,
            if (edge == NavEdge.BOTTOM) bottom else WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Absolute sides: the gestures follow the screen's edges, not the reading direction.
            gravity = when (edge) {
                NavEdge.LEFT -> Gravity.TOP or Gravity.LEFT
                NavEdge.RIGHT -> Gravity.TOP or Gravity.RIGHT
                NavEdge.BOTTOM -> Gravity.BOTTOM or Gravity.LEFT
            }
            // A side strip is screen-high from here down, so it runs past the bottom, under the bottom strip.
            y = if (edge == NavEdge.BOTTOM) 0 else (SIDE_TOP_DP * density).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Over the navigation bar too: that is where a swipe up from the bottom starts.
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            title = "Fox navigation $edge"
        }
    }

    private companion object {
        const val SIDE_WIDTH_DP = 12
        const val BOTTOM_HEIGHT_DP = 14

        /** Below the status bar and a typical app bar, whose buttons sit near the left edge. */
        const val SIDE_TOP_DP = 96
        const val BACK_DISTANCE_DP = 40
        const val HOME_DISTANCE_DP = 64
        const val FLING_DP_PER_S = 800
        const val HOLD_MS = 250L
    }
}

/**
 * One strip: every touch on it goes to [classifier], with a timer for the hold that makes the lift Recents;
 * [onResult] hears what the classifier says, [onTouched] each event once it has been fed. It is a
 * touch zone only, so it has nothing to click and nothing for accessibility to focus.
 */
@SuppressLint("ViewConstructor")
private class EdgeStrip(
    context: Context,
    val edge: NavEdge,
    val classifier: NavGestureClassifier,
    private val onResult: (EdgeStrip, NavResult) -> Unit,
    private val onTouched: (EdgeStrip, MotionEvent) -> Unit,
) : View(context) {

    private val holdCheck = Runnable { onResult(this, classifier.onTimer(SystemClock.uptimeMillis())) }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    // Lint wants performClick from onTouchEvent; a strip is never clicked (see the class comment).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        event.forEachSample { onResult(this, classifier.feed(it)) }
        onTouched(this, event)
        removeCallbacks(holdCheck)
        // MotionEvent times are uptime, the clock the timer runs on.
        classifier.holdDeadline()?.let { postDelayed(holdCheck, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(holdCheck)
        super.onDetachedFromWindow()
    }
}

/** Every pointer sample in [this], batched history first (a 120 Hz screen batches several per event). */
private inline fun MotionEvent.forEachSample(feed: (PointerSample) -> Unit) {
    when (actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> feed(sample(actionIndex, PointerPhase.DOWN))
        MotionEvent.ACTION_MOVE -> {
            for (h in 0 until historySize) {
                for (i in 0 until pointerCount) {
                    feed(PointerSample(getPointerId(i), getHistoricalX(i, h), getHistoricalY(i, h), getHistoricalEventTime(h), PointerPhase.MOVE))
                }
            }
            for (i in 0 until pointerCount) feed(sample(i, PointerPhase.MOVE))
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> feed(sample(actionIndex, PointerPhase.UP))
        MotionEvent.ACTION_CANCEL -> for (i in 0 until pointerCount) feed(sample(i, PointerPhase.CANCEL))
    }
}

private fun MotionEvent.sample(index: Int, phase: PointerPhase) =
    PointerSample(getPointerId(index), getX(index), getY(index), eventTime, phase)

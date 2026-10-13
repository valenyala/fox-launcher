package dev.foxlauncher.feature.gestures.navigation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * What a navigation gesture looks like while it runs (task 3.8): a bubble slides in from the edge
 * the swipe started on, level with the finger, carrying the action's glyph: an arrow for Back, a
 * circle for Home, a square once Recents opens. It takes the accent colour once lifting would perform
 * the action, and on the bottom edge a ring fills while a hold counts towards Recents. When the
 * action fires the bubble swells and fades; let go short of it and the bubble slides back out.
 *
 * It is the root of a window that takes no touches, and it hides that window ([View.INVISIBLE])
 * whenever no gesture runs, so it costs nothing in between.
 */
@SuppressLint("ViewConstructor")
internal class NavFeedbackView(context: Context, private val holdMs: Long) : View(context) {

    private val density = resources.displayMetrics.density
    private val radius = BUBBLE_RADIUS_DP * density
    private val glyphSize = GLYPH_DP * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = GLYPH_STROKE_DP * density
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = RING_STROKE_DP * density
    }
    private val path = Path()
    private val arc = RectF()

    private var edge = NavEdge.LEFT
    private var fingerX = 0f
    private var fingerY = 0f
    private var progress = 0f
    private var armed = false
    private var holdDeadline: Long? = null

    /** Between [begin] and [end]: the bubble follows the finger. */
    private var active = false

    /** The action that fired, or null when the swipe was let go short of one. */
    private var performed: NavAction? = null

    /** 0 while the gesture runs, then 0 → 1 through the way out. */
    private var exit = 0f
    private var animator: ValueAnimator? = null

    init {
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** A finger went down on the strip along [edge]. */
    fun begin(edge: NavEdge) {
        animator?.cancel()
        this.edge = edge
        active = true
        performed = null
        exit = 0f
        progress = 0f
        armed = false
        holdDeadline = null
        visibility = VISIBLE
        invalidate()
    }

    /** The finger is at ([x], [y]) on the screen; the rest comes from [NavGestureClassifier]. */
    fun update(x: Float, y: Float, progress: Float, armed: Boolean, holdDeadline: Long?) {
        if (!active) return
        fingerX = x
        fingerY = y
        this.progress = progress
        this.armed = armed
        this.holdDeadline = holdDeadline
        invalidate()
    }

    /** The gesture is over: [action] fired, or nothing did. Later calls change nothing. */
    fun end(action: NavAction?) {
        if (!active) return
        active = false
        performed = action
        if (action != null) armed = true
        holdDeadline = null
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (action != null) PERFORMED_MS else RETRACT_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                exit = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) {
                        animator = null
                        visibility = INVISIBLE
                    }
                }
            })
            start()
        }
    }

    /** The window is going away: no animation outlives it. */
    fun reset() {
        animator?.cancel()
        animator = null
        active = false
        visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        if (!active && animator == null) return
        // Let go short of the action: the bubble slides back out. Fired: it holds its place, swells and fades.
        val shown = if (performed == null) progress * (1f - exit) else progress.coerceAtLeast(1f)
        val reveal = shown.coerceIn(0f, 1f)
        if (reveal <= 0f) return
        val stretch = (shown - 1f).coerceIn(0f, 1f) * OVERSHOOT_DP * density
        val inset = -radius + reveal * (2 * radius + MARGIN_DP * density) + stretch
        val (cx, cy) = when (edge) {
            NavEdge.LEFT -> inset to fingerY.coerceIn(radius, height - radius)
            NavEdge.RIGHT -> width - inset to fingerY.coerceIn(radius, height - radius)
            NavEdge.BOTTOM -> fingerX.coerceIn(radius, width - radius) to height - inset
        }
        val fade = if (performed != null) 1f - exit else 1f
        val opacity = ((reveal * 2f).coerceAtMost(1f) * fade * 255).toInt()
        val scale = if (performed != null) 1f + PERFORMED_SWELL * exit else 1f
        val colors = colors()

        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        fill.color = if (armed) colors.accent else colors.surface
        fill.alpha = opacity * SURFACE_ALPHA / 255
        // A soft shadow keeps a light bubble apart from a light app or navigation bar.
        fill.setShadowLayer(SHADOW_DP * density, 0f, density, (opacity * SHADOW_ALPHA / 255) shl 24)
        canvas.drawCircle(cx, cy, radius, fill)
        stroke.color = if (armed) colors.onAccent else colors.onSurface
        stroke.alpha = opacity
        drawGlyph(canvas, cx, cy)
        drawHoldRing(canvas, cx, cy, colors.accent, opacity)
        canvas.restore()
    }

    private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float) {
        val g = glyphSize
        when {
            edge != NavEdge.BOTTOM -> {
                // The arrow points at the edge the swipe came from, as Android's own Back arrow does.
                val towards = if (edge == NavEdge.LEFT) -1f else 1f
                path.reset()
                path.moveTo(cx - towards * g * 0.4f, cy - g)
                path.lineTo(cx + towards * g * 0.5f, cy)
                path.lineTo(cx - towards * g * 0.4f, cy + g)
                canvas.drawPath(path, stroke)
            }
            performed == NavAction.RECENTS ->
                canvas.drawRoundRect(cx - g * 0.8f, cy - g * 0.8f, cx + g * 0.8f, cy + g * 0.8f, g * 0.3f, g * 0.3f, stroke)
            else -> canvas.drawCircle(cx, cy, g * 0.8f, stroke)
        }
    }

    /** Around the Home bubble, while the finger holds still towards Recents. */
    private fun drawHoldRing(canvas: Canvas, cx: Float, cy: Float, color: Int, opacity: Int) {
        val deadline = holdDeadline ?: return
        if (!active || edge != NavEdge.BOTTOM) return
        val held = 1f - (deadline - SystemClock.uptimeMillis()).toFloat() / holdMs
        val r = radius + RING_GAP_DP * density
        arc.set(cx - r, cy - r, cx + r, cy + r)
        ring.color = color
        ring.alpha = opacity
        canvas.drawArc(arc, -90f, 360f * held.coerceIn(0f, 1f), false, ring)
        // A still finger sends no events: keep the ring turning by itself until Recents fires.
        if (held < 1f) postInvalidateOnAnimation()
    }

    private class Colors(val surface: Int, val onSurface: Int, val accent: Int, val onAccent: Int)

    /** The system's dark mode and, from Android 12, its accent; Fox orange before that. */
    private fun colors(): Colors {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val accent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
            night -> FOX_ORANGE_ON_DARK
            else -> FOX_ORANGE
        }
        return if (night) {
            Colors(surface = DARK_SURFACE, onSurface = DARK_ON_SURFACE, accent = accent, onAccent = DARK_ON_ACCENT)
        } else {
            Colors(surface = LIGHT_SURFACE, onSurface = LIGHT_ON_SURFACE, accent = accent, onAccent = LIGHT_ON_ACCENT)
        }
    }

    private companion object {
        const val BUBBLE_RADIUS_DP = 22
        const val GLYPH_DP = 8
        const val GLYPH_STROKE_DP = 2.5f
        const val RING_STROKE_DP = 3
        const val RING_GAP_DP = 4

        /** Between the edge and the bubble once it is all in. */
        const val MARGIN_DP = 12

        /** How much further the bubble follows a swipe past the point that arms it. */
        const val OVERSHOOT_DP = 10
        const val PERFORMED_SWELL = 0.25f
        const val PERFORMED_MS = 220L
        const val RETRACT_MS = 160L
        const val SURFACE_ALPHA = 240
        const val SHADOW_DP = 4
        const val SHADOW_ALPHA = 70

        // Fox's own theme (core/ui/theme/Color.kt): FoxOrange and the dark scheme's primary.
        const val FOX_ORANGE = 0xFFE8622A.toInt()
        const val FOX_ORANGE_ON_DARK = 0xFFFFB59B.toInt()
        const val LIGHT_SURFACE = 0xFFF4F1EE.toInt()
        const val LIGHT_ON_SURFACE = 0xFF2B2119.toInt()
        const val LIGHT_ON_ACCENT = 0xFFFFFFFF.toInt()
        const val DARK_SURFACE = 0xFF2E2622.toInt()
        const val DARK_ON_SURFACE = 0xFFF1DFD7.toInt()
        const val DARK_ON_ACCENT = 0xFF2B1206.toInt()
    }
}

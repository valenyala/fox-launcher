package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import kotlin.math.roundToInt

/**
 * The view of one app widget on Fox's grid (task 6.4), made by [FoxAppWidgetHost.createView]. It
 * tells the widget its size whenever its cells change, and answers the home's questions about it:
 * whether a touch lands on something that scrolls, and what it looks like (for the drag overlay).
 */
class FoxAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private var reportedDp = 0 to 0
    private val location = IntArray(2)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        val dp = (w / density).roundToInt() to (h / density).roundToInt()
        if (w == 0 || h == 0 || dp == reportedDp) return
        reportedDp = dp
        // Not during layout: it is a call to the widget service, and the provider redraws for the new size.
        post { reportSize(dp.first.toFloat(), dp.second.toFloat()) }
    }

    /** The whole area this view is given; the framework subtracts the padding it adds. Portrait only (ADR-14), so min = max. */
    private fun reportSize(widthDp: Float, heightDp: Float) {
        if (appWidgetInfo == null) return // Not bound to a widget (a test's view): nobody to tell.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION") // The list form exists only from API 31.
            updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }

    /** Whether the view under the window point ([x], [y]) or one around it can scroll up or down now. */
    fun scrollsVerticallyAt(x: Float, y: Float): Boolean = scrollsAt(this, x, y)

    private fun scrollsAt(view: View, x: Float, y: Float): Boolean {
        if (!view.isVisible || !view.contains(x, y)) return false
        if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return true
        if (view !is ViewGroup) return false
        return (view.childCount - 1 downTo 0).any { scrollsAt(view.getChildAt(it), x, y) }
    }

    private fun View.contains(x: Float, y: Float): Boolean {
        getLocationInWindow(location)
        return x >= location[0] && x < location[0] + width && y >= location[1] && y < location[1] + height
    }

    /** What it shows now, or null before it is laid out. */
    fun snapshot(): Bitmap? = if (isLaidOut && width > 0 && height > 0) drawToBitmap() else null
}

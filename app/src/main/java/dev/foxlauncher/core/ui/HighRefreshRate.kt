package dev.foxlauncher.core.ui

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display

/** The parts of a `Display.Mode` that choosing one needs; pure so the choice is testable on the JVM. */
data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/** The fastest mode at [current]'s resolution (switching resolution would flash the screen); null when none is faster. */
fun fastestModeAtSameSize(modes: List<DisplayModeSpec>, current: DisplayModeSpec): DisplayModeSpec? =
    modes.filter { it.width == current.width && it.height == current.height }
        .maxByOrNull { it.refreshRate }
        ?.takeIf { it.refreshRate > current.refreshRate }

/**
 * Asks for the display's fastest refresh rate while the user touches the home screen, and hands
 * the choice back to the system [IDLE_MS] after the last finger lifts (flings and page settles
 * finish within that). MIUI keeps third-party apps at 60 Hz unless they ask: on the POCO F3, a
 * 120 Hz panel set to 120 Hz ran Fox at 60 Hz. Asking only while touched keeps an idle home
 * screen from holding the panel at its most power-hungry rate.
 */
class HighRefreshRate(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable { prefer(NO_PREFERENCE) }

    /** A finger went down (or something else is about to animate): go fast now. */
    fun onInteractionStart() {
        handler.removeCallbacks(release)
        fastestModeId()?.let(::prefer)
    }

    /** The last finger lifted: go back to the system's choice once things have settled. */
    fun onInteractionEnd() {
        handler.removeCallbacks(release)
        handler.postDelayed(release, IDLE_MS)
    }

    /** Leaving the screen: let go at once. */
    fun releaseNow() {
        handler.removeCallbacks(release)
        prefer(NO_PREFERENCE)
    }

    private fun prefer(modeId: Int) {
        val attributes = activity.window.attributes
        if (attributes.preferredDisplayModeId == modeId) return
        attributes.preferredDisplayModeId = modeId
        activity.window.attributes = attributes
    }

    private fun fastestModeId(): Int? {
        val display = currentDisplay() ?: return null
        return fastestModeAtSameSize(display.supportedModes.map { it.toSpec() }, display.mode.toSpec())?.id
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }

    private fun Display.Mode.toSpec() = DisplayModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

    private companion object {
        const val IDLE_MS = 3_000L
        const val NO_PREFERENCE = 0
    }
}

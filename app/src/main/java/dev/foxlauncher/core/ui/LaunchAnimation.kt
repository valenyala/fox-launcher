package dev.foxlauncher.core.ui

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect

/** What `LauncherApps.startMainActivity` wants to animate an app open from an icon. */
class LaunchSource(val sourceBounds: Rect, val options: Bundle)

/**
 * Builds a clip-reveal launch from an icon at [boundsInWindow] inside [view] (the Compose host
 * view). `sourceBounds` is in screen coordinates, the reveal origin is relative to [view].
 * See PLATFORM-NOTES S.
 */
fun launchSource(view: View, boundsInWindow: ComposeRect): LaunchSource {
    val inWindow = IntArray(2).also(view::getLocationInWindow)
    val onScreen = IntArray(2).also(view::getLocationOnScreen)
    val left = boundsInWindow.left.roundToInt()
    val top = boundsInWindow.top.roundToInt()
    val width = boundsInWindow.width.roundToInt()
    val height = boundsInWindow.height.roundToInt()
    val screenLeft = left - inWindow[0] + onScreen[0]
    val screenTop = top - inWindow[1] + onScreen[1]
    val options = ActivityOptions.makeClipRevealAnimation(view, left - inWindow[0], top - inWindow[1], width, height)
    return LaunchSource(Rect(screenLeft, screenTop, screenLeft + width, screenTop + height), options.toBundle())
}

package dev.foxlauncher.feature.wallpaper.engine

import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.RenderNode
import android.view.Surface
import dev.foxlauncher.core.common.FoxLog

/**
 * Draws the wallpaper through a [HardwareRenderer] Fox owns, instead of `lockHardwareCanvas`
 * (task 4.7, PLATFORM-NOTES W). On MIUI the canvas lock's hidden renderer can lose the wallpaper's
 * surface when the system swaps it, and then drops every later frame ("The surface had destroyed
 * before draw") while the Java [Surface] still looks valid: the picture freezes. Owning the
 * renderer, Fox binds the surface again on every surface callback, whenever a frame reports it
 * lost, and when frames stop being committed (MIUI swaps it without a callback and drops frames
 * without reporting them), then draws that frame again.
 */
class WallpaperRenderer(private val name: String) {

    private val node = RenderNode(name)
    private val renderer = HardwareRenderer().apply {
        setContentRoot(node)
        isOpaque = true
    }
    private var surface: Surface? = null

    private val commits = CommitWatch()

    /** The surface to draw into, or null when it is gone. */
    fun setSurface(surface: Surface?) {
        this.surface = surface
        bind()
    }

    /**
     * Records one [width]×[height] frame with [record] and shows it at [vsyncNanos]. False when
     * there is no surface to show it on, even after binding it again.
     */
    fun draw(width: Int, height: Int, vsyncNanos: Long, record: (Canvas) -> Unit): Boolean {
        if (surface?.isValid != true) return false
        node.setPosition(0, 0, width, height)
        val canvas = node.beginRecording(width, height)
        try {
            record(canvas)
        } finally {
            node.endRecording()
        }
        if (!commits.stalled()) {
            if (show(vsyncNanos)) return true
            FoxLog.w("$name lost its surface: binding it again")
        } else {
            FoxLog.w("$name: frames are not being committed, binding the surface again")
        }
        bind()
        return show(vsyncNanos)
    }

    fun destroy() {
        renderer.destroy()
        node.discardDisplayList()
    }

    private fun bind() {
        val target = surface?.takeIf { it.isValid }
        renderer.setSurface(target)
        if (target != null) renderer.start()
        commits.bound()
    }

    private fun show(vsyncNanos: Long): Boolean {
        val frame = commits.shown()
        val result = renderer.createRenderRequest()
            .setVsyncTime(vsyncNanos)
            // Runs on the render thread once HWUI draws or skips the frame; frames MIUI drops never get that far.
            .setFrameCommitCallback(Runnable::run) { commits.committed(frame) }
            .syncAndDraw()
        return result and HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND == 0
    }
}

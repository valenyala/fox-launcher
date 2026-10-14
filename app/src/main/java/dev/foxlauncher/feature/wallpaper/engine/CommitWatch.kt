package dev.foxlauncher.feature.wallpaper.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * Notices when the wallpaper renderer's frames stop being committed (PLATFORM-NOTES W). MIUI can
 * swap the wallpaper's surface without a callback; the renderer, still bound to the old one, then
 * has every frame dropped without a word ("The surface had destroyed before draw"), and the picture
 * freezes. A frame's commit callback never comes for those, so a few frames in a row without one
 * mean the surface should be bound again. Pure, so it is unit tested; [committed] comes from the
 * render thread, everything else from the engine's.
 */
class CommitWatch {

    private var shown = 0L
    private val committed = AtomicLong()
    private var boundAt = 0L
    private var wait = FIRST_WAIT

    /** A frame handed to the renderer; its number, for [committed]. */
    fun shown(): Long = ++shown

    /** The renderer committed frame [frame] (render thread). */
    fun committed(frame: Long) {
        committed.accumulateAndGet(frame, ::maxOf)
    }

    /** The surface was bound (again): frames shown before may never be committed. */
    fun bound() {
        boundAt = shown
    }

    /**
     * Before showing a frame: whether the surface should be bound again first. While binding again
     * does not help, the next try waits twice as many frames, up to [MAX_WAIT].
     */
    fun stalled(): Boolean {
        val lastCommitted = committed.get()
        // A frame shown since the last binding made it: the surface works.
        if (lastCommitted > boundAt) wait = FIRST_WAIT
        if (shown - maxOf(lastCommitted, boundAt) < wait) return false
        wait = (wait * 2).coerceAtMost(MAX_WAIT)
        return true
    }

    companion object {
        /** One frame is normally still in flight; four is never normal. */
        const val FIRST_WAIT = 4L
        const val MAX_WAIT = 256L
    }
}

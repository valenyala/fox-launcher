package dev.foxlauncher.feature.wallpaper.engine

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.RotationTrigger
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.wallpaper.TriggerPolicy
import dev.foxlauncher.feature.wallpaper.WallpaperPlaylist
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fox's live wallpaper (ADR-3, task 4.3): the one thing that draws the wallpaper. It shows
 * `activeWallpaperEntryId` (the first enabled entry until the rotation picks one) and crossfades
 * to each new one on a [Choreographer] loop, so a change never flashes. Images are decoded off the
 * main thread at the surface size, a little wider for parallax unless the user turned it off
 * (task 4.7). Frames go through Fox's own [WallpaperRenderer], which survives MIUI swapping the surface.
 *
 * It also runs intervals under 15 minutes (task 4.4), on a timer only while the wallpaper is visible.
 */
@AndroidEntryPoint
class FoxWallpaperService : WallpaperService() {

    @Inject lateinit var settings: FoxSettings

    @Inject lateinit var dao: WallpaperDao

    @Inject lateinit var loader: WallpaperLoader

    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher

    @Inject lateinit var rotation: RotationScheduler

    override fun onCreateEngine(): Engine = FoxEngine()

    private inner class FoxEngine : Engine(), Choreographer.FrameCallback {

        /** Tells engines apart in the log: the system may run a preview engine beside the real one. */
        private val name = "Wallpaper engine ${Integer.toHexString(System.identityHashCode(this))}"
        private val renderer = WallpaperRenderer(name)

        /** Whether the last frame reached the screen; a change is logged once, not every frame. */
        private var framesShown = true
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var surfaceW = 0
        private var surfaceH = 0
        private var visible = false
        private var xOffset = 0f

        /** The image on screen and, during a crossfade, the one fading in: never more than two. */
        private var current: Bitmap? = null
        private var incoming: Bitmap? = null
        private var fadeStartNanos = 0L
        private var fadeMs = FoxSettings.DEFAULT_CROSSFADE_MS

        /** The entry that should be showing, and the load bringing it in. */
        private var wantedId: Long? = null
        private var parallax = true
        private var loadJob: Job? = null

        /** A rotation interval under 15 minutes runs here, while visible (task 4.4). */
        private var intervalMinutes = 0
        private var timerJob: Job? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            FoxLog.d("$name created${if (isPreview) " (preview)" else ""}")
            setOffsetNotificationsEnabled(true)
            scope.launch {
                combine(settings.activeWallpaperEntryId, dao.observeEnabledEntries(), settings.crossfadeMs, settings.wallpaperParallax) { active, entries, ms, parallax ->
                    fadeMs = ms
                    WallpaperPlaylist.showing(active, entries.map { it.id }) to parallax
                }
                    .distinctUntilChanged()
                    .collect { (id, parallax) ->
                        // A new image crossfades in; the same one decoded at another width (parallax switched) just replaces it.
                        // The first image after (re)creation appears without a fade.
                        val animate = current != null && id != wantedId
                        wantedId = id
                        this@FoxEngine.parallax = parallax
                        load(animate)
                    }
            }
            scope.launch {
                settings.rotationTriggers.map { it.intervalMinutes }.distinctUntilChanged().collect {
                    intervalMinutes = it
                    restartTimer()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            FoxLog.d("$name: surface created")
            renderer.setSurface(holder.surface)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            FoxLog.d("$name: surface ${width}x$height")
            renderer.setSurface(holder.surface)
            if (width == surfaceW && height == surfaceH) return
            surfaceW = width
            surfaceH = height
            finishFade()
            load(animate = false)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            renderer.setSurface(holder.surface)
            draw()
        }

        /**
         * The surface is gone; a new one may follow at the same size. Forgetting the size makes that one
         * load and draw again instead of being skipped as unchanged.
         */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            FoxLog.d("$name: surface destroyed")
            Choreographer.getInstance().removeFrameCallback(this)
            incoming?.let { next ->
                current?.recycle()
                current = next
                incoming = null
            }
            surfaceW = 0
            surfaceH = 0
            renderer.setSurface(null)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            FoxLog.d("$name: ${if (visible) "visible" else "hidden"}")
            this.visible = visible
            restartTimer()
            if (visible) {
                draw()
            } else {
                // Nothing animates while hidden; a fade in progress ends at once.
                Choreographer.getInstance().removeFrameCallback(this)
                finishFade()
            }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            this.xOffset = xOffset
            if (visible && incoming == null) draw()
        }

        override fun onComputeColors(): WallpaperColors? = current?.takeUnless { it.isRecycled }?.let(WallpaperColors::fromBitmap)

        override fun onDestroy() {
            FoxLog.d("$name destroyed")
            scope.cancel()
            Choreographer.getInstance().removeFrameCallback(this)
            current?.recycle()
            incoming?.recycle()
            current = null
            incoming = null
            renderer.destroy()
            super.onDestroy()
        }

        /**
         * The short-interval timer: due changes happen while visible, and one that fell due while
         * hidden happens as soon as the wallpaper shows again (with a crossfade).
         */
        private fun restartTimer() {
            timerJob?.cancel()
            val minutes = intervalMinutes
            if (isPreview || !visible || minutes !in 1 until TriggerPolicy.WORK_MANAGER_MIN_MINUTES) return
            timerJob = scope.launch {
                while (isActive) {
                    delay(TriggerPolicy.untilNextInterval(minutes, settings.wallpaperChangedAt.first(), System.currentTimeMillis()))
                    // Not due (something else changed it meanwhile): the loop waits from that change. Due and still
                    // nothing changed (no entries): wait a whole interval rather than spin.
                    val changed = rotation.advanceIfDue(RotationTrigger.INTERVAL) != null
                    val stillDue = TriggerPolicy.untilNextInterval(minutes, settings.wallpaperChangedAt.first(), System.currentTimeMillis()) == 0L
                    if (!changed && stillDue) delay(minutes * MS_PER_MINUTE)
                }
            }
        }

        /** Decodes [wantedId] at the surface size, then shows it with or without a crossfade. */
        private fun load(animate: Boolean) {
            val id = wantedId ?: return
            if (surfaceW == 0 || surfaceH == 0) return // onSurfaceChanged loads it.
            loadJob?.cancel()
            loadJob = scope.launch {
                val entry = withContext(io) { dao.getEntry(id) } ?: return@launch
                val width = if (parallax) (surfaceW * (1 + PARALLAX_EXTRA)).roundToInt() else surfaceW
                val bitmap = withContext(io) { loader.load(entry.uri.toUri(), width, surfaceH) }
                if (bitmap == null) {
                    withContext(io) { dao.markFailed(id) }
                    return@launch
                }
                FoxLog.d("$name: showing entry $id (${bitmap.width}x${bitmap.height}, ${if (animate && visible && current != null) "crossfade" else "at once"})")
                if (animate && visible && current != null) {
                    finishFade()
                    incoming = bitmap
                    fadeStartNanos = 0L
                    Choreographer.getInstance().postFrameCallback(this@FoxEngine)
                } else {
                    finishFade()
                    current?.recycle()
                    current = bitmap
                    draw()
                    notifyColorsChanged()
                }
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (incoming == null) return
            if (fadeStartNanos == 0L) fadeStartNanos = frameTimeNanos
            val elapsedMs = (frameTimeNanos - fadeStartNanos) / NANOS_PER_MS
            if (elapsedMs >= fadeMs) {
                finishFade()
                notifyColorsChanged()
            } else {
                draw(WallpaperGeometry.crossfadeAlpha(elapsedMs, fadeMs), frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        /** Ends a crossfade: the incoming image becomes the only one; the old one is freed. */
        private fun finishFade() {
            val next = incoming ?: return
            current?.recycle()
            current = next
            incoming = null
            draw()
        }

        private fun draw(incomingAlpha: Int = 0, vsyncNanos: Long = System.nanoTime()) {
            if (surfaceW == 0) return
            val shown = renderer.draw(surfaceW, surfaceH, vsyncNanos) { canvas ->
                canvas.drawColor(Color.BLACK)
                current?.let { drawImage(canvas, it, OPAQUE) }
                incoming?.let { if (incomingAlpha > 0) drawImage(canvas, it, incomingAlpha) }
            }
            if (shown != framesShown) {
                framesShown = shown
                if (shown) FoxLog.d("$name: frames reach the screen again") else FoxLog.w("$name: frames are not reaching the screen")
            }
        }

        private fun drawImage(canvas: Canvas, bitmap: Bitmap, alpha: Int) {
            paint.alpha = alpha
            canvas.drawBitmap(bitmap, WallpaperGeometry.parallaxShift(xOffset, bitmap.width, surfaceW), 0f, paint)
        }
    }

    private companion object {
        /** How much wider than the screen the image is, for the page parallax. */
        const val PARALLAX_EXTRA = 0.2f
        const val OPAQUE = 255
        const val NANOS_PER_MS = 1_000_000f
        const val MS_PER_MINUTE = 60_000L
    }
}

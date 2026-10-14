package dev.foxlauncher.feature.wallpaper.engine

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import dev.foxlauncher.BuildConfig
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.framing
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.RotationTrigger
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.wallpaper.TriggerPolicy
import dev.foxlauncher.feature.wallpaper.WallpaperPlaylist
import dev.foxlauncher.feature.wallpaper.source.WallpaperSources
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
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
 * main thread at the surface size, the part of each the user framed (ADR-26); the wallpaper does not
 * move with the pages. Frames go through Fox's own [WallpaperRenderer], which survives MIUI swapping the surface.
 *
 * Memory (task 4.8): an engine holds at most two surface-sized bitmaps, the one showing and the one
 * fading in or being decoded; a fade still running when the next image is wanted ends first.
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

    @Inject lateinit var sources: WallpaperSources

    /** The live engines: the system's and, while the picker shows one, a preview. */
    private val engines = mutableSetOf<FoxEngine>()

    override fun onCreateEngine(): Engine = FoxEngine()

    /**
     * Nothing is decoded ahead of time, so the only thing to give back is a fade's outgoing image:
     * under memory pressure a running crossfade ends at once (task 4.8).
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION") // RUNNING_LOW still arrives for the wallpaper's foreground-like process.
        if (level >= TRIM_MEMORY_RUNNING_LOW) engines.forEach { it.endFade() }
    }

    private inner class FoxEngine : Engine(), Choreographer.FrameCallback {

        /** Tells engines apart in the log: the system may run a preview engine beside the real one. */
        private val name = "Wallpaper engine ${Integer.toHexString(System.identityHashCode(this))}"
        private val renderer = WallpaperRenderer(name)

        /** Frame timing of the running crossfade, logged when it ends; debug builds only (task 4.8). */
        private val frameStats = FrameStats()

        /** Whether the last frame reached the screen; a change is logged once, not every frame. */
        private var framesShown = true
        /**
         * The engine shares the launcher's process: an unexpected failure loading one image is logged,
         * never allowed to take the home screen down with it (task 4.9).
         */
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> FoxLog.e("$name failed", e) },
        )
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val surfaceRect = RectF()
        private var surfaceW = 0
        private var surfaceH = 0
        private var visible = false

        /** The image on screen and, during a crossfade, the one fading in: never more than two. */
        private var current: Bitmap? = null
        private var incoming: Bitmap? = null

        /** Their colours, worked out with the decode: on the main thread they cost 80–190 ms (POCO F3). */
        private var currentColors: WallpaperColors? = null
        private var incomingColors: WallpaperColors? = null

        private var fadeStartNanos = 0L
        private var fadeMs = FoxSettings.DEFAULT_CROSSFADE_MS

        /** The entry that should be showing, and the load bringing it in. */
        private var wantedId: Long? = null
        private var loadJob: Job? = null

        /** A rotation interval under 15 minutes runs here, while visible (task 4.4). */
        private var intervalMinutes = 0
        private var timerJob: Job? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            engines += this
            FoxLog.d("$name created${if (isPreview) " (preview)" else ""}")
            // The wallpaper stays still while the pages move (ADR-26). Without offsets the system also skips its own
            // per-page snapshot of the wallpaper and its 150 ms waits for the engine (PLATFORM-NOTES W).
            setOffsetNotificationsEnabled(false)
            scope.launch {
                combine(settings.activeWallpaperEntryId, dao.observeRotationEntries(), settings.crossfadeMs) { active, entries, ms ->
                    fadeMs = ms
                    val id = WallpaperPlaylist.showing(active, entries.map { it.id })
                    id to entries.firstOrNull { it.id == id }?.framing
                }
                    .distinctUntilChanged()
                    .collect { (id, _) ->
                        // A new image, or a new framing of the one showing, crossfades in.
                        // The first image after (re)creation appears without a fade.
                        val animate = current != null
                        wantedId = id
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
                currentColors = incomingColors
                incomingColors = null
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
                // Hidden by the screen going off (nothing happens otherwise): the lock screen catches up now, a
                // second or more before MIUI delivers ACTION_SCREEN_OFF.
                if (!isPreview) scope.launch { rotation.catchUpLockScreen() }
            }
        }

        override fun onComputeColors(): WallpaperColors? = currentColors

        override fun onDestroy() {
            engines -= this
            FoxLog.d("$name destroyed")
            scope.cancel()
            Choreographer.getInstance().removeFrameCallback(this)
            current?.recycle()
            incoming?.recycle()
            current = null
            incoming = null
            currentColors = null
            incomingColors = null
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
                // Never three images at once: the fade in progress ends before the next one is decoded.
                finishFade()
                val entry = withContext(io) { dao.getEntry(id) } ?: return@launch
                val decoded = withContext(io) {
                    loader.load(entry.uri.toUri(), surfaceW, surfaceH, entry.framing)?.let { it to WallpaperColors.fromBitmap(it) }
                }
                if (decoded == null) {
                    sources.reportUnreadable(id)
                    return@launch
                }
                val (bitmap, colors) = decoded
                // MIUI can swap the surface without a callback and drop every frame unreported (PLATFORM-NOTES W):
                // a new image starts on a fresh binding, whatever happened since the last callback.
                renderer.setSurface(surfaceHolder.surface)
                FoxLog.d("$name: showing entry $id (${bitmap.width}x${bitmap.height}, ${if (animate && visible && current != null) "crossfade" else "at once"})")
                if (animate && visible && current != null) {
                    finishFade()
                    incoming = bitmap
                    incomingColors = colors
                    fadeStartNanos = 0L
                    frameStats.reset()
                    Choreographer.getInstance().postFrameCallback(this@FoxEngine)
                } else {
                    finishFade()
                    current?.recycle()
                    current = bitmap
                    currentColors = colors
                    draw()
                    notifyColorsChanged()
                }
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (incoming == null) return
            val workStart = System.nanoTime()
            if (fadeStartNanos == 0L) fadeStartNanos = frameTimeNanos
            val elapsedMs = (frameTimeNanos - fadeStartNanos) / NANOS_PER_MS
            if (elapsedMs >= fadeMs) {
                finishFade()
                notifyColorsChanged()
                if (BuildConfig.DEBUG) frameStats.summary()?.let { FoxLog.d("$name: crossfade frames: $it") }
            } else {
                draw(WallpaperGeometry.crossfadeAlpha(elapsedMs, fadeMs), frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
                if (BuildConfig.DEBUG) frameStats.frame(frameTimeNanos, System.nanoTime() - workStart)
            }
        }

        /** Under memory pressure: the crossfade jumps to its end, freeing the outgoing image. */
        fun endFade() {
            Choreographer.getInstance().removeFrameCallback(this)
            finishFade()
        }

        /** Ends a crossfade: the incoming image becomes the only one; the old one is freed. */
        private fun finishFade() {
            val next = incoming ?: return
            current?.recycle()
            current = next
            currentColors = incomingColors
            incoming = null
            incomingColors = null
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

        /** The image across the whole surface: it is the surface's size, or smaller when zoomed past its detail. */
        private fun drawImage(canvas: Canvas, bitmap: Bitmap, alpha: Int) {
            paint.alpha = alpha
            surfaceRect.set(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat())
            canvas.drawBitmap(bitmap, null, surfaceRect, paint)
        }
    }

    private companion object {
        const val OPAQUE = 255
        const val NANOS_PER_MS = 1_000_000f
        const val MS_PER_MINUTE = 60_000L
    }
}

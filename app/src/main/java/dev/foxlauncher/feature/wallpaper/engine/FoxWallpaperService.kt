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
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fox's live wallpaper (ADR-3, task 4.3): the one thing that draws the wallpaper. It shows
 * `activeWallpaperEntryId` (the first enabled entry until the rotation picks one) and crossfades
 * to each new one on a [Choreographer] loop, so a change never flashes. Images are decoded off the
 * main thread at the surface size, a little wider for parallax.
 */
@AndroidEntryPoint
class FoxWallpaperService : WallpaperService() {

    @Inject lateinit var settings: FoxSettings

    @Inject lateinit var dao: WallpaperDao

    @Inject lateinit var loader: WallpaperLoader

    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher

    override fun onCreateEngine(): Engine = FoxEngine()

    private inner class FoxEngine : Engine(), Choreographer.FrameCallback {

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
        private var loadJob: Job? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            scope.launch {
                combine(settings.activeWallpaperEntryId, dao.observeEnabledEntries(), settings.crossfadeMs) { active, entries, ms ->
                    fadeMs = ms
                    active?.takeIf { id -> entries.any { it.id == id } } ?: entries.firstOrNull()?.id
                }
                    .distinctUntilChanged()
                    .collect { id ->
                        wantedId = id
                        // The first image after (re)creation appears without a fade.
                        load(animate = current != null)
                    }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width == surfaceW && height == surfaceH) return
            surfaceW = width
            surfaceH = height
            finishFade()
            load(animate = false)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
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
            scope.cancel()
            Choreographer.getInstance().removeFrameCallback(this)
            current?.recycle()
            incoming?.recycle()
            current = null
            incoming = null
            super.onDestroy()
        }

        /** Decodes [wantedId] at the surface size, then shows it with or without a crossfade. */
        private fun load(animate: Boolean) {
            val id = wantedId ?: return
            if (surfaceW == 0 || surfaceH == 0) return // onSurfaceChanged loads it.
            loadJob?.cancel()
            loadJob = scope.launch {
                val entry = withContext(io) { dao.getEntry(id) } ?: return@launch
                val width = (surfaceW * (1 + PARALLAX_EXTRA)).roundToInt()
                val bitmap = withContext(io) { loader.load(entry.uri.toUri(), width, surfaceH) }
                if (bitmap == null) {
                    withContext(io) { dao.markFailed(id) }
                    return@launch
                }
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
                draw(WallpaperGeometry.crossfadeAlpha(elapsedMs, fadeMs))
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

        private fun draw(incomingAlpha: Int = 0) {
            if (surfaceW == 0) return
            val holder = surfaceHolder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: IllegalStateException) {
                FoxLog.w("Wallpaper surface not ready", e)
                return
            } ?: return
            try {
                canvas.drawColor(Color.BLACK)
                current?.let { drawImage(canvas, it, OPAQUE) }
                incoming?.let { if (incomingAlpha > 0) drawImage(canvas, it, incomingAlpha) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
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
    }
}

package dev.foxlauncher.feature.wallpaper.engine

import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Fox's own wallpaper renderer (task 4.7) against a real surface. */
@RunWith(AndroidJUnit4::class)
class WallpaperRendererTest {

    private val reader = ImageReader.newInstance(
        SIZE,
        SIZE,
        PixelFormat.RGBA_8888,
        2,
        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN,
    )
    private val renderer = WallpaperRenderer("test")

    @After
    fun tearDown() {
        renderer.destroy()
        reader.close()
    }

    @Test
    fun aFrameReachesTheSurface() {
        renderer.setSurface(reader.surface)

        assertTrue(renderer.draw(SIZE, SIZE, System.nanoTime()) { it.drawColor(Color.RED) })

        nextImage().use { image ->
            val pixel = image.planes[0].buffer
            assertEquals(listOf(0xFF, 0x00, 0x00, 0xFF), List(4) { pixel.get(it).toInt() and 0xFF }, "one red RGBA pixel")
        }
    }

    @Test
    fun withoutASurfaceNothingIsShown() {
        renderer.setSurface(null)

        assertFalse(renderer.draw(SIZE, SIZE, System.nanoTime()) { it.drawColor(Color.RED) })
    }

    @Test
    fun aReleasedSurfaceIsReportedNotDrawnTo() {
        val surface = reader.surface
        renderer.setSurface(surface)
        surface.release()

        assertFalse(renderer.draw(SIZE, SIZE, System.nanoTime()) { it.drawColor(Color.RED) })
    }

    /** The frame arrives on the render thread, a moment after `draw` returns. */
    private fun nextImage(): Image {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            reader.acquireLatestImage()?.let { return it }
            SystemClock.sleep(POLL_MS)
        }
        error("no frame within $TIMEOUT_MS ms")
    }

    private companion object {
        const val SIZE = 16
        const val TIMEOUT_MS = 3_000L
        const val POLL_MS = 20L
    }
}

package dev.foxlauncher.feature.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The wallpaper loader (tasks 4.3, 4.8) on real JPEGs: exact size, centre crop, EXIF rotation. */
@RunWith(AndroidJUnit4::class)
class WallpaperLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val loader = WallpaperLoader(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    @Test
    fun aLandscapePhotoComesOutScreenSizedFromItsMiddle() {
        // Red, green and blue thirds: a portrait screen keeps the green middle.
        val uri = jpeg(4000, 3000) { canvas, paint ->
            listOf(Color.RED, Color.GREEN, Color.BLUE).forEachIndexed { i, color ->
                paint.color = color
                canvas.drawRect(i * 4000f / 3, 0f, (i + 1) * 4000f / 3, 3000f, paint)
            }
        }

        val bitmap = checkNotNull(loader.load(uri, 1080, 2400))

        assertEquals(1080 to 2400, bitmap.width to bitmap.height)
        listOf(100, 540, 980).forEach { x -> assertDominant(Color.GREEN, bitmap.getPixel(x, 1200), "x=$x") }
    }

    @Test
    fun anExifRotatedPhotoIsDrawnUpright() {
        // Stored landscape, left half red and right half blue, tagged "rotate 90° clockwise to show":
        // upright it is portrait, red on top and blue below.
        val uri = jpeg(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90) { canvas, paint ->
            paint.color = Color.RED
            canvas.drawRect(0f, 0f, 2000f, 3000f, paint)
            paint.color = Color.BLUE
            canvas.drawRect(2000f, 0f, 4000f, 3000f, paint)
        }

        val bitmap = checkNotNull(loader.load(uri, 1080, 2400))

        assertEquals(1080 to 2400, bitmap.width to bitmap.height)
        assertDominant(Color.RED, bitmap.getPixel(100, 100), "top")
        assertDominant(Color.BLUE, bitmap.getPixel(100, 2300), "bottom")
    }

    @Test
    fun aSmallImageIsScaledUpToFill() {
        val uri = jpeg(270, 600) { canvas, _ -> canvas.drawColor(Color.GREEN) }

        val bitmap = checkNotNull(loader.load(uri, 1080, 2400))

        assertEquals(1080 to 2400, bitmap.width to bitmap.height)
    }

    @Test
    fun aMissingFileGivesNothing() {
        assertNull(loader.load(Uri.fromFile(File(context.cacheDir, "missing.jpg")), 1080, 2400))
    }

    private fun jpeg(width: Int, height: Int, orientation: Int? = null, paint: (Canvas, Paint) -> Unit): Uri {
        val bitmap = createBitmap(width, height)
        paint(Canvas(bitmap), Paint())
        val file = File.createTempFile("wallpaper", ".jpg", context.cacheDir).also { files += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (orientation != null) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    /** JPEG shifts colours a little: the expected channel must clearly lead. */
    private fun assertDominant(expected: Int, pixel: Int, where: String) {
        val channels = mapOf(Color.RED to Color.red(pixel), Color.GREEN to Color.green(pixel), Color.BLUE to Color.blue(pixel))
        val strongest = channels.maxBy { it.value }
        assertTrue(strongest.key == expected && strongest.value > DOMINANT, "$where: ${Integer.toHexString(pixel)}")
    }

    private companion object {
        const val DOMINANT = 200
    }
}

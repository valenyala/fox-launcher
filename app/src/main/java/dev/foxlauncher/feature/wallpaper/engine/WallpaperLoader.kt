package dev.foxlauncher.feature.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes a wallpaper image to exactly the size the engine draws (task 4.3): sampled down while
 * decoding, EXIF rotation applied by `ImageDecoder`, then centre-cropped to cover. Blocking: call
 * it off the main thread. Software bitmaps, so `WallpaperColors.fromBitmap` can read them.
 */
class WallpaperLoader @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** The image at [uri] filling [width]×[height], or null when it cannot be read. */
    fun load(uri: Uri, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val decoded = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(WallpaperGeometry.sampleSizeFor(info.size.width, info.size.height, width, height))
            }
        } catch (e: IOException) {
            FoxLog.w("Wallpaper $uri could not be decoded", e)
            return null
        } catch (e: SecurityException) {
            FoxLog.w("Wallpaper $uri is no longer readable", e)
            return null
        }
        val crop = WallpaperGeometry.cropRect(decoded.width, decoded.height, width, height)
        val fitted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(fitted).drawBitmap(
            decoded,
            Rect(crop.left, crop.top, crop.right, crop.bottom),
            Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        decoded.recycle()
        return fitted
    }
}

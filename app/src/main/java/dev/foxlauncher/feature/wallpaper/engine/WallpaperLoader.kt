package dev.foxlauncher.feature.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes a wallpaper image to exactly the size the engine draws (tasks 4.3, 4.8): `ImageDecoder`
 * scales it while decoding so it covers the size, applies EXIF rotation, and crops the centre, so
 * the only bitmap allocated is the final one (a 12 MP photo never exists at full size). Blocking:
 * call it off the main thread. Software bitmaps, so `WallpaperColors.fromBitmap` can read them.
 */
class WallpaperLoader @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * The image at [uri] filling [width]×[height], or null when it cannot be read: missing,
     * unreadable, access revoked, or refused by its provider (the runtime exceptions a provider can
     * send back across processes are caught too, task 4.9).
     */
    fun load(uri: Uri, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val cover = WallpaperGeometry.coverDecode(info.size.width, info.size.height, width, height)
                decoder.setTargetSize(cover.width, cover.height)
                decoder.crop = cover.crop.let { Rect(it.left, it.top, it.right, it.bottom) }
            }
        } catch (e: IOException) {
            FoxLog.w("Wallpaper $uri could not be decoded", e)
            null
        } catch (e: SecurityException) {
            FoxLog.w("Wallpaper $uri is no longer readable", e)
            null
        } catch (e: IllegalArgumentException) {
            // What a documents provider throws for a document whose folder is gone (task 4.9, seen on fox-api33).
            FoxLog.w("Wallpaper $uri is gone", e)
            null
        } catch (e: IllegalStateException) {
            FoxLog.w("Wallpaper $uri could not be opened", e)
            null
        } catch (e: UnsupportedOperationException) {
            FoxLog.w("Wallpaper $uri could not be opened", e)
            null
        }
    }
}

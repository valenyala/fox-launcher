package dev.foxlauncher.feature.wallpaper.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.model.WallpaperFraming
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes the part of a wallpaper image the user framed (tasks 4.3, 4.8, ADR-26): `ImageDecoder`
 * scales it while decoding, applies EXIF rotation, and crops that part, so the only bitmap kept
 * is the final one, never larger than the screen. Blocking: call it off the main thread.
 * Software bitmaps, so `WallpaperColors.fromBitmap` can read them.
 */
class WallpaperLoader @Inject constructor(@param:ApplicationContext private val context: Context) {

    /**
     * The part of the image at [uri] that [framing] shows on a [width]×[height] screen, or null when it
     * cannot be read: missing, unreadable, access revoked, or refused by its provider (the runtime
     * exceptions a provider can send back across processes are caught too, task 4.9).
     */
    fun load(uri: Uri, width: Int, height: Int, framing: WallpaperFraming = WallpaperFraming.CENTERED): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val cover = WallpaperGeometry.framedDecode(info.size.width, info.size.height, width, height, framing)
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

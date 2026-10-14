package dev.foxlauncher.feature.wallpaper.engine

import dev.foxlauncher.core.model.WallpaperFraming
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A rectangle in pixels: [left], [top] inclusive, [right], [bottom] exclusive. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Decode an image at [width]×[height], then keep [crop]: the screen's size, or less when the image
 * has less detail than that, to be stretched when drawn (tasks 4.8, ADR-26).
 */
data class CoverDecode(val width: Int, val height: Int, val crop: CropRect)

/** The part of an image that shows, in fractions of its width and height. */
data class ImageRegion(val left: Float, val top: Float, val width: Float, val height: Float)

/** How a wallpaper image is fitted to the wallpaper surface (tasks 4.3, ADR-26). Pure, so it is unit tested. */
object WallpaperGeometry {

    /** The scale at which [srcW]×[srcH] covers [dstW]×[dstH], overflowing in one dimension at most. */
    fun coverScale(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Float = coverScale(srcW.toFloat(), srcH.toFloat(), dstW.toFloat(), dstH.toFloat())

    private fun coverScale(srcW: Float, srcH: Float, dstW: Float, dstH: Float): Float = max(dstW / srcW, dstH / srcH)

    private fun zoomOf(framing: WallpaperFraming): Float = framing.zoom.coerceIn(1f, WallpaperFraming.MAX_ZOOM)

    /** The part of a [srcW]×[srcH] image a [dstW]×[dstH] screen shows with [framing]; never past the image's edges. */
    fun visibleRegion(srcW: Float, srcH: Float, dstW: Float, dstH: Float, framing: WallpaperFraming): ImageRegion {
        val scale = coverScale(srcW, srcH, dstW, dstH) * zoomOf(framing)
        val width = min(1f, dstW / (scale * srcW))
        val height = min(1f, dstH / (scale * srcH))
        val left = (framing.focusX - width / 2).coerceIn(0f, 1f - width)
        val top = (framing.focusY - height / 2).coerceIn(0f, 1f - height)
        return ImageRegion(left, top, width, height)
    }

    /**
     * How to decode the part of a [srcW]×[srcH] image that [framing] shows on a [dstW]×[dstH] screen,
     * allocating only the final bitmap (task 4.8). Scaled down while decoding to exactly the screen's
     * size when the image has the detail; otherwise cut from the image as it is, smaller than the
     * screen, and stretched when drawn, so a zoomed photo is never decoded huge. With no
     * destination size, the image as it is.
     */
    fun framedDecode(srcW: Int, srcH: Int, dstW: Int, dstH: Int, framing: WallpaperFraming): CoverDecode {
        if (dstW <= 0 || dstH <= 0) return CoverDecode(srcW, srcH, CropRect(0, 0, srcW, srcH))
        val scale = coverScale(srcW, srcH, dstW, dstH) * zoomOf(framing)
        val downscale = scale <= 1f
        val width = if (downscale) max(dstW, (srcW * scale).roundToInt()) else srcW
        val height = if (downscale) max(dstH, (srcH * scale).roundToInt()) else srcH
        val cropW = if (downscale) dstW else (dstW / scale).roundToInt().coerceIn(1, srcW)
        val cropH = if (downscale) dstH else (dstH / scale).roundToInt().coerceIn(1, srcH)
        val left = floor(framing.focusX * width - cropW / 2f).toInt().coerceIn(0, width - cropW)
        val top = floor(framing.focusY * height - cropH / 2f).toInt().coerceIn(0, height - cropH)
        return CoverDecode(width, height, CropRect(left, top, left + cropW, top + cropH))
    }

    /** [framing] after dragging the image by [dx], [dy] screen pixels: it follows the finger, up to its edges. */
    fun moved(framing: WallpaperFraming, dx: Float, dy: Float, srcW: Float, srcH: Float, dstW: Float, dstH: Float): WallpaperFraming {
        val region = visibleRegion(srcW, srcH, dstW, dstH, framing)
        val focusX = region.left + region.width / 2 - dx * region.width / dstW
        val focusY = region.top + region.height / 2 - dy * region.height / dstH
        return settled(WallpaperFraming(focusX, focusY, zoomOf(framing)), srcW, srcH, dstW, dstH)
    }

    /** [framing] after pinching by [factor] around ([centroidX], [centroidY]) on screen: that point stays under the fingers. */
    fun zoomed(
        framing: WallpaperFraming,
        factor: Float,
        centroidX: Float,
        centroidY: Float,
        srcW: Float,
        srcH: Float,
        dstW: Float,
        dstH: Float,
    ): WallpaperFraming {
        val before = visibleRegion(srcW, srcH, dstW, dstH, framing)
        val pointX = before.left + centroidX / dstW * before.width
        val pointY = before.top + centroidY / dstH * before.height
        val zoom = (zoomOf(framing) * factor).coerceIn(1f, WallpaperFraming.MAX_ZOOM)
        val after = visibleRegion(srcW, srcH, dstW, dstH, WallpaperFraming(zoom = zoom))
        val focusX = pointX - centroidX / dstW * after.width + after.width / 2
        val focusY = pointY - centroidY / dstH * after.height + after.height / 2
        return settled(WallpaperFraming(focusX, focusY, zoom), srcW, srcH, dstW, dstH)
    }

    /** [framing] with its focus pulled back to where the image still covers the screen. */
    private fun settled(framing: WallpaperFraming, srcW: Float, srcH: Float, dstW: Float, dstH: Float): WallpaperFraming {
        val region = visibleRegion(srcW, srcH, dstW, dstH, framing)
        return WallpaperFraming(region.left + region.width / 2, region.top + region.height / 2, framing.zoom)
    }

    /**
     * Where to put an image already scaled to [contentW]×[contentH] in a [boxW]×[boxH] box, so the box
     * shows the part [framing] chooses (thumbnails): x and y, zero or negative. Content smaller than
     * the box is centred.
     */
    fun framedOffset(contentW: Float, contentH: Float, boxW: Float, boxH: Float, framing: WallpaperFraming): Pair<Float, Float> {
        fun offset(focus: Float, content: Float, box: Float): Float {
            if (content <= box) return (box - content) / 2
            val shown = box / content
            // 0f - x rather than -x: no negative zero at the start.
            return 0f - (focus - shown / 2).coerceIn(0f, 1f - shown) * content
        }
        return offset(framing.focusX, contentW, boxW) to offset(framing.focusY, contentH, boxH)
    }

    /** The incoming image's alpha, 0–255, [elapsedMs] into a crossfade of [durationMs], eased in and out. */
    fun crossfadeAlpha(elapsedMs: Float, durationMs: Int): Int {
        if (durationMs <= 0) return OPAQUE
        val t = (elapsedMs / durationMs).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return (eased * OPAQUE).roundToInt()
    }

    private const val OPAQUE = 255
}

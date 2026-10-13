package dev.foxlauncher.feature.wallpaper.engine

import kotlin.math.max
import kotlin.math.roundToInt

/** A rectangle in pixels: [left], [top] inclusive, [right], [bottom] exclusive. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Decode an image at [width]×[height], then keep [crop]: exactly the destination's size (task 4.8). */
data class CoverDecode(val width: Int, val height: Int, val crop: CropRect)

/** How a wallpaper image is fitted to the wallpaper surface (task 4.3). Pure, so it is unit tested. */
object WallpaperGeometry {

    /** The scale at which [srcW]×[srcH] covers [dstW]×[dstH], overflowing in one dimension at most. */
    fun coverScale(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Float =
        max(dstW.toFloat() / srcW, dstH.toFloat() / srcH)

    /**
     * How to decode a [srcW]×[srcH] image straight into a [dstW]×[dstH] bitmap: scaled while decoding
     * to cover the destination, then centre-cropped to it, so the only bitmap allocated is the final
     * one (task 4.8). A small image is scaled up. With no destination size, the image as it is.
     */
    fun coverDecode(srcW: Int, srcH: Int, dstW: Int, dstH: Int): CoverDecode {
        if (dstW <= 0 || dstH <= 0) return CoverDecode(srcW, srcH, CropRect(0, 0, srcW, srcH))
        val scale = coverScale(srcW, srcH, dstW, dstH)
        val width = max(dstW, (srcW * scale).roundToInt())
        val height = max(dstH, (srcH * scale).roundToInt())
        val left = (width - dstW) / 2
        val top = (height - dstH) / 2
        return CoverDecode(width, height, CropRect(left, top, left + dstW, top + dstH))
    }

    /** The incoming image's alpha, 0–255, [elapsedMs] into a crossfade of [durationMs], eased in and out. */
    fun crossfadeAlpha(elapsedMs: Float, durationMs: Int): Int {
        if (durationMs <= 0) return OPAQUE
        val t = (elapsedMs / durationMs).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return (eased * OPAQUE).roundToInt()
    }

    /**
     * Where to draw an image [bitmapW] wide on a surface [surfaceW] wide for the launcher's page
     * offset [xOffset] (0 = first page, 1 = last): the extra width slides from left to right.
     */
    fun parallaxShift(xOffset: Float, bitmapW: Int, surfaceW: Int): Float =
        // 0f - x rather than -x: no negative zero at the first page.
        0f - xOffset.coerceIn(0f, 1f) * (bitmapW - surfaceW).coerceAtLeast(0)

    private const val OPAQUE = 255
}

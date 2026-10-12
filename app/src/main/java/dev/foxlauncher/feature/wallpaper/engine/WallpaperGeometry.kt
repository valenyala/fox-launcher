package dev.foxlauncher.feature.wallpaper.engine

import kotlin.math.max
import kotlin.math.roundToInt

/** A rectangle in source-image pixels: [left], [top] inclusive, [right], [bottom] exclusive. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** How a wallpaper image is fitted to the wallpaper surface (task 4.3). Pure, so it is unit tested. */
object WallpaperGeometry {

    /**
     * The largest power-of-two decode sample size that still leaves the image at least
     * [dstW]×[dstH], so the crop never has to scale up a sampled-down image.
     */
    fun sampleSizeFor(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        if (dstW <= 0 || dstH <= 0) return 1
        var sample = 1
        while (srcW / (sample * 2) >= dstW && srcH / (sample * 2) >= dstH) sample *= 2
        return sample
    }

    /** The scale at which [srcW]×[srcH] covers [dstW]×[dstH], overflowing in one dimension at most. */
    fun coverScale(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Float =
        max(dstW.toFloat() / srcW, dstH.toFloat() / srcH)

    /** The centred part of the source that, scaled by [coverScale], fills the destination exactly. */
    fun cropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): CropRect {
        val scale = coverScale(srcW, srcH, dstW, dstH)
        val cropW = (dstW / scale).roundToInt().coerceIn(1, srcW)
        val cropH = (dstH / scale).roundToInt().coerceIn(1, srcH)
        val left = (srcW - cropW) / 2
        val top = (srcH - cropH) / 2
        return CropRect(left, top, left + cropW, top + cropH)
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

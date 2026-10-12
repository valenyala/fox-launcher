package dev.foxlauncher.feature.wallpaper.engine

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class WallpaperGeometryTest {

    // --- sample size ---

    @Test
    fun aHugePhotoIsSampledDownButNotBelowTheTarget() {
        // 4000×3000 for a 1296×2400 surface: halving once would leave 2000×1500, under 2400 high.
        assertEquals(1, WallpaperGeometry.sampleSizeFor(4000, 3000, 1296, 2400))
        // 8000×12000: two halvings leave 2000×3000, still covering; a third would not.
        assertEquals(4, WallpaperGeometry.sampleSizeFor(8000, 12000, 1296, 2400))
    }

    @Test
    fun aSmallImageIsNotSampled() {
        assertEquals(1, WallpaperGeometry.sampleSizeFor(800, 600, 1296, 2400))
    }

    @Test
    fun anUnknownTargetIsNotSampled() {
        assertEquals(1, WallpaperGeometry.sampleSizeFor(8000, 8000, 0, 0))
    }

    // --- cover and crop ---

    @Test
    fun aLandscapePhotoIsCroppedAtTheSidesForAPortraitScreen() {
        val crop = WallpaperGeometry.cropRect(4000, 3000, 1080, 2400)

        assertEquals(3000, crop.height)
        assertEquals(1350, crop.width) // 3000 × 1080 / 2400
        assertEquals((4000 - 1350) / 2, crop.left)
        assertEquals(0, crop.top)
    }

    @Test
    fun aTallImageIsCroppedTopAndBottom() {
        val crop = WallpaperGeometry.cropRect(1080, 4000, 1080, 2400)

        assertEquals(CropRect(0, 800, 1080, 3200), crop)
    }

    @Test
    fun aMatchingImageIsNotCropped() {
        assertEquals(CropRect(0, 0, 1440, 3200), WallpaperGeometry.cropRect(1440, 3200, 1440, 3200))
    }

    @Test
    fun aSmallImageIsScaledUpToCover() {
        assertEquals(4f, WallpaperGeometry.coverScale(270, 600, 1080, 2400))
        assertEquals(CropRect(0, 0, 270, 600), WallpaperGeometry.cropRect(270, 600, 1080, 2400))
    }

    @Test
    fun theCropAlwaysKeepsTheTargetsAspect() {
        listOf(1000 to 1000, 4032 to 3024, 3024 to 4032, 500 to 3000).forEach { (w, h) ->
            val crop = WallpaperGeometry.cropRect(w, h, 1296, 2400)
            val aspect = crop.width.toFloat() / crop.height
            assertEquals(1296f / 2400f, aspect, 0.01f, "$w×$h")
        }
    }

    // --- crossfade ---

    @Test
    fun theCrossfadeGoesFromNothingToOpaque() {
        assertEquals(0, WallpaperGeometry.crossfadeAlpha(0f, 800))
        assertEquals(128, WallpaperGeometry.crossfadeAlpha(400f, 800))
        assertEquals(255, WallpaperGeometry.crossfadeAlpha(800f, 800))
        assertEquals(255, WallpaperGeometry.crossfadeAlpha(5000f, 800))
    }

    @Test
    fun theCrossfadeEasesInAndOut() {
        val alphas = (0..10).map { WallpaperGeometry.crossfadeAlpha(it * 80f, 800) }

        assertTrue(alphas.zipWithNext().all { (a, b) -> b >= a }, "$alphas")
        assertTrue(alphas[1] < 255 / 10, "slow start: $alphas")
        assertTrue(alphas[5] - alphas[4] > alphas[1] - alphas[0], "faster in the middle: $alphas")
    }

    @Test
    fun aZeroDurationIsAnInstantChange() {
        assertEquals(255, WallpaperGeometry.crossfadeAlpha(0f, 0))
    }

    // --- parallax ---

    @Test
    fun parallaxSlidesTheExtraWidthAcrossThePages() {
        assertEquals(0f, WallpaperGeometry.parallaxShift(0f, 1296, 1080))
        assertEquals(-108f, WallpaperGeometry.parallaxShift(0.5f, 1296, 1080))
        assertEquals(-216f, WallpaperGeometry.parallaxShift(1f, 1296, 1080))
    }

    @Test
    fun noExtraWidthMeansNoParallax() {
        assertEquals(0f, WallpaperGeometry.parallaxShift(0.7f, 1080, 1080))
    }
}

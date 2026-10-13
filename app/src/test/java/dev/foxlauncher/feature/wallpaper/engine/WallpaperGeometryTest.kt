package dev.foxlauncher.feature.wallpaper.engine

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class WallpaperGeometryTest {

    // --- cover decode ---

    @Test
    fun aHugePhotoIsDecodedSmallerNotAtFullSize() {
        // 8000×12000 for a 1296×2400 surface: decoded at 1600×2400 (not 96 MP), the sides cropped.
        assertEquals(CoverDecode(1600, 2400, CropRect(152, 0, 1448, 2400)), WallpaperGeometry.coverDecode(8000, 12000, 1296, 2400))
    }

    @Test
    fun aLandscapePhotoIsCroppedAtTheSidesForAPortraitScreen() {
        // 4000×3000 for 1080×2400: scaled by 0.8 to 3200×2400, the middle 1080 kept.
        assertEquals(CoverDecode(3200, 2400, CropRect(1060, 0, 2140, 2400)), WallpaperGeometry.coverDecode(4000, 3000, 1080, 2400))
    }

    @Test
    fun aTallImageIsCroppedTopAndBottom() {
        assertEquals(CoverDecode(1080, 4000, CropRect(0, 800, 1080, 3200)), WallpaperGeometry.coverDecode(1080, 4000, 1080, 2400))
    }

    @Test
    fun aMatchingImageIsNotCropped() {
        assertEquals(CoverDecode(1440, 3200, CropRect(0, 0, 1440, 3200)), WallpaperGeometry.coverDecode(1440, 3200, 1440, 3200))
    }

    @Test
    fun aSmallImageIsScaledUpToCover() {
        assertEquals(4f, WallpaperGeometry.coverScale(270, 600, 1080, 2400))
        assertEquals(CoverDecode(1080, 2400, CropRect(0, 0, 1080, 2400)), WallpaperGeometry.coverDecode(270, 600, 1080, 2400))
    }

    @Test
    fun anUnknownTargetDecodesTheImageAsItIs() {
        assertEquals(CoverDecode(800, 600, CropRect(0, 0, 800, 600)), WallpaperGeometry.coverDecode(800, 600, 0, 0))
    }

    @Test
    fun theCropIsAlwaysExactlyTheTargetInsideTheDecodedImage() {
        listOf(1000 to 1000, 4032 to 3024, 3024 to 4032, 500 to 3000, 1297 to 2401, 7 to 13).forEach { (w, h) ->
            val decode = WallpaperGeometry.coverDecode(w, h, 1296, 2400)
            assertEquals(1296, decode.crop.width, "$w×$h")
            assertEquals(2400, decode.crop.height, "$w×$h")
            assertTrue(decode.crop.left >= 0 && decode.crop.top >= 0, "$w×$h: $decode")
            assertTrue(decode.crop.right <= decode.width && decode.crop.bottom <= decode.height, "$w×$h: $decode")
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

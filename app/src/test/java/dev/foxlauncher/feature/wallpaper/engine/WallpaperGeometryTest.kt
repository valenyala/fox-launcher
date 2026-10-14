package dev.foxlauncher.feature.wallpaper.engine

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class WallpaperGeometryTest {

    // --- cover (decoding with a framing: WallpaperFramingTest) ---

    @Test
    fun aSmallImageNeedsScalingUpToCover() {
        assertEquals(4f, WallpaperGeometry.coverScale(270, 600, 1080, 2400))
        assertEquals(0.8f, WallpaperGeometry.coverScale(4000, 3000, 1080, 2400))
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
}

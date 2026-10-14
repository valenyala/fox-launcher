package dev.foxlauncher.feature.wallpaper.engine

import dev.foxlauncher.core.model.WallpaperFraming
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class WallpaperFramingTest {

    private val centered = WallpaperFraming.CENTERED

    private fun assertNear(expected: Float, actual: Float, message: String = "") =
        assertTrue(abs(expected - actual) < 0.001f, "$message expected $expected, was $actual")

    // --- the region shown ---

    @Test
    fun aLandscapePhotoShowsAStripOfItsWidthOnAPortraitScreen() {
        // 4000×3000 on 1080×2400: the whole height, 1080/2400 ÷ 4/3 = 0.3375 of the width, in the middle.
        val region = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, centered)
        assertNear(0.3375f, region.width)
        assertNear(1f, region.height)
        assertNear((1 - 0.3375f) / 2, region.left)
        assertNear(0f, region.top)
    }

    @Test
    fun theFocusChoosesWhichStripShows() {
        val left = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, WallpaperFraming(focusX = 0f))
        val right = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, WallpaperFraming(focusX = 1f))
        assertNear(0f, left.left, "pinned to the left edge:")
        assertNear(1f, right.left + right.width, "pinned to the right edge:")
    }

    @Test
    fun zoomingInShowsLess() {
        val region = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, WallpaperFraming(zoom = 2f))
        assertNear(0.3375f / 2, region.width)
        assertNear(0.5f, region.height)
        assertNear(0.25f, region.top)
    }

    @Test
    fun theZoomStaysBetweenOneAndTheMaximum() {
        val out = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, WallpaperFraming(zoom = 0.2f))
        val far = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, WallpaperFraming(zoom = 50f))
        assertNear(1f, out.height)
        assertNear(1f / WallpaperFraming.MAX_ZOOM, far.height)
    }

    // --- decoding it ---

    @Test
    fun theDefaultFramingCropsTheMiddle() {
        assertEquals(CoverDecode(1600, 2400, CropRect(152, 0, 1448, 2400)), WallpaperGeometry.framedDecode(8000, 12000, 1296, 2400, centered))
        assertEquals(CoverDecode(3200, 2400, CropRect(1060, 0, 2140, 2400)), WallpaperGeometry.framedDecode(4000, 3000, 1080, 2400, centered))
        assertEquals(CoverDecode(1080, 4000, CropRect(0, 800, 1080, 3200)), WallpaperGeometry.framedDecode(1080, 4000, 1080, 2400, centered))
        assertEquals(CoverDecode(1440, 3200, CropRect(0, 0, 1440, 3200)), WallpaperGeometry.framedDecode(1440, 3200, 1440, 3200, centered))
    }

    @Test
    fun aFocusAtTheEdgeCropsThatEdge() {
        assertEquals(CoverDecode(3200, 2400, CropRect(0, 0, 1080, 2400)), WallpaperGeometry.framedDecode(4000, 3000, 1080, 2400, WallpaperFraming(focusX = 0f)))
        assertEquals(CoverDecode(3200, 2400, CropRect(2120, 0, 3200, 2400)), WallpaperGeometry.framedDecode(4000, 3000, 1080, 2400, WallpaperFraming(focusX = 1f)))
    }

    @Test
    fun aRegionNeedingMoreDetailThanTheImageHasIsDecodedAtTheImagesOwnSize() {
        // Zoom 2 needs a 1.6× scale: the region is cut from the image as it is (675×1500) and stretched when drawn.
        assertEquals(CoverDecode(4000, 3000, CropRect(1662, 750, 2337, 2250)), WallpaperGeometry.framedDecode(4000, 3000, 1080, 2400, WallpaperFraming(zoom = 2f)))
        // A small image is never blown up while decoding.
        assertEquals(CoverDecode(270, 600, CropRect(0, 0, 270, 600)), WallpaperGeometry.framedDecode(270, 600, 1080, 2400, centered))
    }

    @Test
    fun anUnknownTargetDecodesTheImageAsItIs() {
        assertEquals(CoverDecode(800, 600, CropRect(0, 0, 800, 600)), WallpaperGeometry.framedDecode(800, 600, 0, 0, centered))
    }

    @Test
    fun theCropAlwaysHasTheScreensShapeInsideTheDecodedImage() {
        val framings = listOf(centered, WallpaperFraming(0f, 0f), WallpaperFraming(1f, 1f, 3f), WallpaperFraming(0.3f, 0.8f, 1.7f))
        listOf(1000 to 1000, 4032 to 3024, 3024 to 4032, 500 to 3000, 1297 to 2401, 7 to 13, 8000 to 12000).forEach { (w, h) ->
            framings.forEach { framing ->
                val decode = WallpaperGeometry.framedDecode(w, h, 1296, 2400, framing)
                val crop = decode.crop
                assertTrue(crop.left >= 0 && crop.top >= 0 && crop.right <= decode.width && crop.bottom <= decode.height, "$w×$h $framing: $decode")
                assertTrue(crop.width >= 1 && crop.height >= 1, "$w×$h $framing: $decode")
                assertTrue(crop.width <= 1296 && crop.height <= 2400, "never larger than the screen: $w×$h $framing: $decode")
                val aspect = crop.width.toFloat() / crop.height
                assertTrue(abs(aspect - 1296f / 2400) < 2f / crop.height + 0.01f, "$w×$h $framing: $decode")
            }
        }
    }

    // --- moving it ---

    @Test
    fun draggingTheImageRightShowsMoreOfItsLeft() {
        // The strip is 0.3375 of the width across 1080 px: dragging 108 px moves it a tenth of that.
        val moved = WallpaperGeometry.moved(centered, dx = 108f, dy = 0f, srcW = 4000f, srcH = 3000f, dstW = 1080f, dstH = 2400f)
        assertNear(0.5f - 0.03375f, moved.focusX)
        assertNear(0.5f, moved.focusY)
    }

    @Test
    fun draggingStopsAtTheImagesEdge() {
        val moved = WallpaperGeometry.moved(centered, dx = 100_000f, dy = -100_000f, srcW = 4000f, srcH = 3000f, dstW = 1080f, dstH = 2400f)
        assertNear(0.3375f / 2, moved.focusX)
        assertNear(0.5f, moved.focusY, "the whole height shows, so it cannot move up or down:")
    }

    @Test
    fun pinchingKeepsThePointUnderTheFingersInPlace() {
        // Zoom 2 around a point a quarter of the way across and down the screen.
        val start = WallpaperFraming(zoom = 1.5f)
        val before = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, start)
        val zoomed = WallpaperGeometry.zoomed(start, factor = 2f, centroidX = 270f, centroidY = 600f, srcW = 4000f, srcH = 3000f, dstW = 1080f, dstH = 2400f)
        val after = WallpaperGeometry.visibleRegion(4000f, 3000f, 1080f, 2400f, zoomed)
        assertNear(3f, zoomed.zoom)
        assertNear(before.left + 0.25f * before.width, after.left + 0.25f * after.width, "x under the fingers:")
        assertNear(before.top + 0.25f * before.height, after.top + 0.25f * after.height, "y under the fingers:")
    }

    @Test
    fun pinchingOutStopsAtTheWholeImage() {
        val zoomed = WallpaperGeometry.zoomed(WallpaperFraming(0.1f, 0.5f, 1.2f), factor = 0.1f, centroidX = 540f, centroidY = 1200f, srcW = 4000f, srcH = 3000f, dstW = 1080f, dstH = 2400f)
        assertNear(1f, zoomed.zoom)
        assertNear(0.3375f / 2, zoomed.focusX, "back inside the image:")
    }

    // --- thumbnails ---

    @Test
    fun aThumbnailPlacesTheImageToShowTheChosenPart() {
        // A 2000×1000 scaled image in a 500×1000 box: a quarter of its width shows.
        assertEquals(0f to 0f, WallpaperGeometry.framedOffset(2000f, 1000f, 500f, 1000f, WallpaperFraming(focusX = 0f)))
        assertEquals(-750f to 0f, WallpaperGeometry.framedOffset(2000f, 1000f, 500f, 1000f, centered))
        assertEquals(-1500f to 0f, WallpaperGeometry.framedOffset(2000f, 1000f, 500f, 1000f, WallpaperFraming(focusX = 1f)))
    }

    @Test
    fun aThumbnailCentresAnImageSmallerThanItsBox() {
        assertEquals(50f to 0f, WallpaperGeometry.framedOffset(400f, 1000f, 500f, 1000f, WallpaperFraming(focusX = 0f)))
    }
}

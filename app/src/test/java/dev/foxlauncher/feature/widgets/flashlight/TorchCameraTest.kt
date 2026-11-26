package dev.foxlauncher.feature.widgets.flashlight

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TorchCameraTest {

    @Test
    fun theBackCameraWithAFlashWins() {
        val cameras = listOf(
            CameraFlashInfo("1", hasFlash = true, facingBack = false),
            CameraFlashInfo("2", hasFlash = false, facingBack = true),
            CameraFlashInfo("0", hasFlash = true, facingBack = true),
        )
        assertEquals("0", pickTorchCamera(cameras))
    }

    @Test
    fun anyFlashWhenNoBackCameraHasOne() {
        val cameras = listOf(
            CameraFlashInfo("0", hasFlash = false, facingBack = true),
            CameraFlashInfo("1", hasFlash = true, facingBack = false),
        )
        assertEquals("1", pickTorchCamera(cameras))
    }

    @Test
    fun noFlashNoCamera() {
        assertNull(pickTorchCamera(listOf(CameraFlashInfo("0", hasFlash = false, facingBack = true))))
        assertNull(pickTorchCamera(emptyList()))
    }
}

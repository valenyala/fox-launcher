package dev.foxlauncher.feature.widgets.flashlight

/** What the torch picker needs to know about one camera: its id, whether it has a flash, and which way it faces. */
data class CameraFlashInfo(val id: String, val hasFlash: Boolean, val facingBack: Boolean)

/** The flash unit is on the back camera; a phone without one may still have a flash on another camera. */
fun pickTorchCamera(cameras: List<CameraFlashInfo>): String? =
    (cameras.firstOrNull { it.hasFlash && it.facingBack } ?: cameras.firstOrNull { it.hasFlash })?.id

/** What the Flashlight widget shows. */
enum class TorchState {
    /** No camera has a flash unit. */
    NONE,

    /** Another app holds the camera, so the torch cannot be switched. */
    BUSY,
    OFF,
    ON,
}

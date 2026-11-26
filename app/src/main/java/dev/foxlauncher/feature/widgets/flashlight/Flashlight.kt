package dev.foxlauncher.feature.widgets.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phone's torch through [CameraManager.setTorchMode]: no permission, no camera opened. Another
 * app that opens the camera turns the torch off and makes it unavailable until it closes the camera.
 */
@Singleton
class Flashlight @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val lookup = Mutex()
    private var looked = false
    private var cameraId: String? = null

    private suspend fun torchCamera(): String? = lookup.withLock {
        if (!looked) {
            cameraId = withContext(io) { findTorchCamera() }
            looked = true
            FoxLog.d("Flashlight camera: $cameraId")
        }
        cameraId
    }

    private fun findTorchCamera(): String? = try {
        pickTorchCamera(
            cameras.cameraIdList.map { id ->
                val characteristics = cameras.getCameraCharacteristics(id)
                CameraFlashInfo(
                    id = id,
                    hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    facingBack = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK,
                )
            },
        )
    } catch (e: CameraAccessException) {
        FoxLog.w("No camera list for the flashlight", e)
        null
    }

    /** The torch's state; the camera service reports the current one as soon as this is collected. */
    fun observe(): Flow<TorchState> = callbackFlow {
        val id = torchCamera()
        if (id == null) {
            send(TorchState.NONE)
            awaitClose()
            return@callbackFlow
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) trySend(if (enabled) TorchState.ON else TorchState.OFF)
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) trySend(TorchState.BUSY)
            }
        }
        cameras.registerTorchCallback(Runnable::run, callback)
        awaitClose { cameras.unregisterTorchCallback(callback) }
    }.flowOn(io)

    /** Turns the torch [on] or off; false when it could not (no flash, or the camera is in use). */
    suspend fun set(on: Boolean): Boolean {
        val id = torchCamera() ?: return false
        return withContext(io) {
            try {
                cameras.setTorchMode(id, on)
                true
            } catch (e: CameraAccessException) {
                FoxLog.w("Flashlight ${if (on) "on" else "off"} failed", e)
                false
            } catch (e: IllegalArgumentException) {
                FoxLog.w("Flashlight camera $id is gone", e)
                false
            }
        }
    }
}

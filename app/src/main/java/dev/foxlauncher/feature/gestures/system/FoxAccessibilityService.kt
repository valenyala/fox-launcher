package dev.foxlauncher.feature.gestures.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dev.foxlauncher.core.common.FoxLog
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Performs the system actions gestures map to (ADR-4, PLATFORM-NOTES G): notifications, quick
 * settings, lock, recents, power menu, screenshot. It asks for no events and reads no window
 * content; the app reaches it through the companion while the system keeps it bound.
 */
class FoxAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The config must name an event type; receive none at all.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        connectedState.value = true
        FoxLog.i("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (instance?.get() === this) instance = null
        connectedState.value = false
    }

    companion object {
        private var instance: WeakReference<FoxAccessibilityService>? = null
        private val connectedState = MutableStateFlow(false)

        /** True while the system has the service bound to Fox. */
        val connected: StateFlow<Boolean> = connectedState.asStateFlow()

        /** `performGlobalAction` on the bound service; false when there is none. */
        fun perform(action: Int): Boolean = instance?.get()?.performGlobalAction(action) ?: false
    }
}

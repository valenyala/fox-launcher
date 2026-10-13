package dev.foxlauncher.feature.gestures.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.gestures.navigation.NavigationGestures
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Performs the system actions gestures map to (ADR-4, PLATFORM-NOTES G): notifications, quick
 * settings, lock, recents, power menu, screenshot. It asks for no events and reads no window
 * content; the app reaches it through the companion while the system keeps it bound. While bound
 * it also runs the navigation gestures over other apps, when they are turned on (task 3.8, ADR-20).
 */
@AndroidEntryPoint
class FoxAccessibilityService : AccessibilityService() {

    @Inject lateinit var navigationGestures: NavigationGestures

    private var navigation: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The config must name an event type; receive none at all.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        connectedState.value = true
        navigation?.cancel()
        navigation = navigationGestures.start(this)
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
        navigation?.cancel()
        navigation = null
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

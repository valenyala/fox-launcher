package dev.foxlauncher.feature.gestures.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.SystemActions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The secure setting listing the enabled accessibility services, `:`-separated flattened components. */
internal object EnabledAccessibilityServices {

    fun read(context: Context): List<ComponentName> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }

    /** Needs WRITE_SECURE_SETTINGS. */
    fun write(context: Context, services: List<ComponentName>) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(":") { it.flattenToString() },
        )
    }
}

/**
 * Restarts Fox's accessibility service when the system has left it stopped (PLATFORM-NOTES G) by
 * turning it off and on in the secure settings, as the user would. Possible only after the user
 * granted WRITE_SECURE_SETTINGS over adb (ADR-18). It only ever touches Fox's own entry, and only
 * while the user has it turned on: it never turns the service on by itself.
 */
@Singleton
class AccessibilityRepair @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val systemActions: SystemActions,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val component = ComponentName(context, FoxAccessibilityService::class.java)
    private val mutex = Mutex()

    /** True while a restart is under way: meanwhile the service is briefly listed as off. */
    @Volatile
    var isRepairing: Boolean = false
        private set

    /** The user has granted the permission, so Fox can restart its service by itself. */
    val isAvailable: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Restarts the service if it is stopped and Fox may; true when it is running afterwards. */
    suspend fun repairIfStopped(): Boolean = mutex.withLock {
        when (systemActions.status) {
            AccessibilityStatus.ON -> return true
            AccessibilityStatus.OFF -> return false
            AccessibilityStatus.STOPPED -> if (!isAvailable) return false
        }
        FoxLog.i("Accessibility service stopped by the system; turning it off and on")
        isRepairing = true
        try {
            val others = withContext(io) { EnabledAccessibilityServices.read(context).filterNot { it == component } }
            withContext(io) { EnabledAccessibilityServices.write(context, others) }
            // The system reacts to each change of the setting: it must see "off" before "on".
            delay(TOGGLE_GAP_MS)
            withContext(io) {
                EnabledAccessibilityServices.write(context, others + component)
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
            val running = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { FoxAccessibilityService.connected.first { it } } != null
            FoxLog.i("Accessibility service ${if (running) "restarted" else "did not come back"}")
            return running
        } catch (e: SecurityException) {
            FoxLog.w("WRITE_SECURE_SETTINGS was revoked", e)
            return false
        } finally {
            isRepairing = false
        }
    }

    private companion object {
        const val TOGGLE_GAP_MS = 500L
        const val CONNECT_TIMEOUT_MS = 3_000L
    }
}

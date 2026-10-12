package dev.foxlauncher.feature.gestures.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.SystemAction
import dev.foxlauncher.feature.gestures.SystemActions
import javax.inject.Inject

/** [SystemActions] through [FoxAccessibilityService]. */
class AccessibilitySystemActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemActions {

    private val component = ComponentName(context, FoxAccessibilityService::class.java)

    /**
     * Connected means on. Otherwise the settings' list of enabled services tells "off" from
     * "stopped": a service the system stopped stays listed (PLATFORM-NOTES G).
     */
    override val status: AccessibilityStatus
        get() = when {
            FoxAccessibilityService.connected.value -> AccessibilityStatus.ON
            isListedAsEnabled() -> AccessibilityStatus.STOPPED
            else -> AccessibilityStatus.OFF
        }

    private fun isListedAsEnabled(): Boolean = component in EnabledAccessibilityServices.read(context)

    override fun perform(action: SystemAction): Boolean = FoxAccessibilityService.perform(
        when (action) {
            SystemAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            SystemAction.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            SystemAction.LOCK_SCREEN -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            SystemAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            SystemAction.POWER_MENU -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            SystemAction.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
        },
    )
}

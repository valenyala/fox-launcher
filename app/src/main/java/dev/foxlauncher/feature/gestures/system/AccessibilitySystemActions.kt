package dev.foxlauncher.feature.gestures.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.feature.gestures.SystemAction
import dev.foxlauncher.feature.gestures.SystemActions
import javax.inject.Inject

/** [SystemActions] through [FoxAccessibilityService]. */
class AccessibilitySystemActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SystemActions {

    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
    private val component = ComponentName(context, FoxAccessibilityService::class.java)

    /** Turned on in the system settings; it may not be bound yet (PLATFORM-NOTES G). */
    override val isEnabled: Boolean
        get() = FoxAccessibilityService.connected.value ||
            accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == component } }

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

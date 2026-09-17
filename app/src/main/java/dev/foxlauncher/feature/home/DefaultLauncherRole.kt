package dev.foxlauncher.feature.home

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import dev.foxlauncher.core.common.FoxLog

/** True when Fox currently holds `ROLE_HOME`, i.e. it is the device's default launcher. */
fun Context.isDefaultLauncher(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
}

/**
 * Asks the system to make Fox the default launcher. Falls back to the Home settings screen on
 * devices whose OEM does not surface the role request dialog.
 */
fun Context.requestDefaultLauncherRole(launcher: ActivityResultLauncher<Intent>) {
    val roleManager = getSystemService(RoleManager::class.java)
    val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_HOME)
    try {
        launcher.launch(intent ?: homeSettingsIntent())
    } catch (e: ActivityNotFoundException) {
        FoxLog.w("No ROLE_HOME request activity, falling back to home settings", e)
        try {
            launcher.launch(homeSettingsIntent())
        } catch (e: ActivityNotFoundException) {
            FoxLog.e("No home settings screen on this device either", e)
        }
    }
}

private fun homeSettingsIntent() = Intent(Settings.ACTION_HOME_SETTINGS)

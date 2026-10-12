package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.apps.AppRepository
import javax.inject.Inject

/** [AppLauncher] through [AppRepository], which launches via `LauncherApps` (PLATFORM-NOTES A). */
class RepositoryAppLauncher @Inject constructor(private val appRepository: AppRepository) : AppLauncher {

    override fun launchApp(component: String, userSerial: Long): Boolean {
        val entry = appRepository.apps.value.firstOrNull { it.component == component && it.userSerial == userSerial }
            ?: return false
        appRepository.launch(entry, sourceBounds = null, opts = null)
        return true
    }

    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean {
        appRepository.startShortcut(packageName, shortcutId, userSerial, sourceBounds = null, opts = null)
        return true
    }
}

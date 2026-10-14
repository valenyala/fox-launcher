package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.feature.gestures.navigation.ButtonlessLaunches
import javax.inject.Inject

/**
 * [AppLauncher] through [AppRepository], which launches via `LauncherApps` (PLATFORM-NOTES A), and
 * through [ButtonlessLaunches] for the apps opened without navigation buttons (task 9.7).
 */
class RepositoryAppLauncher @Inject constructor(
    private val appRepository: AppRepository,
    private val buttonless: ButtonlessLaunches,
) : AppLauncher {

    override fun launchApp(component: String, userSerial: Long): Boolean {
        val entry = appRepository.apps.value.firstOrNull { it.component == component && it.userSerial == userSerial }
            ?: return false
        buttonless.open(entry.packageName, entry.userSerial) { appRepository.launch(entry, sourceBounds = null, opts = null) }
        return true
    }

    override fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean {
        buttonless.open(packageName, userSerial) {
            appRepository.startShortcut(packageName, shortcutId, userSerial, sourceBounds = null, opts = null)
        }
        return true
    }
}

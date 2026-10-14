package dev.foxlauncher.feature.gestures.navigation

import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.MainDispatcher
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import dev.foxlauncher.feature.shizuku.ShizukuStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opens the apps the user listed without navigation buttons (task 9.7, user request, ADR-24): Fox
 * hides the buttons first, so the app starts in full-screen gestures instead of relaunching under
 * the change, and brings them back when its home shows again. Only Fox's own launches count (home,
 * dock, folders, drawer, gestures); an app opened from a notification or Recents leaves the buttons
 * as they are. Without Shizuku ready every app opens as usual.
 */
@Singleton
class ButtonlessLaunches @Inject constructor(
    private val settings: FoxSettings,
    private val navigationButtons: NavigationButtons,
    private val shizuku: ShizukuAccess,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:MainDispatcher private val main: CoroutineDispatcher,
) {
    private val apps: StateFlow<Set<String>> = settings.buttonlessApps.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** One change at a time, so a quick return home cannot overtake the hide before it. */
    private val mutex = Mutex()

    /**
     * Runs [open] for [packageName]'s app or shortcut: straight away, unless the app is listed and
     * Shizuku ready; then on the main thread once the buttons are hidden, well under a second later.
     */
    fun open(packageName: String, userSerial: Long, open: () -> Unit) {
        if (shizuku.status.value != ShizukuStatus.READY || !ButtonlessApps.isListed(apps.value, packageName, userSerial)) {
            open()
            return
        }
        scope.launch(main) {
            mutex.withLock {
                val hide = ButtonlessApps.hideBeforeOpening(listed = true, hiddenNow = navigationButtons.isHidden())
                if (hide && navigationButtons.setHidden(true, awaitApplied = true)) settings.setButtonsHiddenByFox(true)
            }
            open()
        }
    }

    /** From the home's `onStart`: the buttons come back if Fox hid them for an app; it tries again next time if Shizuku is gone. */
    fun onHomeShown() {
        scope.launch {
            mutex.withLock {
                if (!settings.buttonsHiddenByFox.first()) return@withLock
                val show = ButtonlessApps.showOnHome(hiddenByFox = true, hiddenNow = navigationButtons.isHidden())
                if (!show || navigationButtons.setHidden(false)) settings.setButtonsHiddenByFox(false)
            }
        }
    }

    /** The user set the buttons themselves with the switch: home leaves them as they are. */
    fun forget() {
        scope.launch { mutex.withLock { settings.setButtonsHiddenByFox(false) } }
    }
}

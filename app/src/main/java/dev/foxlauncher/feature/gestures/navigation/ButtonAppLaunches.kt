package dev.foxlauncher.feature.gestures.navigation

import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.MainDispatcher
import dev.foxlauncher.core.data.settings.FoxSettings
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
 * Opens the apps the user listed with the navigation buttons while they are hidden elsewhere (task
 * 9.7, user request, ADR-24): Fox shows the buttons first, so the app starts with them instead of
 * relaunching under the change, and hides them again when its home shows. Only Fox's own launches
 * count (home, dock, folders, drawer, gestures); an app opened from a notification or Recents leaves
 * the buttons as they are. When Fox cannot change them (no Shizuku, no adb grant) every app opens as usual.
 */
@Singleton
class ButtonAppLaunches @Inject constructor(
    private val settings: FoxSettings,
    private val navigationButtons: NavigationButtons,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:MainDispatcher private val main: CoroutineDispatcher,
) {
    private val apps: StateFlow<Set<String>> = settings.buttonApps.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** One change at a time, so a quick return home cannot overtake the change before it. */
    private val mutex = Mutex()

    /**
     * Runs [open] for [packageName]'s app or shortcut: straight away, unless the app is listed and
     * Fox can change the buttons; then on the main thread once the buttons show.
     */
    fun open(packageName: String, userSerial: Long, open: () -> Unit) {
        if (!navigationButtons.canChange() || !ButtonApps.isListed(apps.value, packageName, userSerial)) {
            open()
            return
        }
        scope.launch(main) {
            mutex.withLock {
                val show = ButtonApps.showBeforeOpening(listed = true, hiddenNow = navigationButtons.isHidden())
                if (show && navigationButtons.setHidden(false, awaitApplied = true)) settings.setButtonsShownByFox(true)
            }
            open()
        }
    }

    /** From the home's `onStart`: the buttons hide again if Fox showed them for an app; it tries again next time if it cannot now. */
    fun onHomeShown() {
        scope.launch {
            mutex.withLock {
                if (!settings.buttonsShownByFox.first()) return@withLock
                val hide = ButtonApps.hideOnHome(shownByFox = true, hiddenNow = navigationButtons.isHidden())
                if (!hide || navigationButtons.setHidden(true)) settings.setButtonsShownByFox(false)
            }
        }
    }

    /** The user set the buttons themselves with the switch: home leaves them as they are. */
    fun forget() {
        scope.launch { mutex.withLock { settings.setButtonsShownByFox(false) } }
    }
}

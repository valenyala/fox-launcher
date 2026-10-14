package dev.foxlauncher.feature.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update

/**
 * Whether Fox's home, with its own gestures, is what is in front, for the whole process:
 * `HomeActivity` reports as it resumes and pauses, and whether a settings screen covers the home.
 * The navigation gestures (task 3.8) stay off over the home, which has its own, and come on over
 * Fox's settings screens as over other apps (user request, 2026-10-14).
 *
 * It counts resumed instances rather than keeping a flag: during the hand-over of ADR-19 a second
 * instance briefly exists, and its pause may come after the home one resumed.
 */
@Singleton
class HomeVisibility @Inject constructor() {

    private val resumed = MutableStateFlow(0)
    private val settingsOpen = MutableStateFlow(false)

    /** The home itself is in front: Fox resumed and no settings screen over it. */
    val homeGesturesInFront: Flow<Boolean> =
        combine(resumed, settingsOpen) { count, settings -> count > 0 && !settings }.distinctUntilChanged()

    fun onResumed() = resumed.update { it + 1 }

    fun onPaused() = resumed.update { (it - 1).coerceAtLeast(0) }

    /** From the home's composition: whether a settings screen is open over it. */
    fun onSettingsOpen(open: Boolean) {
        settingsOpen.value = open
    }
}

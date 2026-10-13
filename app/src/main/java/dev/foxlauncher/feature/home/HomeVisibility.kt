package dev.foxlauncher.feature.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Whether Fox's home is the app in front, for the whole process: `HomeActivity` reports as it
 * resumes and pauses. The navigation gestures (task 3.8) stay off over the home, which has its own.
 *
 * It counts resumed instances rather than keeping a flag: during the hand-over of ADR-19 a second
 * instance briefly exists, and its pause may come after the home one resumed.
 */
@Singleton
class HomeVisibility @Inject constructor() {

    private val resumed = MutableStateFlow(0)

    val inFront: Flow<Boolean> = resumed.map { it > 0 }.distinctUntilChanged()

    fun onResumed() = resumed.update { it + 1 }

    fun onPaused() = resumed.update { (it - 1).coerceAtLeast(0) }
}

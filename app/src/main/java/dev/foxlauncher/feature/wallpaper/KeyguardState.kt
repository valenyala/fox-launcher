package dev.foxlauncher.feature.wallpaper

import android.app.KeyguardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Whether the lock screen is up, so the live wallpaper can draw the lock screen's picture while it
 * is and crossfade to the home screen's as it goes. [KeyguardManager.isKeyguardLocked] turns false
 * as the unlock starts, earlier than `ACTION_USER_PRESENT`; [refresh] is called wherever Fox hears
 * about the screen going off, the unlock or the home coming to the front.
 */
@Singleton
class KeyguardState @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val _locked = MutableStateFlow(keyguard.isKeyguardLocked)
    val locked: StateFlow<Boolean> = _locked

    private var recheck: Job? = null

    /**
     * Reads the lock screen's state. A read of "locked" is read again shortly after: an unlock that
     * Fox heard nothing about (straight into an app) still ends the lock picture.
     */
    fun refresh() {
        val now = keyguard.isKeyguardLocked
        if (now != _locked.value) FoxLog.d("Keyguard locked: $now")
        _locked.value = now
        recheck?.cancel()
        if (now) {
            recheck = scope.launch {
                RECHECK_DELAYS_MS.forEach {
                    delay(it)
                    _locked.value = keyguard.isKeyguardLocked
                    if (!_locked.value) return@launch
                }
            }
        }
    }

    /** `ACTION_USER_PRESENT`: unlocked, whatever the manager says in the instant. */
    fun onUnlocked() {
        recheck?.cancel()
        if (_locked.value) FoxLog.d("Keyguard locked: false (user present)")
        _locked.value = false
    }

    private companion object {
        val RECHECK_DELAYS_MS = longArrayOf(100, 200, 500)
    }
}

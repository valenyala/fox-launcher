package dev.foxlauncher.feature.gestures.navigation

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.foxlauncher.core.common.MainDispatcher
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.home.HomeVisibility
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Puts the [NavigationOverlay] up while it is wanted (task 3.8): the user turned navigation
 * gestures on, another app or one of Fox's settings screens is in front (the home has its own
 * gestures), the screen is on and unlocked (the lock screen keeps its edges to itself), and that
 * app is not one Fox opened with the navigation buttons (task 9.7: the user wants buttons there).
 */
class NavigationGestures @Inject constructor(
    private val settings: FoxSettings,
    private val homeVisibility: HomeVisibility,
    @param:MainDispatcher private val main: CoroutineDispatcher,
) {

    /** Runs for [service] until the returned job is cancelled, which takes the overlay down. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(service: AccessibilityService): Job {
        val overlay = NavigationOverlay(service) { action -> service.performGlobalAction(action.globalAction) }
        return CoroutineScope(main).launch {
            try {
                combine(
                    settings.navigationGestures,
                    homeVisibility.homeGesturesInFront,
                    screenUnlocked(service),
                    settings.buttonsShownByFox,
                ) { on, homeInFront, unlocked, buttonApp ->
                    on && !homeInFront && unlocked && !buttonApp
                }
                    // The home pauses and resumes around each new intent (a Home press on the home):
                    // only a pause that lasts puts the strips up. Taking them down is immediate.
                    .transformLatest { wanted ->
                        if (wanted) delay(SHOW_DELAY_MS)
                        emit(wanted)
                    }
                    .distinctUntilChanged()
                    .collect { wanted -> if (wanted) overlay.show() else overlay.hide() }
            } finally {
                overlay.hide()
            }
        }
    }

    private val NavAction.globalAction: Int
        get() = when (this) {
            NavAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            NavAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            NavAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }

    private companion object {
        const val SHOW_DELAY_MS = 150L
    }

    /**
     * Screen on and no lock screen showing. `SCREEN_ON`/`OFF` reach only runtime receivers;
     * `USER_PRESENT` comes from SystemUI's uid, which a not-exported receiver refuses (as in
     * `FoxApp`). All three are protected broadcasts, so no app can fake them.
     */
    private fun screenUnlocked(context: Context): Flow<Boolean> = callbackFlow {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> false
                        Intent.ACTION_USER_PRESENT -> true
                        else -> power.isInteractive && !keyguard.isKeyguardLocked
                    },
                )
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(power.isInteractive && !keyguard.isKeyguardLocked)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

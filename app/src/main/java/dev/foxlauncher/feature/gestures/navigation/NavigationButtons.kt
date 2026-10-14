package dev.foxlauncher.feature.gestures.navigation

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.feature.shizuku.ShellCommands
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The system's navigation buttons (task 9.6, user request, ADR-23): whether they are hidden, read
 * from the system each time so a change made in its own settings shows, and hiding or showing them
 * through Shizuku, the shell being allowed what Fox is not. Hiding them turns on full-screen
 * gestures, MIUI's or Android's, which then take Home, Back and Recents.
 */
@Singleton
class NavigationButtons @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizuku: ShizukuAccess,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Xiaomi, Redmi and POCO phones run MIUI, whose navigation ignores Android's overlays. */
    private val miui = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /** Hidden now, or null when this phone does not let Fox read it. */
    suspend fun isHidden(): Boolean? = withContext(io) {
        val resolver = context.contentResolver
        ShellCommands.navigationButtonsHidden(
            miui = miui,
            miuiSetting = if (miui) read { Settings.Global.getInt(resolver, ShellCommands.MIUI_FULL_SCREEN_GESTURES) } else null,
            navigationMode = if (miui) null else read { Settings.Secure.getInt(resolver, ShellCommands.NAVIGATION_MODE) },
        )
    }

    /**
     * True when the shell did it; false when Shizuku is not ready or the command failed. With
     * [awaitApplied], it returns only once the change has reached Fox's own configuration (the
     * system swaps an overlay for it), or after [APPLY_TIMEOUT_MS]: an app opened before that
     * would start in the old mode and relaunch in front of the user (task 9.7).
     */
    suspend fun setHidden(hidden: Boolean, awaitApplied: Boolean = false): Boolean {
        val start = SystemClock.elapsedRealtime()
        val applied = CompletableDeferred<Unit>()
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                applied.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        if (awaitApplied) context.registerComponentCallbacks(callbacks)
        try {
            val done = shizuku.withShell { it.setNavigationButtonsHidden(hidden, miui) } == true
            val seen = done && awaitApplied && withTimeoutOrNull(APPLY_TIMEOUT_MS) { applied.await() } != null
            FoxLog.i(
                "Navigation buttons ${if (hidden) "hidden" else "shown"}: $done" +
                    (if (awaitApplied) ", applied $seen" else "") + " in ${SystemClock.elapsedRealtime() - start} ms",
            )
            return done
        } finally {
            if (awaitApplied) context.unregisterComponentCallbacks(callbacks)
        }
    }

    private companion object {
        const val APPLY_TIMEOUT_MS = 1_500L
    }

    private inline fun read(get: () -> Int): Int? = try {
        get()
    } catch (e: Settings.SettingNotFoundException) {
        // Never set: the system default, buttons.
        0
    } catch (e: SecurityException) {
        // A hidden setting Android keeps from apps targeting a recent API.
        FoxLog.w("Cannot read the navigation setting", e)
        null
    }
}

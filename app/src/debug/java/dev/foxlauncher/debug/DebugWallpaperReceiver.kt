package dev.foxlauncher.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only: moves the wallpaper rotation on, as "Next wallpaper now" does, so
 * `scripts/perf/wallpaper.sh` can measure many changes without a finger (task 4.8):
 * `adb shell am broadcast -a dev.foxlauncher.debug.NEXT_WALLPAPER -p dev.foxlauncher`.
 */
class DebugWallpaperReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun rotation(): RotationScheduler

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val pending = goAsync()
        dependencies.scope().launch {
            try {
                dependencies.rotation().advanceNow()
            } finally {
                pending.finish()
            }
        }
    }
}

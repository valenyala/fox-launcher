package dev.foxlauncher.debug

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.memory.appwidget.MemoryGaugeAppWidget
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only, adb only (DUMP). `NEXT_WALLPAPER` moves the wallpaper rotation on, as "Next
 * wallpaper now" does, so `scripts/perf/wallpaper.sh` can measure many changes without a finger
 * (task 4.8). `PIN_MEMORY_GAUGE` asks the default launcher to pin the RAM gauge app widget, to try
 * it in another launcher (task 5.5).
 * `adb shell am broadcast -a dev.foxlauncher.debug.NEXT_WALLPAPER -p dev.foxlauncher`
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
        if (intent.action == ACTION_PIN_MEMORY_GAUGE) {
            // Android only takes pin requests from an app in front: start a Fox activity first.
            try {
                val pinned = AppWidgetManager.getInstance(context)
                    .requestPinAppWidget(ComponentName(context, MemoryGaugeAppWidget::class.java), null, null)
                FoxLog.d("Pin RAM gauge app widget requested: $pinned")
            } catch (e: IllegalStateException) {
                FoxLog.w("Pin RAM gauge app widget refused: bring a Fox activity to the front first", e)
            }
            return
        }
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

    private companion object {
        const val ACTION_PIN_MEMORY_GAUGE = "dev.foxlauncher.debug.PIN_MEMORY_GAUGE"
    }
}

package dev.foxlauncher.feature.automation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.memory.MemoryBooster
import dev.foxlauncher.feature.memory.MemoryFormat
import dev.foxlauncher.feature.wallpaper.WallpaperAdvancer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Commands from other apps (task 9.4, ADR-22): Tasker and the like send an explicit broadcast to
 * Fox with one of the [AutomationCommand] actions. It does something only while the user has
 * "Commands from other apps" on (off by default); otherwise it ignores the broadcast. An ordered
 * broadcast gets a result: `RESULT_OK` with what happened, or `RESULT_CANCELED` when ignored or
 * when it could not act (no Fox live wallpaper for the wallpaper commands).
 */
class AutomationReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun settings(): FoxSettings

        fun wallpaper(): WallpaperAdvancer

        fun booster(): MemoryBooster

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = AutomationCommand.of(intent.action) ?: return
        val ordered = isOrderedBroadcast
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        dependencies.scope().launch {
            try {
                val (code, data) = if (dependencies.settings().automation.first()) {
                    run(command, dependencies)
                } else {
                    FoxLog.i("Ignored $command: commands from other apps are off")
                    Activity.RESULT_CANCELED to "off"
                }
                if (ordered) pending.setResult(code, data, null)
            } finally {
                // Always, or the broadcast waits until Android gives up on it.
                pending.finish()
            }
        }
    }

    private suspend fun run(command: AutomationCommand, dependencies: Dependencies): Pair<Int, String> {
        FoxLog.i("Command from another app: $command")
        return when (command) {
            AutomationCommand.NEXT_WALLPAPER, AutomationCommand.PREV_WALLPAPER -> {
                val advanced = dependencies.wallpaper().advance(forward = command == AutomationCommand.NEXT_WALLPAPER)
                if (advanced) Activity.RESULT_OK to "changed" else Activity.RESULT_CANCELED to "no live wallpaper"
            }
            AutomationCommand.BOOST -> {
                val result = dependencies.booster().boost()
                Activity.RESULT_OK to "freed ${MemoryFormat.mb(result.freedBytes)} MB"
            }
        }
    }
}

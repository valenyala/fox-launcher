package dev.foxlauncher.feature.memory

import android.app.ActivityManager
import android.app.WallpaperManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.apps.IconCache
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.feature.memory.shizuku.ShizukuAccess
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * One way to free memory (task 5.4, ADR-5). [boost] does the work and says how many other apps it
 * asked to close; how much memory that freed is measured around all strategies by [MemoryBooster].
 */
interface BoostStrategy {
    val name: String

    suspend fun boost(): Int
}

/** Always available: Fox lets go of its own caches (icons, Coil's memory cache), then asks for a GC. */
class SelfTrimStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val iconCache: IconCache,
) : BoostStrategy {

    override val name = "self trim"

    override suspend fun boost(): Int {
        iconCache.clearMemory()
        SingletonImageLoader.get(context).memoryCache?.clear()
        Runtime.getRuntime().gc()
        return 0
    }
}

/**
 * Android 13 and lower only (PLATFORM-NOTES M, ADR-5): `killBackgroundProcesses` for every
 * [BoostCandidates] app. It closes only background and cached processes; anything with a
 * foreground service, such as music playing, is out of its reach, and Android may restart what it
 * closed.
 */
class LegacyKillStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val candidates: BoostCandidates,
) : BoostStrategy {

    override val name = "close background apps"

    override suspend fun boost(): Int {
        val targets = candidates.targets()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        targets.forEach(activityManager::killBackgroundProcesses)
        FoxLog.d("Boost asked ${targets.size} apps to close their background processes")
        return targets.size
    }
}

/**
 * The deep boost (task 9.1, ADR-9, ADR-21): the same apps as [LegacyKillStrategy], closed by the
 * shell through Shizuku, which works where an app's request does not (Android 14+, MIUI). Still
 * background processes only, not a force stop. Runs only while the user has it on and Shizuku
 * allows Fox; otherwise [MemoryBooster] falls back to the other strategies.
 */
class ShizukuBoostStrategy @Inject constructor(
    private val candidates: BoostCandidates,
    private val shizuku: ShizukuAccess,
) : BoostStrategy {

    override val name = "close background apps (Shizuku)"

    override suspend fun boost(): Int {
        val targets = candidates.targets()
        if (targets.isEmpty()) return 0 // No need to start the shell service.
        val closed = shizuku.withShell { it.killBackgroundProcesses(targets.toTypedArray()) } ?: 0
        FoxLog.d("Deep boost asked the shell to close $closed of ${targets.size} apps")
        return closed
    }
}

/** The apps a boost may close (ADR-5): launchable, not system, not [ProtectedApps]. */
class BoostCandidates @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val recentApps: RecentAppsSource,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun targets(): List<String> = withContext(io) {
        val apps = appRepository.apps.value.filter { !it.isWorkProfile }
        val system = apps.filter { appRepository.isSystemApp(it) }.mapTo(HashSet()) { it.packageName }
        BoostTargets.choose(apps.map { it.packageName }, system, protectedApps())
    }

    private fun protectedApps(): ProtectedApps {
        val resolver = context.contentResolver
        return ProtectedApps(
            self = context.packageName,
            foreground = foregroundApp(),
            inputMethod = BoostTargets.packageOfComponent(Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)),
            dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            sms = Telephony.Sms.getDefaultSmsPackage(context),
            accessibility = BoostTargets.accessibilityPackages(Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)),
            liveWallpaper = WallpaperManager.getInstance(context).wallpaperInfo?.packageName,
            shizuku = ShizukuAccess.SHIZUKU_PACKAGE,
        )
    }

    /** The app most recently brought to the front, when usage access allows knowing it. */
    private fun foregroundApp(): String? {
        if (!recentApps.hasAccess()) return null
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - FOREGROUND_WINDOW_MS, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = event.packageName
        }
        return last
    }

    private companion object {
        const val FOREGROUND_WINDOW_MS = 60_000L
    }
}

package dev.foxlauncher

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.gestures.system.AccessibilityRepair
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.wallpaper.source.SourceScanner
import dev.foxlauncher.feature.wallpaper.source.WallpaperSources
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Application entry point: Hilt root, WorkManager factory and the Coil singleton loader.
 *
 * Coil is for UI images only (icons, wallpaper thumbnails). The live wallpaper engine decodes its
 * own bitmaps at surface size and never goes through this loader.
 */
@HiltAndroidApp
class FoxApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var accessibilityRepair: AccessibilityRepair

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    @Inject lateinit var wallpaperSources: WallpaperSources

    @Inject lateinit var rotation: RotationScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        FoxLog.i("FoxApp started")
        // A new process may be one the system killed with Fox's accessibility service in it, which then
        // stays stopped (PLATFORM-NOTES G). Give an ordinary rebind its moment first.
        scope.launch {
            delay(ACCESSIBILITY_REPAIR_DELAY_MS)
            accessibilityRepair.repairIfStopped()
        }
        // Wallpaper rotation works out of the box (task 4.1): the built-ins exist, folders rescan daily.
        scope.launch {
            wallpaperSources.ensureBuiltIn()
            SourceScanner.scheduleDaily(WorkManager.getInstance(this@FoxApp))
            rotation.syncSchedule()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE_DIR))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        /** A launcher is a background process most of the time; keep the icon cache modest. */
        const val MEMORY_CACHE_FRACTION = 0.15
        const val DISK_CACHE_BYTES = 64L * 1024 * 1024
        const val IMAGE_CACHE_DIR = "image_cache"
        const val ACCESSIBILITY_REPAIR_DELAY_MS = 2_000L
    }
}

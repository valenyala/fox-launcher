package dev.foxlauncher.feature.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.wallpaper.engine.WallpaperLoader
import dev.foxlauncher.feature.wallpaper.source.WallpaperSources
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The non-seamless fallback (ADR-3, task 4.5): while Fox's live wallpaper is not the system's, a
 * change of wallpaper is applied as a static image, decoded at the screen's size and handed over as
 * a JPEG stream (PLATFORM-NOTES W: never let the system server decode a full-size image). The lock
 * screen, when the user asks for it, gets the same image the same way (task 4.6).
 */
class StaticWallpaperApplier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WallpaperDao,
    private val loader: WallpaperLoader,
    private val sources: WallpaperSources,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Sets entry [entryId] as the static wallpaper of [targets], in one write; true when it was set. */
    suspend fun apply(entryId: Long, targets: Set<StaticTarget>): Boolean = withContext(io) {
        if (targets.isEmpty()) return@withContext false
        val entry = dao.getEntry(entryId) ?: return@withContext false
        val (width, height) = screenSize()
        val bitmap = loader.load(entry.uri.toUri(), width, height)
        if (bitmap == null) {
            sources.reportUnreadable(entryId)
            return@withContext false
        }
        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        bitmap.recycle()
        try {
            WallpaperManager.getInstance(context).setStream(ByteArrayInputStream(jpeg), null, true, flags(targets))
            FoxLog.d("Static wallpaper set: entry $entryId (${width}x$height) on $targets")
            true
        } catch (e: IOException) {
            FoxLog.w("Static wallpaper not set", e)
            false
        }
    }

    private fun flags(targets: Set<StaticTarget>): Int = targets.fold(0) { flags, target ->
        flags or when (target) {
            StaticTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            StaticTarget.LOCK -> WallpaperManager.FLAG_LOCK
        }
    }

    /** The whole display, system bars included: the wallpaper sits under them. */
    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private companion object {
        const val JPEG_QUALITY = 92
    }
}

/**
 * Sets the active entry as a still image off the caller's coroutine; rapid changes keep only the
 * latest. What it sets is decided when it runs: the live wallpaper or the lock-screen setting may
 * have changed meanwhile.
 */
@HiltWorker
class StaticApplyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val applier: StaticWallpaperApplier,
    private val settings: FoxSettings,
    private val dao: WallpaperDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val targets = StaticTargets.of(
            foxLiveActive = LiveWallpaper.isFoxActive(applicationContext),
            changeLockScreen = settings.changeLockScreen.first(),
            lockOnly = inputData.getBoolean(KEY_LOCK_ONLY, false),
        )
        if (targets.isEmpty()) return Result.success()
        // The same entry the live wallpaper shows, so the lock screen matches the home screen.
        val entryId = WallpaperPlaylist.showing(settings.activeWallpaperEntryId.first(), dao.observeRotationEntries().first().map { it.id })
        if (entryId != null) applier.apply(entryId, targets)
        return Result.success()
    }

    companion object {
        private const val KEY_LOCK_ONLY = "lock_only"
        private const val WORK = "wallpaper_static_apply"

        /** After a change of wallpaper: replaces any pending run, which would set an older image. */
        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<StaticApplyWorker>().build())
        }

        /**
         * The lock screen catches up with the home screen (the setting was just turned on). A pending
         * run is kept: it reads the setting when it runs, so it covers the lock screen too.
         */
        fun enqueueLockOnly(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<StaticApplyWorker>().setInputData(workDataOf(KEY_LOCK_ONLY to true)).build()
            workManager.enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
        }
    }
}

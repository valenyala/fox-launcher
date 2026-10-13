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
import dev.foxlauncher.feature.wallpaper.engine.WallpaperLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The non-seamless fallback (ADR-3, task 4.5): while Fox's live wallpaper is not the system's, a
 * change of wallpaper is applied as a static image, decoded at the screen's size and handed over as
 * a JPEG stream (PLATFORM-NOTES W: never let the system server decode a full-size image).
 */
class StaticWallpaperApplier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WallpaperDao,
    private val loader: WallpaperLoader,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Sets entry [entryId] as the home screen's static wallpaper; true when it was set. */
    suspend fun apply(entryId: Long): Boolean = withContext(io) {
        val entry = dao.getEntry(entryId) ?: return@withContext false
        val (width, height) = screenSize()
        val bitmap = loader.load(entry.uri.toUri(), width, height)
        if (bitmap == null) {
            dao.markFailed(entryId)
            return@withContext false
        }
        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        bitmap.recycle()
        try {
            WallpaperManager.getInstance(context).setStream(ByteArrayInputStream(jpeg), null, true, WallpaperManager.FLAG_SYSTEM)
            FoxLog.d("Static wallpaper set: entry $entryId (${width}x$height)")
            true
        } catch (e: IOException) {
            FoxLog.w("Static wallpaper not set", e)
            false
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

/** Applies the static fallback off the caller's coroutine; rapid changes keep only the latest. */
@HiltWorker
class StaticApplyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val applier: StaticWallpaperApplier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The live wallpaper may have been applied meanwhile: it draws the change itself.
        if (LiveWallpaper.isFoxActive(applicationContext)) return Result.success()
        val entryId = inputData.getLong(KEY_ENTRY_ID, NO_ENTRY)
        if (entryId != NO_ENTRY) applier.apply(entryId)
        return Result.success()
    }

    companion object {
        private const val KEY_ENTRY_ID = "entry_id"
        private const val NO_ENTRY = -1L
        private const val WORK = "wallpaper_static_apply"

        fun enqueue(workManager: WorkManager, entryId: Long) {
            val request = OneTimeWorkRequestBuilder<StaticApplyWorker>().setInputData(workDataOf(KEY_ENTRY_ID to entryId)).build()
            workManager.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

package dev.foxlauncher.feature.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.RotationTrigger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one way the wallpaper changes (CLAUDE.md, data flows): picks the next or previous entry from
 * the playlist (task 4.2) and writes the active entry, which the live wallpaper engine draws
 * (task 4.3). Gestures and "next now" ask directly; the interval, unlock and page-change triggers
 * (task 4.4) go through [advanceIfDue], which applies [TriggerPolicy].
 */
@Singleton
class RotationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: FoxSettings,
    private val dao: WallpaperDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) : WallpaperAdvancer {

    private val mutex = Mutex()

    override fun advance(forward: Boolean) {
        scope.launch { advanceNow(forward) }
    }

    /** Moves on if [trigger] is due under the user's triggers; returns the entry now active, or null. */
    suspend fun advanceIfDue(trigger: RotationTrigger): Long? {
        val due = TriggerPolicy.shouldAdvance(
            trigger,
            settings.rotationTriggers.first(),
            settings.wallpaperChangedAt.first(),
            System.currentTimeMillis(),
        )
        if (!due) {
            FoxLog.d("Wallpaper $trigger: not due")
            return null
        }
        return advanceNow(forward = true, reason = trigger)
    }

    /** Moves the rotation on (or back) and returns the entry now active; null when there is none. */
    suspend fun advanceNow(forward: Boolean = true, reason: RotationTrigger = RotationTrigger.MANUAL): Long? = mutex.withLock {
        // Entries that failed three times in a row are skipped (task 4.9).
        val entries = dao.observeEnabledEntries().first().filter { it.failedCount < MAX_FAILURES }.map { it.id }
        val state = settings.playlistState.first()
        // Before the first change the engine shows the active entry or, with none, the first one: that is
        // the current wallpaper, so the first "next" must move on from it.
        val history = state.history.ifEmpty { listOfNotNull(settings.activeWallpaperEntryId.first() ?: entries.firstOrNull()) }
        val playlist = WallpaperPlaylist(entries, state.mode, state.seed ?: Random.nextLong(), history)
        val (id, after) = if (forward) playlist.next() else playlist.previous()
        if (id == null) return null
        val now = System.currentTimeMillis()
        settings.savePlaylist(after.seed, after.history)
        settings.setActiveWallpaper(id, changedAt = now)
        dao.markShown(id, now)
        FoxLog.d("Wallpaper ${if (forward) "next" else "previous"} ($reason): entry $id")
        return id
    }

    /**
     * Keeps the periodic work in step with the interval setting: WorkManager for 15 minutes and
     * more; shorter intervals run on the live wallpaper's own timer while it is visible.
     */
    suspend fun syncSchedule() {
        val minutes = settings.rotationTriggers.first().intervalMinutes
        val workManager = WorkManager.getInstance(context)
        if (minutes >= TriggerPolicy.WORK_MANAGER_MIN_MINUTES) {
            val request = PeriodicWorkRequestBuilder<RotationWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(INTERVAL_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            workManager.cancelUniqueWork(INTERVAL_WORK)
        }
    }

    private companion object {
        const val MAX_FAILURES = 3
        const val INTERVAL_WORK = "wallpaper_rotation_interval"
    }
}

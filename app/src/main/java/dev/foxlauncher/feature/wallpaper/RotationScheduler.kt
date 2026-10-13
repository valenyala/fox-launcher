package dev.foxlauncher.feature.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
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
 * the playlist (task 4.2), or the one the user picked ([showNow], task 4.7), and writes the
 * active entry, which the live wallpaper engine draws (task 4.3). Gestures and "next now" ask directly; the interval, unlock and page-change triggers
 * (task 4.4) go through [advanceIfDue], which applies [TriggerPolicy]. While Fox's live wallpaper is
 * not applied, a change is also set as a static image (task 4.5), except from a gesture ([advance]);
 * so is the lock screen's, when the user asked for it (task 4.6).
 */
@Singleton
class RotationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: FoxSettings,
    private val dao: WallpaperDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) : WallpaperAdvancer {

    private val mutex = Mutex()

    override fun advance(forward: Boolean): Boolean {
        if (!LiveWallpaper.isFoxActive(context)) return false
        scope.launch { advanceNow(forward, RotationTrigger.GESTURE) }
        return true
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
        val playlist = playlist()
        val (id, after) = if (forward) playlist.next() else playlist.previous()
        if (id == null) return null
        show(id, after)
        FoxLog.d("Wallpaper ${if (forward) "next" else "previous"} ($reason): entry $id")
        return id
    }

    /** Shows entry [entryId], which the user picked; the rotation goes on from it. False when it cannot be shown. */
    suspend fun showNow(entryId: Long): Boolean = mutex.withLock {
        val after = playlist().jumpTo(entryId) ?: return false
        show(entryId, after)
        FoxLog.d("Wallpaper picked: entry $entryId")
        return true
    }

    /** The playlist as stored, over the entries that can be shown, at the entry on screen. */
    private suspend fun playlist(): WallpaperPlaylist {
        val enabled = dao.observeRotationEntries().first()
        // Entries that failed three times in a row are skipped (task 4.9).
        val entries = enabled.filter { it.failedCount < WallpaperEntryEntity.MAX_FAILURES }.map { it.id }
        val state = settings.playlistState.first()
        // What the engine shows: the active entry or, before the first change or once it went away, the first one.
        val onScreen = WallpaperPlaylist.showing(settings.activeWallpaperEntryId.first(), enabled.map { it.id })
        return WallpaperPlaylist(entries, state.mode, state.seed ?: Random.nextLong(), state.history).onScreen(onScreen)
    }

    private suspend fun show(id: Long, after: WallpaperPlaylist) {
        val now = System.currentTimeMillis()
        settings.savePlaylist(after.seed, after.history)
        settings.setActiveWallpaper(id, changedAt = now)
        dao.markShown(id, now)
        if (StaticTargets.of(LiveWallpaper.isFoxActive(context), settings.changeLockScreen.first()).isNotEmpty()) {
            StaticApplyWorker.enqueue(WorkManager.getInstance(context))
        }
    }

    /** Turns "also change the lock screen" on or off; on, the lock screen gets the current image at once. */
    suspend fun setChangeLockScreen(enabled: Boolean) {
        settings.setChangeLockScreen(enabled)
        if (enabled) StaticApplyWorker.enqueueLockOnly(WorkManager.getInstance(context))
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
        const val INTERVAL_WORK = "wallpaper_rotation_interval"
    }
}

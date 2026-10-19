package dev.foxlauncher.feature.wallpaper

import android.content.Context
import android.os.PowerManager
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
import dev.foxlauncher.core.model.WallpaperFraming
import dev.foxlauncher.core.model.WallpaperTarget
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
 * not applied, a change is also set as a static image (task 4.5), except from a gesture ([advance]).
 * Each image says where it shows, the home screen, the lock screen or both; the two screens rotate
 * over their own images. The lock screen is a still image, set with the home screen's when that is
 * one too, otherwise once the screen goes off ([catchUpLockScreen]).
 */
@Singleton
class RotationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: FoxSettings,
    private val dao: WallpaperDao,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val applier: StaticWallpaperApplier,
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

    /**
     * Moves the home screen's rotation and the lock screen's on (or back) and returns the entry now
     * on the home screen (the lock screen's when only it moved); null when there is none.
     */
    suspend fun advanceNow(forward: Boolean = true, reason: RotationTrigger = RotationTrigger.MANUAL): Long? = mutex.withLock {
        val entries = dao.observeRotationEntries().first()
        val home = homePlaylist(entries)
        val (homeId, homeAfter) = if (forward) home.next() else home.previous()
        val lock = lockPlaylist(entries, home.seed)
        val (lockId, lockAfter) = lock?.let { if (forward) it.next() else it.previous() } ?: (null to null)
        if (homeId == null && lockId == null) return null
        val now = System.currentTimeMillis()
        if (homeId != null) showOnHome(homeId, homeAfter, now)
        if (lockId != null && lockAfter != null) showOnLock(lockId, lockAfter, now)
        onChanged()
        FoxLog.d("Wallpaper ${if (forward) "next" else "previous"} ($reason): home $homeId, lock $lockId")
        return homeId ?: lockId
    }

    /**
     * Shows entry [entryId], which the user picked, on the screens it is aimed at; each rotation goes
     * on from it. False when it cannot be shown.
     */
    suspend fun showNow(entryId: Long): Boolean = mutex.withLock {
        val entries = dao.observeRotationEntries().first()
        val homeBefore = homePlaylist(entries)
        val home = homeBefore.jumpTo(entryId)
        val lock = lockPlaylist(entries, homeBefore.seed)?.jumpTo(entryId)
        if (home == null && lock == null) return false
        val now = System.currentTimeMillis()
        if (home != null) showOnHome(entryId, home, now)
        if (lock != null) showOnLock(entryId, lock, now)
        onChanged()
        FoxLog.d("Wallpaper picked: entry $entryId (home ${home != null}, lock ${lock != null})")
        return true
    }

    /** The home screen's rotation as stored, over its images that can be shown, at the entry on screen. */
    private suspend fun homePlaylist(entries: List<WallpaperEntryEntity>): WallpaperPlaylist {
        val ids = EntryTargets.home(entries.map { it.id to it.target })
        val state = settings.playlistState.first()
        // What the engine shows: the active entry or, before the first change or once it went away, the first one.
        val onScreen = WallpaperPlaylist.showing(settings.activeWallpaperEntryId.first(), ids)
        return WallpaperPlaylist(playable(entries, ids), state.mode, state.seed ?: Random.nextLong(), state.history).onScreen(onScreen)
    }

    /**
     * The lock screen's rotation, null when no image is aimed at it. Until it has a seed of its own it
     * shares [homeSeed], so over the same images the two walk alike and a "both" image shows on both.
     */
    private suspend fun lockPlaylist(entries: List<WallpaperEntryEntity>, homeSeed: Long): WallpaperPlaylist? {
        val ids = EntryTargets.lock(entries.map { it.id to it.target })
        if (ids.isEmpty()) return null
        val state = settings.lockPlaylistState.first()
        val onScreen = WallpaperPlaylist.showing(settings.lockActiveWallpaperEntryId.first(), ids)
        return WallpaperPlaylist(playable(entries, ids), state.mode, state.seed ?: homeSeed, state.history).onScreen(onScreen)
    }

    /** Entries that failed three times in a row are skipped (task 4.9). */
    private fun playable(entries: List<WallpaperEntryEntity>, ids: List<Long>): List<Long> {
        val failing = entries.filter { it.failedCount >= WallpaperEntryEntity.MAX_FAILURES }.map { it.id }.toSet()
        return ids.filter { it !in failing }
    }

    private suspend fun showOnHome(id: Long, after: WallpaperPlaylist, now: Long) {
        settings.savePlaylist(after.seed, after.history)
        settings.setActiveWallpaper(id, changedAt = now)
        dao.markShown(id, now)
    }

    private suspend fun showOnLock(id: Long, after: WallpaperPlaylist, now: Long) {
        settings.saveLockPlaylist(after.seed, after.history)
        settings.setLockActiveWallpaper(id, changedAt = now)
        dao.markShown(id, now)
    }

    /** Without the live wallpaper the home screen needs a still image now; the lock screen's waits for screen off. */
    private fun onChanged() {
        if (!LiveWallpaper.isFoxActive(context)) StaticApplyWorker.enqueue(WorkManager.getInstance(context))
    }

    private suspend fun homeShowing(entries: List<WallpaperEntryEntity>): Long? =
        WallpaperPlaylist.showing(settings.activeWallpaperEntryId.first(), EntryTargets.home(entries.map { it.id to it.target }))

    private suspend fun lockShowing(entries: List<WallpaperEntryEntity>): Long? =
        WallpaperPlaylist.showing(settings.lockActiveWallpaperEntryId.first(), EntryTargets.lock(entries.map { it.id to it.target }))

    /**
     * Keeps the part of entry [entryId] the user chose to show (ADR-26). The live wallpaper redraws
     * it if it is on screen (it watches the entries); a still image of it is set again, the home
     * screen's now and the lock screen's at the next screen off.
     */
    suspend fun setFraming(entryId: Long, framing: WallpaperFraming) {
        mutex.withLock {
            dao.setFraming(entryId, framing.focusX, framing.focusY, framing.zoom)
            if (settings.lockWallpaperEntryId.first() == entryId) settings.setLockWallpaperEntry(null)
            val entries = dao.observeRotationEntries().first()
            if (entryId == homeShowing(entries) && !LiveWallpaper.isFoxActive(context)) StaticApplyWorker.enqueue(WorkManager.getInstance(context))
        }
    }

    /** Aims entry [entryId] at the home screen, the lock screen or both. The lock screen catches up at its next screen off. */
    suspend fun setTarget(entryId: Long, target: WallpaperTarget) {
        mutex.withLock {
            dao.setTarget(entryId, target)
            // It may have left the lock screen's images, or the home screen's, or the lock screen has just got its first.
            if (!LiveWallpaper.isFoxActive(context)) StaticApplyWorker.enqueue(WorkManager.getInstance(context))
        }
    }

    /** Users of the retired "also change the lock screen" switch keep that: their images go on both screens. Once. */
    suspend fun migrateLegacyLockSwitch() {
        if (settings.takeLegacyLockSwitch()) dao.setAllTargets(WallpaperTarget.BOTH)
    }

    /**
     * Without Fox's live wallpaper: sets the still images the system shows, the home screen's now and
     * the lock screen's too if it is behind (one write when both show the same image). With it, the
     * home screen draws itself and the lock screen waits for [catchUpLockScreen].
     */
    suspend fun applyStatic() {
        if (LiveWallpaper.isFoxActive(context)) return
        mutex.withLock {
            val entries = dao.observeRotationEntries().first()
            val homeId = homeShowing(entries)
            val lockId = lockShowing(entries)?.takeIf { StaticTargets.lockScreenBehind(settings.lockWallpaperEntryId.first(), it) }
            if (homeId != null && homeId == lockId) {
                if (applier.apply(homeId, setOf(StaticTarget.HOME, StaticTarget.LOCK))) settings.setLockWallpaperEntry(homeId)
                return
            }
            if (homeId != null) applier.apply(homeId, setOf(StaticTarget.HOME))
            if (lockId != null && applier.apply(lockId, setOf(StaticTarget.LOCK))) settings.setLockWallpaperEntry(lockId)
        }
    }

    /**
     * With the screen off: the lock screen gets its image, if it is behind, and only while the screen
     * stays off. A still image set in front of the user restyles every app at once (PLATFORM-NOTES W);
     * the next screen off tries again. Called when the live wallpaper goes out of sight with the screen
     * off, and on `ACTION_SCREEN_OFF`, which MIUI delivers one to three seconds late.
     */
    suspend fun catchUpLockScreen() {
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        mutex.withLock {
            val lockId = lockShowing(dao.observeRotationEntries().first()) ?: return
            if (!StaticTargets.lockScreenBehind(settings.lockWallpaperEntryId.first(), lockId)) return
            if (applier.apply(lockId, setOf(StaticTarget.LOCK)) { !power.isInteractive }) settings.setLockWallpaperEntry(lockId)
        }
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

package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.settings.FoxSettings
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
 * the playlist (task 4.2) and writes `activeWallpaperEntryId`, which the live wallpaper engine
 * draws (task 4.3). The gesture actions call it now; the interval and unlock triggers arrive in 4.4.
 */
@Singleton
class RotationScheduler @Inject constructor(
    private val settings: FoxSettings,
    private val dao: WallpaperDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) : WallpaperAdvancer {

    private val mutex = Mutex()

    override fun advance(forward: Boolean) {
        scope.launch { advanceNow(forward) }
    }

    /** Moves the rotation on (or back) and returns the entry now active; null when there is none. */
    suspend fun advanceNow(forward: Boolean = true): Long? = mutex.withLock {
        // Entries that failed three times in a row are skipped (task 4.9).
        val entries = dao.observeEnabledEntries().first().filter { it.failedCount < MAX_FAILURES }.map { it.id }
        val state = settings.playlistState.first()
        // Before the first change the engine shows the active entry or, with none, the first one: that is
        // the current wallpaper, so the first "next" must move on from it.
        val history = state.history.ifEmpty { listOfNotNull(settings.activeWallpaperEntryId.first() ?: entries.firstOrNull()) }
        val playlist = WallpaperPlaylist(entries, state.mode, state.seed ?: Random.nextLong(), history)
        val (id, after) = if (forward) playlist.next() else playlist.previous()
        if (id == null) return null
        settings.savePlaylist(after.seed, after.history)
        settings.setActiveWallpaperEntryId(id)
        dao.markShown(id, System.currentTimeMillis())
        FoxLog.d("Wallpaper ${if (forward) "next" else "previous"}: entry $id")
        return id
    }

    private companion object {
        const val MAX_FAILURES = 3
    }
}

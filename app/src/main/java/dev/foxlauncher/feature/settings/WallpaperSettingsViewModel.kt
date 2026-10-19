package dev.foxlauncher.feature.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import dev.foxlauncher.core.data.db.framing
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.PlaylistMode
import dev.foxlauncher.core.model.RotationTriggers
import dev.foxlauncher.core.model.WallpaperFraming
import dev.foxlauncher.core.model.WallpaperTarget
import dev.foxlauncher.feature.wallpaper.EntryTargets
import dev.foxlauncher.feature.wallpaper.LiveWallpaper
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.wallpaper.WallpaperPlaylist
import dev.foxlauncher.feature.wallpaper.source.WallpaperSources
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A wallpaper source as the settings list it (task 4.7). */
@Immutable
data class WallpaperSourceUi(
    val id: Long,
    val type: WallpaperSourceType,
    val name: String,
    val enabled: Boolean,
    /** The folder's access is gone: it must be picked again. */
    val needsReauth: Boolean,
    val imageCount: Int,
)

/** One wallpaper in the thumbnail grid (task 4.7). */
@Immutable
data class WallpaperEntryUi(
    val id: Long,
    /** What the thumbnail loads: the image's URI, or a resource id for a built-in one. */
    val thumbnail: Any,
    val name: String,
    /** It failed to load too often in a row: the rotation skips it. */
    val failing: Boolean,
    /** The part of it that shows (ADR-26). */
    val framing: WallpaperFraming = WallpaperFraming.CENTERED,
    /** Where it shows: the home screen, the lock screen or both. */
    val target: WallpaperTarget = WallpaperTarget.BOTH,
)

@Immutable
data class WallpaperSettingsUiState(
    /** Fox's live wallpaper is the system wallpaper, so changes crossfade. */
    val liveWallpaperActive: Boolean = true,
    /** The lock screen shows it too, so its pictures change into the home screen's smoothly at the unlock. */
    val liveWallpaperOnLock: Boolean = true,
    val triggers: RotationTriggers = RotationTriggers(),
    val shuffle: Boolean = false,
    val crossfadeMs: Int = FoxSettings.DEFAULT_CROSSFADE_MS,
    val sources: List<WallpaperSourceUi> = emptyList(),
    /** The wallpapers of the enabled sources, in rotation order. */
    val entries: List<WallpaperEntryUi> = emptyList(),
    /** The one on the home screen now. */
    val showingId: Long? = null,
    /** The one the lock screen is to show; null when no image is aimed at it. */
    val lockShowingId: Long? = null,
)

/** Everything about the wallpaper (tasks 4.4–4.7): the live wallpaper, the rotation, sources and pictures. */
@HiltViewModel
class WallpaperSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: FoxSettings,
    private val rotation: RotationScheduler,
    private val sources: WallpaperSources,
) : ViewModel() {

    private val liveWallpaperActive = MutableStateFlow(isLiveWallpaperActive())
    private val liveWallpaperOnLock = MutableStateFlow(LiveWallpaper.isFoxOnLock(context))

    private val rotationSettings = combine(
        combine(liveWallpaperActive, liveWallpaperOnLock, ::Pair),
        settings.rotationTriggers,
        settings.playlistState,
        settings.crossfadeMs,
    ) { (active, onLock), triggers, playlist, crossfade ->
        WallpaperSettingsUiState(
            liveWallpaperActive = active,
            liveWallpaperOnLock = onLock,
            triggers = triggers,
            shuffle = playlist.mode == PlaylistMode.SHUFFLE,
            crossfadeMs = crossfade,
        )
    }

    val uiState: StateFlow<WallpaperSettingsUiState> = combine(
        rotationSettings,
        combine(sources.sources, sources.entryCounts) { list, counts ->
            val countOf = counts.associate { it.sourceId to it.count }
            list.map { WallpaperSourceUi(it.id, it.type, it.displayName, it.enabled, it.needsReauth, countOf[it.id] ?: 0) }
        },
        sources.rotationEntries,
        combine(settings.activeWallpaperEntryId, settings.lockActiveWallpaperEntryId, ::Pair),
    ) { base, sourceList, entries, (active, lockActive) ->
        val targets = entries.map { it.id to it.target }
        base.copy(
            sources = sourceList,
            entries = entries.map {
                WallpaperEntryUi(it.id, sources.thumbnailModel(it.uri), it.displayName, it.failedCount >= WallpaperEntryEntity.MAX_FAILURES, it.framing, it.target)
            },
            showingId = WallpaperPlaylist.showing(active, EntryTargets.home(targets)),
            lockShowingId = WallpaperPlaylist.showing(lockActive, EntryTargets.lock(targets)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WallpaperSettingsUiState())

    /** Coming back from the system's wallpaper preview: the live wallpaper may have been applied. */
    fun refresh() {
        liveWallpaperActive.value = isLiveWallpaperActive()
        liveWallpaperOnLock.value = LiveWallpaper.isFoxOnLock(context)
    }

    /** "Next now" is an explicit request: without the live wallpaper it is set as a static image (task 4.5). */
    fun nextNow() {
        viewModelScope.launch { rotation.advanceNow() }
    }

    /** A tapped thumbnail: shown now, the same way as "Next now". */
    fun show(entryId: Long) {
        viewModelScope.launch { rotation.showNow(entryId) }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            settings.setRotationInterval(minutes)
            rotation.syncSchedule()
        }
    }

    fun setOnUnlock(enabled: Boolean) {
        viewModelScope.launch { settings.setRotateOnUnlock(enabled) }
    }

    fun setOnPageChange(enabled: Boolean) {
        viewModelScope.launch { settings.setRotateOnPageChange(enabled) }
    }

    /** Aims a picture at the home screen, the lock screen or both. */
    fun setTarget(entryId: Long, target: WallpaperTarget) {
        viewModelScope.launch { rotation.setTarget(entryId, target) }
    }

    fun setShuffle(enabled: Boolean) {
        viewModelScope.launch { settings.setPlaylistMode(if (enabled) PlaylistMode.SHUFFLE else PlaylistMode.SEQUENTIAL) }
    }

    fun setCrossfadeMs(ms: Int) {
        viewModelScope.launch { settings.setCrossfadeMs(ms) }
    }

    /** The part of a picture to show, chosen in the framing editor (ADR-26). */
    fun setFraming(entryId: Long, framing: WallpaperFraming) {
        viewModelScope.launch { rotation.setFraming(entryId, framing) }
    }

    fun addFolder(tree: Uri) {
        viewModelScope.launch { if (!sources.addFolder(tree)) FoxLog.w("Folder $tree not added") }
    }

    fun reauthorize(sourceId: Long, tree: Uri) {
        viewModelScope.launch { if (!sources.reauthorize(sourceId, tree)) FoxLog.w("Folder $tree not re-authorized") }
    }

    fun addPictures(uris: List<Uri>) {
        viewModelScope.launch { FoxLog.d("Wallpaper pictures added: ${sources.addPictures(uris)} of ${uris.size}") }
    }

    fun setSourceEnabled(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch { sources.setEnabled(sourceId, enabled) }
    }

    fun removeSource(sourceId: Long) {
        viewModelScope.launch { sources.remove(sourceId) }
    }

    private fun isLiveWallpaperActive(): Boolean = LiveWallpaper.isFoxActive(context)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

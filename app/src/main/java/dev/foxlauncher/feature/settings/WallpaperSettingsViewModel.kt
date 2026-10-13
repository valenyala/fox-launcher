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
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.PlaylistMode
import dev.foxlauncher.core.model.RotationTriggers
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
)

@Immutable
data class WallpaperSettingsUiState(
    /** Fox's live wallpaper is the system wallpaper, so changes crossfade. */
    val liveWallpaperActive: Boolean = true,
    val triggers: RotationTriggers = RotationTriggers(),
    /** Each new wallpaper is also set on the lock screen (task 4.6). */
    val changeLockScreen: Boolean = false,
    val shuffle: Boolean = false,
    val crossfadeMs: Int = FoxSettings.DEFAULT_CROSSFADE_MS,
    val parallax: Boolean = true,
    val sources: List<WallpaperSourceUi> = emptyList(),
    /** The wallpapers of the enabled sources, in rotation order. */
    val entries: List<WallpaperEntryUi> = emptyList(),
    /** The one on screen now. */
    val showingId: Long? = null,
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

    private val rotationSettings = combine(
        liveWallpaperActive,
        settings.rotationTriggers,
        settings.changeLockScreen,
        settings.playlistState,
        settings.crossfadeMs,
    ) { active, triggers, lock, playlist, crossfade ->
        WallpaperSettingsUiState(
            liveWallpaperActive = active,
            triggers = triggers,
            changeLockScreen = lock,
            shuffle = playlist.mode == PlaylistMode.SHUFFLE,
            crossfadeMs = crossfade,
        )
    }

    val uiState: StateFlow<WallpaperSettingsUiState> = combine(
        rotationSettings,
        settings.wallpaperParallax,
        combine(sources.sources, sources.entryCounts) { list, counts ->
            val countOf = counts.associate { it.sourceId to it.count }
            list.map { WallpaperSourceUi(it.id, it.type, it.displayName, it.enabled, it.needsReauth, countOf[it.id] ?: 0) }
        },
        sources.enabledEntries,
        settings.activeWallpaperEntryId,
    ) { base, parallax, sourceList, entries, active ->
        base.copy(
            parallax = parallax,
            sources = sourceList,
            entries = entries.map {
                WallpaperEntryUi(it.id, sources.thumbnailModel(it.uri), it.displayName, it.failedCount >= WallpaperEntryEntity.MAX_FAILURES)
            },
            showingId = WallpaperPlaylist.showing(active, entries.map { it.id }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WallpaperSettingsUiState())

    /** Coming back from the system's wallpaper preview: the live wallpaper may have been applied. */
    fun refresh() {
        liveWallpaperActive.value = isLiveWallpaperActive()
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

    fun setChangeLockScreen(enabled: Boolean) {
        viewModelScope.launch { rotation.setChangeLockScreen(enabled) }
    }

    fun setShuffle(enabled: Boolean) {
        viewModelScope.launch { settings.setPlaylistMode(if (enabled) PlaylistMode.SHUFFLE else PlaylistMode.SEQUENTIAL) }
    }

    fun setCrossfadeMs(ms: Int) {
        viewModelScope.launch { settings.setCrossfadeMs(ms) }
    }

    fun setParallax(enabled: Boolean) {
        viewModelScope.launch { settings.setWallpaperParallax(enabled) }
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

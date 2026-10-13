package dev.foxlauncher.feature.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.RotationTriggers
import dev.foxlauncher.feature.wallpaper.RotationScheduler
import dev.foxlauncher.feature.wallpaper.LiveWallpaper
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class WallpaperSettingsUiState(
    /** Fox's live wallpaper is the system wallpaper, so changes crossfade. */
    val liveWallpaperActive: Boolean = true,
    val triggers: RotationTriggers = RotationTriggers(),
)

/** The wallpaper settings (task 4.4 brings the triggers; task 4.7 adds sources and the rest). */
@HiltViewModel
class WallpaperSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: FoxSettings,
    private val rotation: RotationScheduler,
) : ViewModel() {

    private val liveWallpaperActive = MutableStateFlow(isLiveWallpaperActive())

    val uiState: StateFlow<WallpaperSettingsUiState> =
        combine(liveWallpaperActive, settings.rotationTriggers) { active, triggers -> WallpaperSettingsUiState(active, triggers) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WallpaperSettingsUiState())

    /** Coming back from the system's wallpaper preview: the live wallpaper may have been applied. */
    fun refresh() {
        liveWallpaperActive.value = isLiveWallpaperActive()
    }

    /** "Next now" is an explicit request: without the live wallpaper it is set as a static image (task 4.5). */
    fun nextNow() {
        viewModelScope.launch { rotation.advanceNow() }
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

    private fun isLiveWallpaperActive(): Boolean = LiveWallpaper.isFoxActive(context)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

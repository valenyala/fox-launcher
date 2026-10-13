package dev.foxlauncher.feature.memory

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.model.AppEntry
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * Feeds every memory widget on the home (task 5.3). One reading every [POLL_MS] is shared by all of
 * them, and only while one is composed and the home is started: when none collects, reading stops
 * after [STOP_MS]. The sparkline's history lives only while reading does.
 */
@HiltViewModel
class MemoryWidgetViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    stats: MemoryStats,
    private val booster: MemoryBooster,
    private val recentApps: RecentAppsSource,
    private val appRepository: AppRepository,
) : ViewModel() {

    private val readings: SharedFlow<MemorySnapshot> =
        stats.observe(POLL_MS).shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), replay = 1)

    val snapshot: StateFlow<MemorySnapshot?> = readings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** Used memory as a fraction, oldest first, the last [HISTORY_SIZE] readings. */
    val history: StateFlow<List<Float>> = readings
        .scan(emptyList<Float>()) { samples, reading -> (samples + reading.usedFraction).takeLast(HISTORY_SIZE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), emptyList())

    val recent: StateFlow<RecentApps?> = flow {
        while (true) {
            emit(recentApps.read(RECENT_LIMIT, RECENT_WINDOW_MS))
            delay(RECENT_REFRESH_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val canCloseOtherApps: Boolean get() = booster.canCloseOtherApps

    suspend fun boost(): BoostResult = booster.boost()

    /** App info, where the user can force-stop it: Fox does not force-stop apps itself (ADR-5). */
    fun openAppInfo(app: AppEntry, bounds: Rect?) = appRepository.openAppInfo(app, bounds)

    fun openUsageAccess() {
        try {
            context.startActivity(recentApps.accessSettings())
        } catch (e: ActivityNotFoundException) {
            FoxLog.w("No usage access settings", e)
        }
    }

    companion object {
        const val POLL_MS = 2_000L
        const val STOP_MS = 5_000L
        const val HISTORY_SIZE = 60
        const val RECENT_LIMIT = 3
        const val RECENT_WINDOW_MS = 60 * 60 * 1000L
        const val RECENT_REFRESH_MS = 30_000L
    }
}

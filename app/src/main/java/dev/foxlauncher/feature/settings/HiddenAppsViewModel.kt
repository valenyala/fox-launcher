package dev.foxlauncher.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HiddenAppRow(val entry: AppEntry, val isHidden: Boolean)

@Immutable
data class HiddenAppsUiState(val rows: ImmutableList<HiddenAppRow> = persistentListOf())

@HiltViewModel
class HiddenAppsViewModel @Inject constructor(
    appRepository: AppRepository,
    private val settings: FoxSettings,
) : ViewModel() {

    val uiState: StateFlow<HiddenAppsUiState> =
        combine(appRepository.apps, settings.hiddenApps) { apps, hidden ->
            HiddenAppsUiState(apps.map { HiddenAppRow(it, it.key in hidden) }.toImmutableList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HiddenAppsUiState())

    fun onHiddenChange(entry: AppEntry, hidden: Boolean) {
        viewModelScope.launch { settings.setHidden(entry.key, hidden) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package dev.foxlauncher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.settings.FoxSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the home screen needs to render. Grows with every phase. */
data class HomeUiState(
    val isFirstRun: Boolean = FoxSettings.DEFAULT_IS_FIRST_RUN,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: FoxSettings,
    appRepository: AppRepository,
) : ViewModel() {

    init {
        // TODO(1.4): temporary proof that the catalog loads; remove when the drawer lands.
        viewModelScope.launch {
            appRepository.isLoaded.first { it }
            FoxLog.i("App catalog loaded: ${appRepository.apps.value.size} apps")
        }
    }

    val uiState: StateFlow<HomeUiState> = settings.isFirstRun
        .map(::HomeUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(),
        )

    /** Called once Fox is actually holding the home role; Phase 2 hangs the default layout here. */
    fun onLauncherReady() {
        if (!uiState.value.isFirstRun) return
        viewModelScope.launch { settings.setFirstRun(false) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

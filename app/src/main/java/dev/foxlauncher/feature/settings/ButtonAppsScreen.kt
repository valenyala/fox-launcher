package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.R
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

/** The apps Fox opens with the navigation buttons (task 9.7): every app, checked when listed. */
@HiltViewModel
class ButtonAppsViewModel @Inject constructor(
    appRepository: AppRepository,
    private val settings: FoxSettings,
) : ViewModel() {

    val rows: StateFlow<ImmutableList<AppCheckRow>> =
        combine(appRepository.apps, settings.buttonApps) { apps, listed ->
            apps.map { AppCheckRow(it, it.key in listed) }.toImmutableList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), persistentListOf())

    fun onCheckedChange(entry: AppEntry, withButtons: Boolean) {
        viewModelScope.launch { settings.setButtonApp(entry.key, withButtons) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ButtonAppsScreen(viewModel: ButtonAppsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    AppChecklistContent(
        title = stringResource(R.string.button_apps_title),
        explainer = stringResource(R.string.button_apps_explainer),
        rows = rows,
        onCheckedChange = viewModel::onCheckedChange,
        onBack = onBack,
        modifier = modifier,
    )
}

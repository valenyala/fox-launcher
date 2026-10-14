package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.R
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.automation.AutomationCommand
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object AutomationTestTags {
    const val SWITCH = "automation_switch"
}

/** Commands from other apps (task 9.4). */
@HiltViewModel
class AutomationViewModel @Inject constructor(private val settings: FoxSettings) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.automation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), false)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutomation(enabled) }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

@Composable
fun AutomationScreen(viewModel: AutomationViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    AutomationContent(enabled, onEnabledChange = viewModel::setEnabled, onBack = onBack, modifier = modifier)
}

/**
 * Commands from other apps (task 9.4, ADR-22): what it allows, the switch (off by default), and
 * each command's broadcast action, selectable for copying into Tasker, with an adb example.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationContent(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.automation_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.automation_switch)) },
                // The row handles the toggle, so the switch itself is display-only.
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                modifier = Modifier
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .testTag(AutomationTestTags.SWITCH),
            )
            SelectionContainer {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AutomationCommand.entries.forEach { command ->
                        Text(stringResource(command.labelRes()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                        Text(command.action, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    Text(stringResource(R.string.automation_how), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        stringResource(R.string.automation_adb_example),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun AutomationCommand.labelRes(): Int = when (this) {
    AutomationCommand.NEXT_WALLPAPER -> R.string.action_next_wallpaper
    AutomationCommand.PREV_WALLPAPER -> R.string.action_previous_wallpaper
    AutomationCommand.BOOST -> R.string.memory_boost_action
}

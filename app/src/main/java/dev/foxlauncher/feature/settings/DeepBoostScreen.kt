package dev.foxlauncher.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.memory.DeepBoost
import dev.foxlauncher.feature.memory.DeepBoostState
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import dev.foxlauncher.feature.shizuku.ShizukuStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object DeepBoostTestTags {
    const val SWITCH = "deep_boost_switch"
    const val STATUS = "deep_boost_status"
    const val ACTION = "deep_boost_action"
}

/** The deep boost's settings (task 9.1). */
@HiltViewModel
class DeepBoostViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val deepBoost: DeepBoost,
    private val shizuku: ShizukuAccess,
) : ViewModel() {

    val state: StateFlow<DeepBoostState> = deepBoost.state

    /** On every return to the screen: the user may have installed, started or allowed Shizuku meanwhile. */
    fun refresh() = shizuku.refresh()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { deepBoost.setEnabled(enabled) }
    }

    fun requestPermission() = shizuku.requestPermission()

    fun openShizuku() {
        viewModelScope.launch {
            try {
                context.startActivity(shizuku.shizukuAppIntent())
            } catch (e: ActivityNotFoundException) {
                FoxLog.w("Neither Shizuku nor a store to get it", e)
            }
        }
    }
}

@Composable
fun DeepBoostScreen(viewModel: DeepBoostViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    DeepBoostContent(
        state = state,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onRequestPermission = viewModel::requestPermission,
        onOpenShizuku = viewModel::openShizuku,
        modifier = modifier,
    )
}

/**
 * Deep boost (task 9.1, ADR-9, ADR-21): what it does and costs, the switch (off until the user turns
 * it on), and where Shizuku stands with the one step it needs next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepBoostContent(
    state: DeepBoostState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deep_boost_title)) },
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
                stringResource(R.string.deep_boost_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.deep_boost_switch)) },
                // The row handles the toggle, so the switch itself is display-only.
                trailingContent = { Switch(checked = state.enabled, onCheckedChange = null) },
                modifier = Modifier
                    .toggleable(value = state.enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .testTag(DeepBoostTestTags.SWITCH),
            )
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(statusText(state.status)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(DeepBoostTestTags.STATUS),
                    )
                    if (state.enabled && !state.active) {
                        Text(
                            stringResource(R.string.deep_boost_fallback),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    val action = when (state.status) {
                        ShizukuStatus.NOT_INSTALLED -> R.string.deep_boost_get to onOpenShizuku
                        ShizukuStatus.NEEDS_PERMISSION -> R.string.deep_boost_allow to onRequestPermission
                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.TOO_OLD, ShizukuStatus.DENIED -> R.string.deep_boost_open to onOpenShizuku
                        ShizukuStatus.READY -> null
                    }
                    if (action != null) {
                        FilledTonalButton(
                            onClick = action.second,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .testTag(DeepBoostTestTags.ACTION),
                        ) { Text(stringResource(action.first)) }
                    }
                }
            }
        }
    }
}

private fun statusText(status: ShizukuStatus): Int = when (status) {
    ShizukuStatus.NOT_INSTALLED -> R.string.deep_boost_status_not_installed
    ShizukuStatus.NOT_RUNNING -> R.string.deep_boost_status_not_running
    ShizukuStatus.TOO_OLD -> R.string.deep_boost_status_too_old
    ShizukuStatus.NEEDS_PERMISSION -> R.string.deep_boost_status_needs_permission
    ShizukuStatus.DENIED -> R.string.deep_boost_status_denied
    ShizukuStatus.READY -> R.string.deep_boost_status_ready
}

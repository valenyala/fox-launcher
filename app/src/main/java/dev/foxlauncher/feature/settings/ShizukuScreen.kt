package dev.foxlauncher.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
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
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.gestures.navigation.ButtonAppLaunches
import dev.foxlauncher.feature.gestures.navigation.NavigationButtons
import dev.foxlauncher.feature.memory.DeepBoost
import dev.foxlauncher.feature.memory.DeepBoostState
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import dev.foxlauncher.feature.shizuku.ShizukuStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ShizukuTestTags {
    const val DEEP_BOOST_SWITCH = "deep_boost_switch"
    const val STATUS = "shizuku_status"
    const val ACTION = "shizuku_action"
    const val NAVIGATION_SWITCH = "navigation_buttons_switch"
    const val BUTTON_APPS = "button_apps"
}

/**
 * The navigation buttons' row (task 9.6): [hidden] as last read from the system (null when this
 * phone does not let Fox read it), [direct] when Fox changes it itself, without Shizuku (MIUI with
 * the adb grant, ADR-25), [busy] while it changes, [failed] after it could not.
 */
data class NavigationButtonsState(
    val hidden: Boolean? = null,
    val direct: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

/** What Fox does through Shizuku (tasks 9.1, 9.6, 9.7): the deep boost and hiding the navigation buttons. */
@HiltViewModel
class ShizukuViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val boost: DeepBoost,
    private val shizuku: ShizukuAccess,
    private val navigationButtons: NavigationButtons,
    private val buttonApps: ButtonAppLaunches,
    appRepository: AppRepository,
    settings: FoxSettings,
) : ViewModel() {

    val deepBoost: StateFlow<DeepBoostState> = boost.state

    /** How many installed apps open with the buttons (task 9.7); uninstalled ones still listed do not count. */
    val buttonAppsCount: StateFlow<Int> = combine(appRepository.apps, settings.buttonApps) { apps, listed ->
        apps.count { it.key in listed }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _navigation = MutableStateFlow(NavigationButtonsState())
    val navigation: StateFlow<NavigationButtonsState> = _navigation.asStateFlow()

    /**
     * On every return to the screen: the user may have installed, started or allowed Shizuku
     * meanwhile, or changed the navigation in the system's settings.
     */
    fun refresh() {
        shizuku.refresh()
        viewModelScope.launch {
            val hidden = navigationButtons.isHidden()
            _navigation.update { it.copy(hidden = hidden, direct = navigationButtons.writesDirectly) }
        }
    }

    fun setDeepBoost(enabled: Boolean) {
        viewModelScope.launch { boost.setEnabled(enabled) }
    }

    fun setNavigationButtonsHidden(hidden: Boolean) {
        if (_navigation.value.busy) return
        _navigation.update { it.copy(busy = true, failed = false) }
        // The user's own choice: home no longer hides buttons Fox showed for an app (task 9.7).
        buttonApps.forget()
        viewModelScope.launch {
            val done = navigationButtons.setHidden(hidden)
            // Android's navigation mode follows its overlay a moment later, so a success shows what was asked.
            _navigation.update {
                it.copy(hidden = if (done) hidden else navigationButtons.isHidden(), busy = false, failed = !done)
            }
        }
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun ShizukuScreen(viewModel: ShizukuViewModel, onBack: () -> Unit, onOpenButtonApps: () -> Unit, modifier: Modifier = Modifier) {
    val deepBoost by viewModel.deepBoost.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val buttonAppsCount by viewModel.buttonAppsCount.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    ShizukuContent(
        deepBoost = deepBoost,
        navigation = navigation,
        buttonAppsCount = buttonAppsCount,
        onBack = onBack,
        onDeepBoostChange = viewModel::setDeepBoost,
        onNavigationButtonsHiddenChange = viewModel::setNavigationButtonsHidden,
        onOpenButtonApps = onOpenButtonApps,
        onRequestPermission = viewModel::requestPermission,
        onOpenShizuku = viewModel::openShizuku,
        modifier = modifier,
    )
}

/**
 * Shizuku (tasks 9.1, 9.6 and 9.7, ADR-9, ADR-21, ADR-23 to ADR-25): where it stands with the one
 * step it needs next, then each thing Fox does through it, explained, with its switch. Deep boost
 * is off until the user turns it on; the navigation buttons' switch shows the system's own setting
 * and changes it only when tapped, while Shizuku is ready or Fox can change it itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuContent(
    deepBoost: DeepBoostState,
    navigation: NavigationButtonsState,
    buttonAppsCount: Int,
    onBack: () -> Unit,
    onDeepBoostChange: (Boolean) -> Unit,
    onNavigationButtonsHiddenChange: (Boolean) -> Unit,
    onOpenButtonApps: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = deepBoost.status
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shizuku_title)) },
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
            Explainer(R.string.shizuku_explainer)
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(statusText(status)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag(ShizukuTestTags.STATUS),
                    )
                    val action = when (status) {
                        ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_get to onOpenShizuku
                        ShizukuStatus.NEEDS_PERMISSION -> R.string.shizuku_allow to onRequestPermission
                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.TOO_OLD, ShizukuStatus.DENIED -> R.string.shizuku_open to onOpenShizuku
                        ShizukuStatus.READY -> null
                    }
                    if (action != null) {
                        FilledTonalButton(
                            onClick = action.second,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .testTag(ShizukuTestTags.ACTION),
                        ) { Text(stringResource(action.first)) }
                    }
                }
            }

            SectionHeader(R.string.deep_boost_title)
            Explainer(R.string.deep_boost_explainer)
            ListItem(
                headlineContent = { Text(stringResource(R.string.deep_boost_switch)) },
                supportingContent = if (deepBoost.enabled && !deepBoost.active) {
                    { Text(stringResource(R.string.deep_boost_fallback)) }
                } else {
                    null
                },
                // The row handles the toggle, so the switch itself is display-only.
                trailingContent = { Switch(checked = deepBoost.enabled, onCheckedChange = null) },
                modifier = Modifier
                    .toggleable(value = deepBoost.enabled, role = Role.Switch, onValueChange = onDeepBoostChange)
                    .testTag(ShizukuTestTags.DEEP_BOOST_SWITCH),
            )

            SectionHeader(R.string.navigation_buttons_title)
            Explainer(R.string.navigation_buttons_explainer)
            val hidden = navigation.hidden == true
            val available = navigation.direct || status == ShizukuStatus.READY
            val canChange = available && !navigation.busy
            val note = when {
                navigation.failed -> R.string.navigation_buttons_failed
                !available -> R.string.navigation_buttons_needs_shizuku
                navigation.hidden == null -> R.string.navigation_buttons_unknown
                else -> null
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.navigation_buttons_switch)) },
                supportingContent = note?.let { { Text(stringResource(it)) } },
                trailingContent = { Switch(checked = hidden, onCheckedChange = null, enabled = canChange) },
                modifier = Modifier
                    .toggleable(value = hidden, enabled = canChange, role = Role.Switch, onValueChange = onNavigationButtonsHiddenChange)
                    .testTag(ShizukuTestTags.NAVIGATION_SWITCH),
            )
            // Per app (task 9.7): what each app does is on its own screen; here how many and whether it matters now.
            ListItem(
                headlineContent = { Text(stringResource(R.string.button_apps_title)) },
                supportingContent = {
                    Text(
                        when {
                            buttonAppsCount > 0 && navigation.hidden == false -> stringResource(R.string.button_apps_everywhere)
                            buttonAppsCount > 0 -> pluralStringResource(R.plurals.button_apps_count, buttonAppsCount, buttonAppsCount)
                            else -> stringResource(R.string.button_apps_none)
                        },
                    )
                },
                modifier = Modifier
                    .clickable(onClick = onOpenButtonApps)
                    .testTag(ShizukuTestTags.BUTTON_APPS),
            )
        }
    }
}

@Composable
private fun Explainer(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun statusText(status: ShizukuStatus): Int = when (status) {
    ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_status_not_installed
    ShizukuStatus.NOT_RUNNING -> R.string.shizuku_status_not_running
    ShizukuStatus.TOO_OLD -> R.string.shizuku_status_too_old
    ShizukuStatus.NEEDS_PERMISSION -> R.string.shizuku_status_needs_permission
    ShizukuStatus.DENIED -> R.string.shizuku_status_denied
    ShizukuStatus.READY -> R.string.shizuku_status_ready
}

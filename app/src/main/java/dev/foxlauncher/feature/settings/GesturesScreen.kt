package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.core.apps.AppSearch
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.AppShortcut
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog

object GesturesTestTags {
    const val ACTION_PICKER = "gestures_action_picker"
    const val APP_CHOOSER = "gestures_app_chooser"
    const val SHORTCUT_PICKER = "gestures_shortcut_picker"
    const val NAVIGATION_SWITCH = "gestures_navigation_switch"
}

@Composable
fun GesturesScreen(
    viewModel: GesturesViewModel,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Coming back from the system settings: the service may have been turned on or off there.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccessibility()
        onPauseOrDispose {}
    }
    GesturesContent(
        state = state,
        onSetGesture = viewModel::setGesture,
        onReset = viewModel::resetGestures,
        loadShortcuts = viewModel::shortcutsFor,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onBack = onBack,
        modifier = modifier,
        onRestartService = viewModel::restartService,
        onSetNavigationGestures = viewModel::setNavigationGestures,
    )
}

/**
 * Every gesture with its action (task 3.5). Tapping one opens the action picker; "Open an app…"
 * goes through an app chooser, "Open an app shortcut…" through the chooser and then that app's
 * shortcuts. Actions that need the accessibility service carry a shield, and the service's status
 * heads the list with a way to turn it on. Above the home gestures, the switch for the navigation
 * gestures in other apps (task 3.8); turning it on while the service is not running shows how to
 * turn the service on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesContent(
    state: GesturesUiState,
    onSetGesture: (GestureType, GestureAction) -> Unit,
    onReset: () -> Unit,
    loadShortcuts: suspend (AppEntry) -> List<AppShortcut>,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRestartService: () -> Unit = {},
    onSetNavigationGestures: (Boolean) -> Unit = {},
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, ROW_ICON_SIZE.dp) },
) {
    val appsByKey = remember(state.apps) { state.apps.associateBy { it.key } }
    var editing by rememberSaveable { mutableStateOf<GestureType?>(null) }
    var choosingAppFor by rememberSaveable { mutableStateOf<GestureType?>(null) }
    var choosingForShortcut by rememberSaveable { mutableStateOf(false) }
    var shortcutsOf by remember { mutableStateOf<Pair<GestureType, AppEntry>?>(null) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    val chooserType = choosingAppFor
    if (chooserType != null) {
        BackHandler { choosingAppFor = null }
        AppChooser(
            apps = state.apps,
            iconContent = iconContent,
            onBack = { choosingAppFor = null },
            onChoose = { entry ->
                choosingAppFor = null
                if (choosingForShortcut) {
                    shortcutsOf = chooserType to entry
                } else {
                    onSetGesture(chooserType, GestureAction.OpenApp(entry.component, entry.userSerial))
                }
            },
            modifier = modifier,
        )
    } else {
        BackHandler(onBack = onBack)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_gestures)) },
                    navigationIcon = { BackButton(onBack) },
                    actions = { TextButton(onClick = onReset) { Text(stringResource(R.string.gestures_reset)) } },
                )
            },
            modifier = modifier.fillMaxSize(),
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                item {
                    // With the adb grant, a stopped service is restarted in place; otherwise it is the settings.
                    val restartsInPlace = state.accessibility == AccessibilityStatus.STOPPED && state.autoRestart
                    AccessibilityBanner(
                        status = state.accessibility,
                        canRestart = state.autoRestart,
                        onFix = { if (restartsInPlace) onRestartService() else showDisclosure = true },
                    )
                }
                item { AutoRestartRow(state.autoRestart, state.showMiuiLockHint) }
                item {
                    val serviceOn = state.accessibility == AccessibilityStatus.ON
                    NavigationGesturesRow(
                        isOn = state.navigationGestures,
                        accessibilityOn = serviceOn,
                        onToggle = { on ->
                            onSetNavigationGestures(on)
                            if (on && !serviceOn) showDisclosure = true
                        },
                    )
                }
                items(state.rows, key = { it.type }) { row ->
                    ListItem(
                        headlineContent = { Text(stringResource(row.type.labelRes())) },
                        supportingContent = { Text(actionLabel(row.action, appsByKey)) },
                        trailingContent = if (row.action.requiresAccessibility) {
                            { ShieldIcon(isOn = state.accessibility == AccessibilityStatus.ON) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { editing = row.type },
                    )
                }
            }
        }
    }

    editing?.let { type ->
        ActionPickerDialog(
            current = state.rows.firstOrNull { it.type == type }?.action ?: GestureAction.None,
            accessibilityOn = state.accessibility == AccessibilityStatus.ON,
            onPick = { action ->
                editing = null
                onSetGesture(type, action)
            },
            onPickApp = { forShortcut ->
                editing = null
                choosingForShortcut = forShortcut
                choosingAppFor = type
            },
            onDismiss = { editing = null },
        )
    }
    shortcutsOf?.let { (type, entry) ->
        ShortcutPickerDialog(
            entry = entry,
            loadShortcuts = loadShortcuts,
            onPick = { shortcut ->
                shortcutsOf = null
                onSetGesture(
                    type,
                    GestureAction.OpenShortcut(shortcut.packageName, shortcut.id, shortcut.userSerial, shortcut.shortLabel),
                )
            },
            onDismiss = { shortcutsOf = null },
        )
    }
    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            stopped = state.accessibility == AccessibilityStatus.STOPPED,
            onOpenSettings = {
                showDisclosure = false
                onOpenAccessibilitySettings()
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

/**
 * The service's state heading the list. When it is off, or stopped and Fox cannot restart it,
 * [onFix] leads to the settings; when it is stopped and [canRestart], [onFix] restarts it.
 */
@Composable
private fun AccessibilityBanner(status: AccessibilityStatus, canRestart: Boolean, onFix: () -> Unit) {
    val isOn = status == AccessibilityStatus.ON
    Surface(
        color = if (isOn) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ShieldIcon(isOn)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    when (status) {
                        AccessibilityStatus.ON -> R.string.gestures_accessibility_on
                        AccessibilityStatus.STOPPED ->
                            if (canRestart) R.string.gestures_accessibility_stopped_restartable else R.string.gestures_accessibility_stopped
                        AccessibilityStatus.OFF -> R.string.gestures_accessibility_off
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            when (status) {
                AccessibilityStatus.ON -> Unit
                AccessibilityStatus.STOPPED -> TextButton(onClick = onFix) {
                    Text(stringResource(if (canRestart) R.string.gestures_restart else R.string.accessibility_open_settings))
                }
                AccessibilityStatus.OFF -> TextButton(onClick = onFix) { Text(stringResource(R.string.gestures_turn_on)) }
            }
        }
    }
}

/**
 * Whether Fox restarts a stopped service by itself, and if not, the one adb command that lets it
 * (ADR-18); on Xiaomi phones also how to keep "clear recent apps" from closing Fox at all.
 */
@Composable
private fun AutoRestartRow(isOn: Boolean, showMiuiLockHint: Boolean) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.gestures_auto_restart)) },
        supportingContent = {
            Column {
                Text(stringResource(if (isOn) R.string.gestures_auto_restart_on else R.string.gestures_auto_restart_off))
                if (!isOn) {
                    SelectionContainer {
                        Text(
                            text = stringResource(R.string.gestures_auto_restart_command),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (showMiuiLockHint) {
                    Text(stringResource(R.string.gestures_miui_lock_hint), modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
    )
}

/** The switch for Back, Home and Recents from the screen edges in other apps (task 3.8). */
@Composable
private fun NavigationGesturesRow(isOn: Boolean, accessibilityOn: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.gestures_navigation)) },
        supportingContent = { Text(stringResource(R.string.gestures_navigation_summary)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldIcon(accessibilityOn)
                Spacer(Modifier.width(12.dp))
                // The row handles the toggle, so the switch itself is display-only.
                Switch(checked = isOn, onCheckedChange = null)
            }
        },
        modifier = Modifier
            .toggleable(value = isOn, role = Role.Switch, onValueChange = onToggle)
            .testTag(GesturesTestTags.NAVIGATION_SWITCH),
    )
}

@Composable
private fun ShieldIcon(isOn: Boolean) {
    Icon(
        painter = painterResource(R.drawable.ic_shield),
        contentDescription = stringResource(R.string.gestures_needs_accessibility),
        tint = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(20.dp),
    )
}

/** The fixed actions, then "Open an app…" and "Open an app shortcut…" ([onPickApp] says which). */
@Composable
private fun ActionPickerDialog(
    current: GestureAction,
    accessibilityOn: Boolean,
    onPick: (GestureAction) -> Unit,
    onPickApp: (forShortcut: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.gestures_choose_action)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PICKABLE_ACTIONS.forEach { action ->
                    ChoiceRow(
                        label = actionLabel(action, emptyMap()),
                        selected = action == current,
                        needsAccessibility = action.requiresAccessibility,
                        accessibilityOn = accessibilityOn,
                        onClick = { onPick(action) },
                    )
                }
                ChoiceRow(
                    label = stringResource(R.string.action_open_app_pick),
                    selected = current is GestureAction.OpenApp,
                    onClick = { onPickApp(false) },
                )
                ChoiceRow(
                    label = stringResource(R.string.action_open_shortcut_pick),
                    selected = current is GestureAction.OpenShortcut,
                    onClick = { onPickApp(true) },
                )
            }
        },
        modifier = Modifier.testTag(GesturesTestTags.ACTION_PICKER),
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    needsAccessibility: Boolean = false,
    accessibilityOn: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        // The row handles the click, so the radio button itself is display-only.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        if (needsAccessibility) ShieldIcon(accessibilityOn)
    }
}

/** Every app, searchable; full screen, since the catalog is long. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppChooser(
    apps: List<AppEntry>,
    iconContent: @Composable (AppEntry) -> Unit,
    onBack: () -> Unit,
    onChoose: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query = rememberTextFieldState()
    val shown = apps.filter { AppSearch.match(query.text.toString(), it.label) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestures_choose_app)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag(GesturesTestTags.APP_CHOOSER),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                state = query,
                placeholder = { Text(stringResource(R.string.drawer_search_hint)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(shown, key = { it.key }) { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(entry) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        iconContent(entry)
                        Spacer(Modifier.width(16.dp))
                        Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutPickerDialog(
    entry: AppEntry,
    loadShortcuts: suspend (AppEntry) -> List<AppShortcut>,
    onPick: (AppShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    val shortcuts by produceState<List<AppShortcut>?>(null, entry) { value = loadShortcuts(entry) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.gestures_choose_shortcut)) },
        text = {
            val loaded = shortcuts
            when {
                loaded == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                loaded.isEmpty() -> Text(stringResource(R.string.gestures_no_shortcuts))
                else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    loaded.forEach { shortcut ->
                        Text(
                            text = shortcut.longLabel ?: shortcut.shortLabel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(shortcut) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        modifier = Modifier.testTag(GesturesTestTags.SHORTCUT_PICKER),
    )
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
    }
}

private const val ROW_ICON_SIZE = 40

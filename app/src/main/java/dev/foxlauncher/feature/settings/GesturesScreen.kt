package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.foxlauncher.core.model.MacroStep
import dev.foxlauncher.core.model.Macros
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog

object GesturesTestTags {
    const val ACTION_PICKER = "gestures_action_picker"
    const val APP_CHOOSER = "gestures_app_chooser"
    const val SHORTCUT_PICKER = "gestures_shortcut_picker"
    const val NAVIGATION_SWITCH = "gestures_navigation_switch"
    const val PAGE_SCOPE = "gestures_page_scope"
    const val MACRO_EDITOR = "gestures_macro_editor"
    const val MACRO_STEP_PICKER = "gestures_macro_step_picker"

    fun macroDelay(index: Int) = "gestures_macro_delay_$index"
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
        onSetPageGesture = viewModel::setPageGesture,
    )
}

/**
 * Every gesture with its action (task 3.5). Tapping one opens the action picker; "Open an app…"
 * goes through an app chooser, "Open an app shortcut…" through the chooser and then that app's
 * shortcuts. Actions that need the accessibility service carry a shield, and the service's status
 * heads the list with a way to turn it on. Above the home gestures, the switch for the navigation
 * gestures in other apps (task 3.8); turning it on while the service is not running shows how to
 * turn the service on. A page selector above the gestures edits one home page's own actions,
 * which fall back to all pages' (task 9.3); "Macro…" builds a list of steps with waits.
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
    onSetPageGesture: (Int, GestureType, GestureAction?) -> Unit = { _, _, _ -> },
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, ROW_ICON_SIZE.dp) },
) {
    val appsByKey = remember(state.apps) { state.apps.associateBy { it.key } }
    var editing by rememberSaveable { mutableStateOf<GestureType?>(null) }
    var choosingAppFor by rememberSaveable { mutableStateOf<GestureType?>(null) }
    var choosingForShortcut by rememberSaveable { mutableStateOf(false) }
    var shortcutsOf by remember { mutableStateOf<Pair<GestureType, AppEntry>?>(null) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    // Null: all pages. A page that has gone since it was chosen falls back to all pages.
    var scopePage by rememberSaveable { mutableStateOf<Int?>(null) }
    val page = scopePage?.takeIf { it < state.pageCount }
    val setAction: (GestureType, GestureAction) -> Unit = { type, action ->
        if (page == null) onSetGesture(type, action) else onSetPageGesture(page, type, action)
    }
    val shownAction: (GestureRow) -> GestureAction = { row -> page?.let { state.pageGestures[it]?.get(row.type) } ?: row.action }
    var macroFor by remember { mutableStateOf<GestureType?>(null) }
    var macroSteps by remember { mutableStateOf(listOf<MacroStep>()) }
    var macroAddingStep by remember { mutableStateOf(false) }
    var macroChoosingApp by remember { mutableStateOf(false) }

    val chooserType = choosingAppFor
    if (macroChoosingApp) {
        BackHandler { macroChoosingApp = false }
        AppChooser(
            apps = state.apps,
            iconContent = iconContent,
            onBack = { macroChoosingApp = false },
            onChoose = { entry ->
                macroChoosingApp = false
                macroSteps = macroSteps + MacroStep(GestureAction.OpenApp(entry.component, entry.userSerial))
            },
            modifier = modifier,
        )
    } else if (chooserType != null) {
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
                    setAction(chooserType, GestureAction.OpenApp(entry.component, entry.userSerial))
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
                item { PageScopeRow(page, state.pageCount, onSelect = { scopePage = it }) }
                items(state.rows, key = { it.type }) { row ->
                    val action = shownAction(row)
                    val label = actionLabel(action, appsByKey)
                    val inherited = page != null && state.pageGestures[page]?.containsKey(row.type) != true
                    ListItem(
                        headlineContent = { Text(stringResource(row.type.labelRes())) },
                        supportingContent = { Text(if (inherited) stringResource(R.string.gestures_same_as_all_pages, label) else label) },
                        trailingContent = if (action.requiresAccessibility) {
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
        val row = state.rows.firstOrNull { it.type == type }
        val inherited = page != null && state.pageGestures[page]?.containsKey(type) != true
        ActionPickerDialog(
            current = row?.let(shownAction) ?: GestureAction.None,
            accessibilityOn = state.accessibility == AccessibilityStatus.ON,
            onPick = { action ->
                editing = null
                setAction(type, action)
            },
            inherit = if (page == null) null else inherited,
            onInherit = {
                editing = null
                page?.let { onSetPageGesture(it, type, null) }
            },
            onPickMacro = {
                editing = null
                macroSteps = (row?.let(shownAction) as? GestureAction.Macro)?.steps.orEmpty()
                macroFor = type
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
                setAction(
                    type,
                    GestureAction.OpenShortcut(shortcut.packageName, shortcut.id, shortcut.userSerial, shortcut.shortLabel),
                )
            },
            onDismiss = { shortcutsOf = null },
        )
    }
    val macroType = macroFor
    if (macroType != null && !macroChoosingApp) {
        if (macroAddingStep) {
            MacroStepPickerDialog(
                accessibilityOn = state.accessibility == AccessibilityStatus.ON,
                onPick = { action ->
                    macroAddingStep = false
                    macroSteps = macroSteps + MacroStep(action)
                },
                onPickApp = {
                    macroAddingStep = false
                    macroChoosingApp = true
                },
                onDismiss = { macroAddingStep = false },
            )
        } else {
            MacroEditorDialog(
                steps = macroSteps,
                apps = appsByKey,
                onCycleDelay = { index -> macroSteps = macroSteps.mapIndexed { i, s -> if (i == index) s.copy(delayMs = Macros.nextDelay(s.delayMs)) else s } },
                onRemove = { index -> macroSteps = macroSteps.filterIndexed { i, _ -> i != index } },
                onAdd = { macroAddingStep = true },
                onSave = {
                    macroFor = null
                    setAction(macroType, if (macroSteps.isEmpty()) GestureAction.None else GestureAction.Macro(macroSteps))
                },
                onDismiss = { macroFor = null },
            )
        }
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

/**
 * The fixed actions, then "Open an app…" and "Open an app shortcut…" ([onPickApp] says which), and
 * "Macro…" ([onPickMacro], task 9.3). [inherit] is null on all pages; on one page it offers "Same as
 * all pages" first ([onInherit]), selected when true.
 */
@Composable
private fun ActionPickerDialog(
    current: GestureAction,
    accessibilityOn: Boolean,
    onPick: (GestureAction) -> Unit,
    onPickApp: (forShortcut: Boolean) -> Unit,
    onDismiss: () -> Unit,
    inherit: Boolean? = null,
    onInherit: () -> Unit = {},
    onPickMacro: () -> Unit = {},
) {
    val own = inherit != true
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.gestures_choose_action)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (inherit != null) {
                    ChoiceRow(label = stringResource(R.string.gestures_inherit), selected = inherit, onClick = onInherit)
                }
                PICKABLE_ACTIONS.forEach { action ->
                    ChoiceRow(
                        label = actionLabel(action, emptyMap()),
                        selected = own && action == current,
                        needsAccessibility = action.requiresAccessibility,
                        accessibilityOn = accessibilityOn,
                        onClick = { onPick(action) },
                    )
                }
                ChoiceRow(
                    label = stringResource(R.string.action_open_app_pick),
                    selected = own && current is GestureAction.OpenApp,
                    onClick = { onPickApp(false) },
                )
                ChoiceRow(
                    label = stringResource(R.string.action_open_shortcut_pick),
                    selected = own && current is GestureAction.OpenShortcut,
                    onClick = { onPickApp(true) },
                )
                ChoiceRow(
                    label = stringResource(R.string.action_macro_pick),
                    selected = own && current is GestureAction.Macro,
                    onClick = onPickMacro,
                )
            }
        },
        modifier = Modifier.testTag(GesturesTestTags.ACTION_PICKER),
    )
}

/** Which home page the gestures below are for (task 9.3): all of them, or one with its own actions. */
@Composable
private fun PageScopeRow(page: Int?, pageCount: Int, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(stringResource(R.string.gestures_pages)) },
            supportingContent = { Text(pageName(page)) },
            modifier = Modifier
                .clickable { open = true }
                .testTag(GesturesTestTags.PAGE_SCOPE),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<Int?>(null) + (0 until pageCount)).forEach { choice ->
                DropdownMenuItem(
                    text = { Text(pageName(choice)) },
                    onClick = {
                        open = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun pageName(page: Int?): String =
    if (page == null) stringResource(R.string.gestures_all_pages) else stringResource(R.string.gestures_page, page + 1)

/**
 * A macro being edited (task 9.3): its steps in order, each with its wait, which a tap cycles
 * through [Macros.DELAYS_MS], and a remove button; [onAdd] picks another, up to [Macros.MAX_STEPS].
 */
@Composable
private fun MacroEditorDialog(
    steps: List<MacroStep>,
    apps: Map<String, AppEntry>,
    onCycleDelay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.macro_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.macro_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.macro_explainer), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                if (steps.isEmpty()) {
                    Text(
                        stringResource(R.string.macro_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ${actionLabel(step.action, apps)}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { onCycleDelay(index) }, modifier = Modifier.testTag(GesturesTestTags.macroDelay(index))) {
                            Text(stringResource(R.string.macro_wait, seconds(step.delayMs)))
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.macro_remove_step, index + 1))
                        }
                    }
                }
                if (steps.size < Macros.MAX_STEPS) {
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.macro_add_step)) }
                }
            }
        },
        modifier = Modifier.testTag(GesturesTestTags.MACRO_EDITOR),
    )
}

private fun seconds(delayMs: Long): String = if (delayMs % 1_000L == 0L) "${delayMs / 1_000L}" else "${delayMs / 1_000L}.${delayMs % 1_000L / 100L}"

/** A macro step's action: the fixed ones but "None", or an app ([onPickApp]). No shortcuts or macros. */
@Composable
private fun MacroStepPickerDialog(
    accessibilityOn: Boolean,
    onPick: (GestureAction) -> Unit,
    onPickApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.macro_add_step)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PICKABLE_ACTIONS.filter { it != GestureAction.None }.forEach { action ->
                    ChoiceRow(
                        label = actionLabel(action, emptyMap()),
                        selected = false,
                        needsAccessibility = action.requiresAccessibility,
                        accessibilityOn = accessibilityOn,
                        onClick = { onPick(action) },
                    )
                }
                ChoiceRow(label = stringResource(R.string.action_open_app_pick), selected = false, onClick = onPickApp)
            }
        },
        modifier = Modifier.testTag(GesturesTestTags.MACRO_STEP_PICKER),
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

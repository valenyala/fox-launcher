package dev.foxlauncher.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.WallpaperSourceType
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.wallpaper.TriggerPolicy
import kotlin.math.roundToInt

object WallpaperSettingsTestTags {
    const val LIST = "wallpaper_settings_list"
    const val INTERVAL_PICKER = "wallpaper_interval_picker"
    const val CROSSFADE = "wallpaper_crossfade"
}

/** What the wallpaper settings ask for. [WallpaperSettingsScreen] wires it to the view model, the pickers and the activity. */
@Stable
interface WallpaperSettingsActions {
    fun back()
    fun applyLiveWallpaper()
    fun nextNow()
    fun show(entryId: Long)
    fun setInterval(minutes: Int)
    fun setOnUnlock(enabled: Boolean)
    fun setOnPageChange(enabled: Boolean)
    fun setShuffle(enabled: Boolean)
    fun setCrossfadeMs(ms: Int)
    fun setParallax(enabled: Boolean)
    fun setChangeLockScreen(enabled: Boolean)
    fun addFolder()
    fun addPictures()
    fun setSourceEnabled(sourceId: Long, enabled: Boolean)
    fun removeSource(sourceId: Long)
    fun reauthorize(sourceId: Long)
}

@Composable
fun WallpaperSettingsScreen(
    viewModel: WallpaperSettingsViewModel,
    onBack: () -> Unit,
    onApplyLiveWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnApply by rememberUpdatedState(onApplyLiveWallpaper)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let(viewModel::addFolder)
    }
    var reauthorizing by rememberSaveable { mutableStateOf<Long?>(null) }
    val reauthorizePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val sourceId = reauthorizing
        reauthorizing = null
        if (tree != null && sourceId != null) viewModel.reauthorize(sourceId, tree)
    }
    val picturesPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPictures(uris)
    }
    val actions = remember(viewModel) {
        object : WallpaperSettingsActions {
            override fun back() = currentOnBack()
            override fun applyLiveWallpaper() = currentOnApply()
            override fun nextNow() = viewModel.nextNow()
            override fun show(entryId: Long) = viewModel.show(entryId)
            override fun setInterval(minutes: Int) = viewModel.setInterval(minutes)
            override fun setOnUnlock(enabled: Boolean) = viewModel.setOnUnlock(enabled)
            override fun setOnPageChange(enabled: Boolean) = viewModel.setOnPageChange(enabled)
            override fun setShuffle(enabled: Boolean) = viewModel.setShuffle(enabled)
            override fun setCrossfadeMs(ms: Int) = viewModel.setCrossfadeMs(ms)
            override fun setParallax(enabled: Boolean) = viewModel.setParallax(enabled)
            override fun setChangeLockScreen(enabled: Boolean) = viewModel.setChangeLockScreen(enabled)
            override fun addFolder() = launchPicker { folderPicker.launch(null) }
            override fun addPictures() = launchPicker {
                picturesPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            override fun setSourceEnabled(sourceId: Long, enabled: Boolean) = viewModel.setSourceEnabled(sourceId, enabled)
            override fun removeSource(sourceId: Long) = viewModel.removeSource(sourceId)
            override fun reauthorize(sourceId: Long) {
                reauthorizing = sourceId
                launchPicker { reauthorizePicker.launch(null) }
            }
        }
    }
    WallpaperSettingsContent(state = state, actions = actions, modifier = modifier)
}

/** Some devices have no document picker (stripped builds); there is nothing to add from then. */
private fun launchPicker(launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        FoxLog.w("No picker for wallpaper sources", e)
    }
}

/**
 * Everything about the wallpaper (tasks 4.4–4.7): whether Fox's live wallpaper is applied, "next
 * now", what changes it, how it looks, where the pictures come from, and the pictures themselves
 * (tap one to show it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsContent(
    state: WallpaperSettingsUiState,
    actions: WallpaperSettingsActions,
    modifier: Modifier = Modifier,
) {
    var pickingInterval by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(onBack = actions::back)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_wallpaper)) },
                navigationIcon = { BackButton(actions::back) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth - GRID_PADDING * 2 + GRID_GAP) / (THUMB_MIN_WIDTH + GRID_GAP)).toInt().coerceAtLeast(MIN_COLUMNS)
            val enabledSources = state.sources.count { it.enabled }
            LazyColumn(contentPadding = padding, modifier = Modifier.testTag(WallpaperSettingsTestTags.LIST)) {
                item {
                    Banner(
                        text = stringResource(if (state.liveWallpaperActive) R.string.wallpaper_live_active else R.string.wallpaper_live_inactive),
                        warning = !state.liveWallpaperActive,
                        action = if (state.liveWallpaperActive) null else stringResource(R.string.wallpaper_apply),
                        onAction = actions::applyLiveWallpaper,
                    )
                }
                // A folder that lost its access is skipped by the rotation until it is picked again (task 4.9).
                state.sources.firstOrNull { it.needsReauth }?.let { lost ->
                    item(key = "reauth ${lost.id}") {
                        Banner(
                            text = stringResource(R.string.wallpaper_reauth_banner, sourceName(lost)),
                            warning = true,
                            action = stringResource(R.string.wallpaper_reauth_pick),
                            onAction = { actions.reauthorize(lost.id) },
                        )
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.wallpaper_next_now)) },
                        modifier = Modifier.clickable(onClick = actions::nextNow),
                    )
                }

                item { SectionHeader(R.string.wallpaper_section_when) }
                item { IntervalRow(state, onClick = { pickingInterval = true }) }
                item { SwitchRow(R.string.wallpaper_on_unlock, state.triggers.onUnlock, actions::setOnUnlock) }
                item { SwitchRow(R.string.wallpaper_on_page_change, state.triggers.onPageChange, actions::setOnPageChange) }

                item { SectionHeader(R.string.wallpaper_section_look) }
                item { SwitchRow(R.string.wallpaper_shuffle, state.shuffle, actions::setShuffle, R.string.wallpaper_shuffle_summary) }
                item { CrossfadeRow(state.crossfadeMs, actions::setCrossfadeMs) }
                item { SwitchRow(R.string.wallpaper_parallax, state.parallax, actions::setParallax, R.string.wallpaper_parallax_summary) }
                item { SwitchRow(R.string.wallpaper_lock_screen, state.changeLockScreen, actions::setChangeLockScreen, R.string.wallpaper_lock_screen_summary) }

                item { SectionHeader(R.string.wallpaper_section_sources) }
                items(state.sources, key = { "source ${it.id}" }) { source ->
                    SourceRow(
                        source = source,
                        // The last enabled source stays on: with no pictures the wallpaper would be blank.
                        canTurnOff = enabledSources > 1,
                        onEnabledChange = { actions.setSourceEnabled(source.id, it) },
                        onRemove = { removing = source.id },
                        onReauthorize = { actions.reauthorize(source.id) },
                    )
                }
                item {
                    ActionRow(R.drawable.ic_create_new_folder, R.string.wallpaper_add_folder, R.string.wallpaper_add_folder_summary, actions::addFolder)
                }
                item {
                    ActionRow(R.drawable.ic_add_photo, R.string.wallpaper_add_pictures, R.string.wallpaper_add_pictures_summary, actions::addPictures)
                }

                item { SectionHeader(R.string.wallpaper_section_pictures) }
                if (state.entries.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.wallpaper_pictures_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(state.entries.chunked(columns), key = { row -> "pictures ${row.first().id}" }) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
                        modifier = Modifier.padding(horizontal = GRID_PADDING, vertical = GRID_GAP / 2),
                    ) {
                        row.forEach { entry ->
                            Thumbnail(entry, showing = entry.id == state.showingId, onClick = { actions.show(entry.id) }, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    if (pickingInterval) {
        IntervalPicker(
            current = state.triggers.intervalMinutes,
            onPick = {
                pickingInterval = false
                actions.setInterval(it)
            },
            onDismiss = { pickingInterval = false },
        )
    }
    state.sources.firstOrNull { it.id == removing }?.let { source ->
        val name = sourceName(source)
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.wallpaper_remove_title, name)) },
            text = { Text(stringResource(R.string.wallpaper_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    actions.removeSource(source.id)
                }) { Text(stringResource(R.string.wallpaper_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/** A note at the top of the settings; a [warning] stands out, with its fix as [action]. */
@Composable
private fun Banner(text: String, warning: Boolean, action: String?, onAction: () -> Unit) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun SectionHeader(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun IntervalRow(state: WallpaperSettingsUiState, onClick: () -> Unit) {
    val minutes = state.triggers.intervalMinutes
    // Under 15 minutes the live wallpaper's own timer does the work (task 4.4).
    val needsLive = !state.liveWallpaperActive && minutes in 1 until TriggerPolicy.WORK_MANAGER_MIN_MINUTES
    ListItem(
        headlineContent = { Text(stringResource(R.string.wallpaper_interval)) },
        supportingContent = {
            Text(if (needsLive) stringResource(R.string.wallpaper_interval_needs_live, intervalLabel(minutes)) else intervalLabel(minutes))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit, summary: Int? = null) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        // The row handles the toggle, so the switch itself is display-only.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** The crossfade's length in whole 100 ms steps (snapped here: slider steps would draw 16 tick dots), saved when let go. */
@Composable
private fun CrossfadeRow(ms: Int, onChange: (Int) -> Unit) {
    var value by remember(ms) { mutableFloatStateOf(ms.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(stringResource(R.string.wallpaper_crossfade), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.wallpaper_crossfade_ms, value.roundToInt()), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = { value = (it / CROSSFADE_STEP_MS).roundToInt() * CROSSFADE_STEP_MS.toFloat() },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = FoxSettings.MIN_CROSSFADE_MS.toFloat()..FoxSettings.MAX_CROSSFADE_MS.toFloat(),
            modifier = Modifier.testTag(WallpaperSettingsTestTags.CROSSFADE),
        )
    }
}

@Composable
private fun SourceRow(
    source: WallpaperSourceUi,
    canTurnOff: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onReauthorize: () -> Unit,
) {
    val name = sourceName(source)
    val toggleAllowed = !source.enabled || canTurnOff
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            if (source.needsReauth) {
                Text(stringResource(R.string.wallpaper_source_reauth), color = MaterialTheme.colorScheme.error)
            } else {
                Text(pluralStringResource(R.plurals.wallpaper_source_pictures, source.imageCount, source.imageCount))
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The built-in pictures come back with every start: they can be turned off, not removed.
                if (source.type != WallpaperSourceType.BUILT_IN) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.wallpaper_source_remove, name))
                    }
                }
                Switch(checked = source.enabled, onCheckedChange = null, enabled = toggleAllowed)
            }
        },
        modifier = if (source.needsReauth) {
            Modifier.clickable(onClick = onReauthorize)
        } else {
            Modifier.toggleable(value = source.enabled, enabled = toggleAllowed, role = Role.Switch, onValueChange = onEnabledChange)
        },
    )
}

@Composable
private fun sourceName(source: WallpaperSourceUi): String = when (source.type) {
    WallpaperSourceType.BUILT_IN -> stringResource(R.string.wallpaper_source_built_in)
    WallpaperSourceType.PICKED_IMAGES -> stringResource(R.string.wallpaper_source_picked)
    WallpaperSourceType.FOLDER_TREE -> source.name
}

@Composable
private fun ActionRow(icon: Int, label: Int, summary: Int, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        headlineContent = { Text(stringResource(label)) },
        supportingContent = { Text(stringResource(summary)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** One wallpaper, cropped like the screen; the one showing is outlined, one that cannot be read is dimmed. */
@Composable
private fun Thumbnail(entry: WallpaperEntryUi, showing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .aspectRatio(THUMB_ASPECT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (showing) Modifier.border(SHOWING_BORDER, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics { selected = showing }
            .clickable(onClickLabel = stringResource(R.string.wallpaper_show_now), onClick = onClick),
    ) {
        AsyncImage(
            model = entry.thumbnail,
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (entry.failing) FAILING_ALPHA else 1f),
        )
        if (entry.failing) {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = stringResource(R.string.wallpaper_entry_failing),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun IntervalPicker(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        title = { Text(stringResource(R.string.wallpaper_interval)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf(0) + TriggerPolicy.INTERVALS).forEach { minutes ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = minutes == current, role = Role.RadioButton) { onPick(minutes) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = minutes == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(intervalLabel(minutes))
                    }
                }
            }
        },
        modifier = Modifier.testTag(WallpaperSettingsTestTags.INTERVAL_PICKER),
    )
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.wallpaper_interval_off)
    minutes % MINUTES_PER_DAY == 0 -> pluralStringResource(R.plurals.wallpaper_every_days, minutes / MINUTES_PER_DAY, minutes / MINUTES_PER_DAY)
    minutes % MINUTES_PER_HOUR == 0 -> pluralStringResource(R.plurals.wallpaper_every_hours, minutes / MINUTES_PER_HOUR, minutes / MINUTES_PER_HOUR)
    else -> pluralStringResource(R.plurals.wallpaper_every_minutes, minutes, minutes)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1440
private const val CROSSFADE_STEP_MS = 100
private val THUMB_MIN_WIDTH = 80.dp
private val GRID_PADDING = 16.dp
private val GRID_GAP = 8.dp
private const val MIN_COLUMNS = 3

/** Roughly a phone screen's shape. */
private const val THUMB_ASPECT = 0.5f
private val SHOWING_BORDER = 3.dp
private const val FAILING_ALPHA = 0.4f

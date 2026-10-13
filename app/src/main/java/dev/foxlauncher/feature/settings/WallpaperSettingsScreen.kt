package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.feature.wallpaper.TriggerPolicy

object WallpaperSettingsTestTags {
    const val INTERVAL_PICKER = "wallpaper_interval_picker"
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
    WallpaperSettingsContent(
        state = state,
        onBack = onBack,
        onApplyLiveWallpaper = onApplyLiveWallpaper,
        onNextNow = viewModel::nextNow,
        onIntervalChange = viewModel::setInterval,
        onUnlockChange = viewModel::setOnUnlock,
        onPageChangeChange = viewModel::setOnPageChange,
        onLockScreenChange = viewModel::setChangeLockScreen,
        modifier = modifier,
    )
}

/**
 * Wallpaper rotation (task 4.4): whether Fox's live wallpaper is applied, "next now", what changes
 * the wallpaper by itself, and the lock screen (task 4.6). Sources, order, duration and more join
 * in task 4.7.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsContent(
    state: WallpaperSettingsUiState,
    onBack: () -> Unit,
    onApplyLiveWallpaper: () -> Unit,
    onNextNow: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onUnlockChange: (Boolean) -> Unit,
    onPageChangeChange: (Boolean) -> Unit,
    onLockScreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingInterval by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_wallpaper)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { LiveWallpaperBanner(state.liveWallpaperActive, onApplyLiveWallpaper) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wallpaper_next_now)) },
                    modifier = Modifier.clickable(onClick = onNextNow),
                )
            }
            item {
                val minutes = state.triggers.intervalMinutes
                // Under 15 minutes the live wallpaper's own timer does the work (task 4.4).
                val needsLive = !state.liveWallpaperActive && minutes in 1 until TriggerPolicy.WORK_MANAGER_MIN_MINUTES
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wallpaper_interval)) },
                    supportingContent = {
                        Text(
                            if (needsLive) {
                                stringResource(R.string.wallpaper_interval_needs_live, intervalLabel(minutes))
                            } else {
                                intervalLabel(minutes)
                            },
                        )
                    },
                    modifier = Modifier.clickable { pickingInterval = true },
                )
            }
            item { SwitchRow(R.string.wallpaper_on_unlock, state.triggers.onUnlock, onUnlockChange) }
            item { SwitchRow(R.string.wallpaper_on_page_change, state.triggers.onPageChange, onPageChangeChange) }
            item { SwitchRow(R.string.wallpaper_lock_screen, state.changeLockScreen, onLockScreenChange, R.string.wallpaper_lock_screen_summary) }
        }
    }
    if (pickingInterval) {
        IntervalPicker(
            current = state.triggers.intervalMinutes,
            onPick = {
                pickingInterval = false
                onIntervalChange(it)
            },
            onDismiss = { pickingInterval = false },
        )
    }
}

@Composable
private fun LiveWallpaperBanner(active: Boolean, onApply: () -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(if (active) R.string.wallpaper_live_active else R.string.wallpaper_live_inactive),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (!active) TextButton(onClick = onApply) { Text(stringResource(R.string.wallpaper_apply)) }
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit, summary: Int? = null) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = summary?.let { { Text(stringResource(it)) } },
        // The row handles the toggle, so the switch itself is display-only.
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
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

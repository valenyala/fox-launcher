package dev.foxlauncher.feature.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.foxlauncher.R
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.apps.iconpack.IconPackInfo
import dev.foxlauncher.core.apps.iconpack.IconPacks
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.core.ui.IconShape
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object IconsTestTags {
    const val PACK = "icons_pack"
    const val PACK_DIALOG = "icons_pack_dialog"
    const val SIZE = "icons_size"
    const val LABELS = "icons_labels"
    const val THEMED = "icons_themed"
}

/** The Icons settings (task 7.1). [pack] is null for the apps' own icons, and when the chosen pack is gone. */
@Immutable
data class IconsUiState(
    val packs: ImmutableList<IconPackInfo> = persistentListOf(),
    val pack: IconPackInfo? = null,
    val shape: IconShape = IconShape.DEFAULT,
    val sizeDp: Int = FoxSettings.DEFAULT_ICON_SIZE_DP,
    val showLabels: Boolean = FoxSettings.DEFAULT_SHOW_HOME_LABELS,
    val themed: Boolean = false,
    val themedAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    val preview: ImmutableList<AppEntry> = persistentListOf(),
)

@HiltViewModel
class IconsViewModel @Inject constructor(
    private val settings: FoxSettings,
    private val iconPacks: IconPacks,
    appRepository: AppRepository,
) : ViewModel() {

    private val look = combine(settings.iconShapeId, settings.iconSizeDp, settings.showHomeLabels, settings.themedIcons) {
            shapeId, sizeDp, labels, themed ->
        Look(IconShape.fromId(shapeId), sizeDp, labels, themed)
    }

    val uiState: StateFlow<IconsUiState> =
        combine(iconPacks.installed, settings.iconPack, look, appRepository.apps) { packs, packName, look, apps ->
            IconsUiState(
                packs = packs.toImmutableList(),
                pack = packs.firstOrNull { it.packageName == packName },
                shape = look.shape,
                sizeDp = look.sizeDp,
                showLabels = look.labels,
                themed = look.themed,
                preview = apps.take(PREVIEW_APPS).toImmutableList(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), IconsUiState())

    /** On every return to the screen: the user may have installed a pack meanwhile. */
    fun refresh() {
        viewModelScope.launch { iconPacks.refresh() }
    }

    fun setPack(packageName: String?) = launch { settings.setIconPack(packageName) }

    fun setShape(shape: IconShape) = launch { settings.setIconShape(shape.id) }

    fun setSize(sizeDp: Int) = launch { settings.setIconSize(sizeDp) }

    fun setShowLabels(show: Boolean) = launch { settings.setShowHomeLabels(show) }

    fun setThemed(themed: Boolean) = launch { settings.setThemedIcons(themed) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class Look(val shape: IconShape, val sizeDp: Int, val labels: Boolean, val themed: Boolean)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PREVIEW_APPS = 5
    }
}

@Composable
fun IconsScreen(viewModel: IconsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    IconsContent(
        state = state,
        onBack = onBack,
        onPackChange = viewModel::setPack,
        onShapeChange = viewModel::setShape,
        onSizeChange = viewModel::setSize,
        onShowLabelsChange = viewModel::setShowLabels,
        onThemedChange = viewModel::setThemed,
        modifier = modifier,
    )
}

/**
 * Icons (task 7.1): a preview of a few apps as they look now, then the icon pack, the mask shape
 * for adaptive icons, the size (48–64 dp, home and drawer), the home labels and, on Android 13+,
 * themed icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconsContent(
    state: IconsUiState,
    onBack: () -> Unit,
    onPackChange: (String?) -> Unit,
    onShapeChange: (IconShape) -> Unit,
    onSizeChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onThemedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppEntry, Int) -> Unit = { entry, sizeDp -> AppIcon(entry, sizeDp.dp) },
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.icons_title)) },
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
            Preview(state, iconContent)

            SectionHeader(R.string.icons_section_pack)
            ListItem(
                headlineContent = { Text(stringResource(R.string.icons_pack)) },
                supportingContent = {
                    Text(
                        when {
                            state.pack != null -> state.pack.label
                            state.packs.isEmpty() -> stringResource(R.string.icons_pack_none_installed)
                            else -> stringResource(R.string.icons_pack_system)
                        },
                    )
                },
                modifier = Modifier
                    .clickable { picking = true }
                    .testTag(IconsTestTags.PACK),
            )

            SectionHeader(R.string.icons_section_shape)
            IconShape.entries.forEach { shape ->
                ListItem(
                    headlineContent = { Text(stringResource(shape.label)) },
                    leadingContent = { RadioButton(selected = shape == state.shape, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = shape == state.shape,
                        role = Role.RadioButton,
                        onClick = { onShapeChange(shape) },
                    ),
                )
            }
            Text(
                stringResource(R.string.icons_shape_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionHeader(R.string.icons_section_look)
            ListItem(
                headlineContent = { Text(stringResource(R.string.icons_size)) },
                trailingContent = { Text(stringResource(R.string.icons_size_value, state.sizeDp)) },
            )
            Slider(
                value = state.sizeDp.toFloat(),
                onValueChange = { onSizeChange(snap(it)) },
                valueRange = FoxSettings.ICON_SIZES_DP.first.toFloat()..FoxSettings.ICON_SIZES_DP.last.toFloat(),
                steps = (FoxSettings.ICON_SIZES_DP.last - FoxSettings.ICON_SIZES_DP.first) / SIZE_STEP_DP - 1,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(IconsTestTags.SIZE),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.icons_labels)) },
                trailingContent = { Switch(checked = state.showLabels, onCheckedChange = null) },
                modifier = Modifier
                    .toggleable(value = state.showLabels, role = Role.Switch, onValueChange = onShowLabelsChange)
                    .testTag(IconsTestTags.LABELS),
            )
            if (state.themedAvailable) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.icons_themed)) },
                    supportingContent = { Text(stringResource(R.string.icons_themed_summary)) },
                    trailingContent = { Switch(checked = state.themed, onCheckedChange = null) },
                    modifier = Modifier
                        .toggleable(value = state.themed, role = Role.Switch, onValueChange = onThemedChange)
                        .testTag(IconsTestTags.THEMED),
                )
            }
        }
    }
    if (picking) {
        PackDialog(
            state = state,
            onPick = { packageName ->
                picking = false
                onPackChange(packageName)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Preview(state: IconsUiState, iconContent: @Composable (AppEntry, Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        state.preview.forEach { entry ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(PREVIEW_CELL_DP.dp)) {
                iconContent(entry, state.sizeDp)
                if (state.showLabels) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PackDialog(state: IconsUiState, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.icons_pack)) },
        text = {
            LazyColumn(Modifier.testTag(IconsTestTags.PACK_DIALOG)) {
                item { PackRow(stringResource(R.string.icons_pack_system), selected = state.pack == null) { onPick(null) } }
                items(state.packs, key = { it.packageName }) { pack ->
                    PackRow(pack.label, selected = pack == state.pack) { onPick(pack.packageName) }
                }
                if (state.packs.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.icons_pack_none_installed),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun PackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

private val IconShape.label: Int
    get() = when (this) {
        IconShape.CIRCLE -> R.string.icons_shape_circle
        IconShape.SQUIRCLE -> R.string.icons_shape_squircle
        IconShape.ROUNDED_SQUARE -> R.string.icons_shape_rounded_square
    }

/** The slider's value on the nearest allowed size. */
private fun snap(value: Float): Int {
    val first = FoxSettings.ICON_SIZES_DP.first
    return first + ((value - first) / SIZE_STEP_DP).roundToInt() * SIZE_STEP_DP
}

private const val SIZE_STEP_DP = 4
private const val PREVIEW_CELL_DP = 72

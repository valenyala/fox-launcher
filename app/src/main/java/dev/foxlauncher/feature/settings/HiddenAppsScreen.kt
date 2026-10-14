package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon
import kotlinx.collections.immutable.toImmutableList

@Composable
fun HiddenAppsScreen(viewModel: HiddenAppsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    HiddenAppsContent(state, viewModel::onHiddenChange, onBack, modifier)
}

/** Every app with a checkbox; checked means hidden from the drawer. */
@Composable
fun HiddenAppsContent(
    state: HiddenAppsUiState,
    onHiddenChange: (AppEntry, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, ROW_ICON_SIZE.dp) },
) {
    val rows = remember(state.rows) { state.rows.map { AppCheckRow(it.entry, it.isHidden) }.toImmutableList() }
    AppChecklistContent(
        title = stringResource(R.string.hidden_apps_title),
        explainer = stringResource(R.string.hidden_apps_explainer),
        rows = rows,
        onCheckedChange = onHiddenChange,
        onBack = onBack,
        modifier = modifier,
        iconContent = iconContent,
    )
}

private const val ROW_ICON_SIZE = 40

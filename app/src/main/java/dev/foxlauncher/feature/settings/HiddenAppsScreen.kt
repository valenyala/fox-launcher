package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon

@Composable
fun HiddenAppsScreen(viewModel: HiddenAppsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    HiddenAppsContent(state, viewModel::onHiddenChange, onBack, modifier)
}

/** Every app with a checkbox; checked means hidden from the drawer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAppsContent(
    state: HiddenAppsUiState,
    onHiddenChange: (AppEntry, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, ROW_ICON_SIZE.dp) },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hidden_apps_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    text = stringResource(R.string.hidden_apps_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.rows, key = { it.entry.key }) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = row.isHidden,
                            role = Role.Checkbox,
                            onValueChange = { onHiddenChange(row.entry, it) },
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    iconContent(row.entry)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = row.entry.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // The row handles the toggle, so the checkbox itself is display-only.
                    Checkbox(checked = row.isHidden, onCheckedChange = null)
                }
            }
        }
    }
}

private const val ROW_ICON_SIZE = 40

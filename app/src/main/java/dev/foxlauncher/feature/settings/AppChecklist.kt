package dev.foxlauncher.feature.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AppCheckRow(val entry: AppEntry, val checked: Boolean)

/**
 * Every app with a checkbox under an explanation: the hidden apps (task 2.x) and the apps opened
 * without navigation buttons (task 9.7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppChecklistContent(
    title: String,
    explainer: String,
    rows: ImmutableList<AppCheckRow>,
    onCheckedChange: (AppEntry, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable (AppEntry) -> Unit = { AppIcon(it, ROW_ICON_SIZE.dp) },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    text = explainer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(rows, key = { it.entry.key }) { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = row.checked,
                            role = Role.Checkbox,
                            onValueChange = { onCheckedChange(row.entry, it) },
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
                    Checkbox(checked = row.checked, onCheckedChange = null)
                }
            }
        }
    }
}

private const val ROW_ICON_SIZE = 40

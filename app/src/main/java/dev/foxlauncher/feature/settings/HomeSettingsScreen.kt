package dev.foxlauncher.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.foxlauncher.R

/** Where settings screens live while Fox has no NavHost (the home must stay composed under them). */
enum class SettingsRoute { HOME, GESTURES, WALLPAPER, HIDDEN_APPS, SHIZUKU, BUTTONLESS_APPS }

/** The settings root (task 3.5): the "Home settings" gesture action and the drawer menu open it. Widgets first (task 5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    onOpen: (SettingsRoute) -> Unit,
    onOpenWidgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                // The widget picker opens over the home, not as another settings screen (task 5.1).
                ListItem(
                    headlineContent = { Text(stringResource(R.string.widgets_title)) },
                    supportingContent = { Text(stringResource(R.string.widgets_summary)) },
                    modifier = Modifier.clickable(onClick = onOpenWidgets),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_gestures)) },
                    supportingContent = { Text(stringResource(R.string.settings_gestures_summary)) },
                    modifier = Modifier.clickable { onOpen(SettingsRoute.GESTURES) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_wallpaper)) },
                    supportingContent = { Text(stringResource(R.string.settings_wallpaper_summary)) },
                    modifier = Modifier.clickable { onOpen(SettingsRoute.WALLPAPER) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.hidden_apps_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_hidden_apps_summary)) },
                    modifier = Modifier.clickable { onOpen(SettingsRoute.HIDDEN_APPS) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_shizuku)) },
                    supportingContent = { Text(stringResource(R.string.settings_shizuku_summary)) },
                    modifier = Modifier.clickable { onOpen(SettingsRoute.SHIZUKU) },
                )
            }
        }
    }
}

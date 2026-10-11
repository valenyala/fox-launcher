package dev.foxlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.foxlauncher.core.apps.IconCache
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.ui.LocalIconCache
import dev.foxlauncher.core.ui.launchSource
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.drawer.DrawerScreen
import dev.foxlauncher.feature.drawer.DrawerViewModel
import dev.foxlauncher.feature.home.HomeRoot
import dev.foxlauncher.feature.home.HomeScreen
import dev.foxlauncher.feature.home.HomeViewModel
import dev.foxlauncher.feature.home.isDefaultLauncher
import dev.foxlauncher.feature.home.requestDefaultLauncherRole
import dev.foxlauncher.feature.settings.HiddenAppsScreen
import javax.inject.Inject

/**
 * The one and only activity. It is `singleTask` and handles every configuration change itself, so
 * it must never be recreated: a launcher that restarts looks like a crash. See
 * docs/PLATFORM-NOTES.md section H.
 */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    @Inject lateinit var iconCache: IconCache

    /** Bumped by every Home key press that reaches us while in front; the home screen reacts to it. */
    private var homeKeyPresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FoxTheme {
                val viewModel: HomeViewModel = hiltViewModel()
                val homeState by viewModel.uiState.collectAsStateWithLifecycle()
                val view = LocalView.current
                val drawerViewModel: DrawerViewModel = hiltViewModel()
                // A single settings screen does not justify a NavHost yet; this becomes a route
                // once settings grow (Phase 3 gesture settings).
                var showHiddenApps by rememberSaveable { mutableStateOf(false) }

                var isDefault by remember { mutableStateOf(isDefaultLauncher()) }

                val roleRequest = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { isDefault = isDefaultLauncher() }

                // The role can also be granted or revoked while we are backgrounded (from Settings,
                // or by an OEM dialog that returns no result), so re-read it on every resume.
                LifecycleResumeEffect(Unit) {
                    isDefault = isDefaultLauncher()
                    // Idempotent: the ViewModel ignores this once first run is recorded.
                    if (isDefault) viewModel.onLauncherReady()
                    onPauseOrDispose {}
                }

                CompositionLocalProvider(LocalIconCache provides iconCache) {
                    HomeRoot(
                        isDefaultLauncher = isDefault,
                        onRequestDefaultLauncher = { requestDefaultLauncherRole(roleRequest) },
                    ) {
                        HomeScreen(
                            homeKeyPresses = homeKeyPresses,
                            onDrawerClosed = drawerViewModel::onDrawerClosed,
                            state = homeState,
                            onItemClick = { item, bounds ->
                                val source = bounds?.let { launchSource(view, it) }
                                viewModel.onItemClick(item, source?.sourceBounds, source?.options)
                            },
                            onDrop = viewModel::onDrop,
                            onRenameFolder = viewModel::onRenameFolder,
                            loadPopup = viewModel::popupFor,
                            onPopupAction = { item, action, bounds ->
                                viewModel.onPopupAction(item, action, bounds?.let { launchSource(view, it).sourceBounds })
                            },
                        ) { autoFocusSearch ->
                            DrawerScreen(
                                viewModel = drawerViewModel,
                                autoFocusSearch = autoFocusSearch,
                                onOpenHiddenApps = { showHiddenApps = true },
                                // TODO(2.7): remove with the first-run layout.
                                onAddTestItems = viewModel::addTestItems.takeIf { BuildConfig.DEBUG },
                                onAppLongPress = ::onAppLongPress,
                            )
                        }
                        if (showHiddenApps) {
                            HiddenAppsScreen(hiltViewModel(), onBack = { showHiddenApps = false })
                        }
                    }
                }
            }
        }
    }

    /**
     * Pressing Home while already home arrives here, because of `singleTask`. It closes the drawer
     * (and, from Phase 2, scrolls back to the default page); never finish or recreate the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        FoxLog.d("onNewIntent: ${intent.action}")
        if (intent.action == Intent.ACTION_MAIN) homeKeyPresses++
    }
}

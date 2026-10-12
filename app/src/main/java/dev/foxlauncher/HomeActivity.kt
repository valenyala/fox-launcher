package dev.foxlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import dev.foxlauncher.core.ui.HighRefreshRate
import dev.foxlauncher.core.ui.LocalIconCache
import dev.foxlauncher.core.ui.LocalIconGeneration
import dev.foxlauncher.core.ui.launchSource
import dev.foxlauncher.core.ui.theme.FoxTheme
import dev.foxlauncher.feature.drawer.DrawerScreen
import dev.foxlauncher.feature.drawer.DrawerViewModel
import dev.foxlauncher.feature.gestures.AccessibilityStatus
import dev.foxlauncher.feature.gestures.ExecContext
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog
import dev.foxlauncher.feature.home.HomeRoot
import dev.foxlauncher.feature.home.HomeScreen
import dev.foxlauncher.feature.home.HomeViewModel
import dev.foxlauncher.feature.home.isDefaultLauncher
import dev.foxlauncher.feature.home.requestDefaultLauncherRole
import dev.foxlauncher.feature.settings.GesturesScreen
import dev.foxlauncher.feature.settings.HiddenAppsScreen
import dev.foxlauncher.feature.settings.HomeSettingsScreen
import dev.foxlauncher.feature.settings.SettingsRoute
import javax.inject.Inject

/**
 * The one and only activity. It is `singleTask` and handles every configuration change itself, so
 * it must never be recreated: a launcher that restarts looks like a crash. See
 * docs/PLATFORM-NOTES.md section H.
 */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    @Inject lateinit var iconCache: IconCache

    /** 120 Hz while touched; MIUI would otherwise hold Fox at 60 Hz (see [HighRefreshRate]). */
    private val refreshRate = HighRefreshRate(this)

    /** Bumped by every Home press that reaches us; the home screen closes whatever is open. */
    private var homeKeyPresses by mutableIntStateOf(0)

    /** The latest Home press came while Fox was already in front, not on the way back from an app. */
    private var homeKeyWhileHome by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A launcher has nowhere to go back to. Unhandled, Back on a root launcher activity makes Android 12+
        // move the home task behind the last app, which then shows (task 3.6). Added first, so the drawer's,
        // a folder's, a popup's and the settings' handlers, registered later while open, all come before it.
        onBackPressedDispatcher.addCallback(this) {}

        setContent {
            FoxTheme {
                val viewModel: HomeViewModel = hiltViewModel()
                val homeState by viewModel.uiState.collectAsStateWithLifecycle()
                val view = LocalView.current
                val drawerViewModel: DrawerViewModel = hiltViewModel()
                // Settings screens stack over the home, which stays composed under them (see SettingsRoute).
                var settings by rememberSaveable { mutableStateOf(listOf<SettingsRoute>()) }
                // Home goes home: it closes any settings screen and the accessibility dialog, as it closes the drawer.
                LaunchedEffect(homeKeyPresses) {
                    if (homeKeyPresses > 0) {
                        settings = emptyList()
                        viewModel.dismissAccessibilityPrompt()
                    }
                }

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

                val iconGeneration by iconCache.generation.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalIconCache provides iconCache, LocalIconGeneration provides iconGeneration) {
                    HomeRoot(
                        isDefaultLauncher = isDefault,
                        onRequestDefaultLauncher = { requestDefaultLauncherRole(roleRequest) },
                    ) {
                        HomeScreen(
                            homeKeyPresses = homeKeyPresses,
                            // With a settings screen open, Home only closes it.
                            homeKeyWhileHome = homeKeyWhileHome && settings.isEmpty(),
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
                            onGesture = { type, home ->
                                viewModel.onGesture(
                                    type,
                                    ExecContext(
                                        openDrawer = home::openDrawer,
                                        openSearch = home::openSearch,
                                        scrollToDefaultPage = home::scrollToDefaultPage,
                                        openPageOverview = home::openPageOverview,
                                        openHomeSettings = { settings = listOf(SettingsRoute.HOME) },
                                    ),
                                )
                            },
                        ) {
                            DrawerScreen(
                                viewModel = drawerViewModel,
                                onOpenHiddenApps = { settings = listOf(SettingsRoute.HIDDEN_APPS) },
                                onOpenSettings = { settings = listOf(SettingsRoute.HOME) },
                                onAppLongPress = ::onAppLongPress,
                                isShown = isShown,
                                autoFocusSearch = isSearchRequested,
                            )
                        }
                        val accessibilityPrompt by viewModel.accessibilityPrompt.collectAsStateWithLifecycle()
                        accessibilityPrompt?.let { status ->
                            AccessibilityDisclosureDialog(
                                stopped = status == AccessibilityStatus.STOPPED,
                                onOpenSettings = {
                                    viewModel.dismissAccessibilityPrompt()
                                    openAccessibilitySettings()
                                },
                                onDismiss = viewModel::dismissAccessibilityPrompt,
                            )
                        }
                        val closeSettings = { settings = settings.dropLast(1) }
                        when (settings.lastOrNull()) {
                            SettingsRoute.HOME -> HomeSettingsScreen(onBack = closeSettings, onOpen = { settings = settings + it })
                            SettingsRoute.GESTURES -> GesturesScreen(
                                viewModel = hiltViewModel(),
                                onBack = closeSettings,
                                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            )
                            SettingsRoute.HIDDEN_APPS -> HiddenAppsScreen(hiltViewModel(), onBack = closeSettings)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    /**
     * Every Home press arrives here, because of `singleTask`: on the way back from an app and while
     * already home. Either closes what is open; the second is also the HOME_KEY gesture (task 3.6).
     * Never finish or recreate the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) {
            // Launcher3's test: Fox has the focus and was not brought to the front from another app.
            homeKeyWhileHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
            FoxLog.d("Home pressed ${if (homeKeyWhileHome) "while home" else "from elsewhere"}")
            // Closing the drawer and scrolling home animate: run them at full rate too.
            refreshRate.onInteractionStart()
            refreshRate.onInteractionEnd()
            homeKeyPresses++
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> refreshRate.onInteractionStart()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> refreshRate.onInteractionEnd()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            FoxLog.e("No accessibility settings screen", e)
        }
    }

    override fun onPause() {
        super.onPause()
        refreshRate.releaseNow()
    }
}

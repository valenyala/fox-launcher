package dev.foxlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.viewModels
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
import androidx.compose.ui.unit.DpSize
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
import dev.foxlauncher.feature.gestures.navigation.ButtonAppLaunches
import dev.foxlauncher.feature.gestures.system.AccessibilityDisclosureDialog
import dev.foxlauncher.feature.home.HomeRoot
import dev.foxlauncher.feature.home.HomeScreen
import dev.foxlauncher.feature.home.HomeViewModel
import dev.foxlauncher.feature.home.HomeVisibility
import dev.foxlauncher.feature.home.isDefaultLauncher
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import dev.foxlauncher.feature.home.requestDefaultLauncherRole
import dev.foxlauncher.feature.settings.ButtonAppsScreen
import dev.foxlauncher.feature.settings.GesturesScreen
import dev.foxlauncher.feature.settings.HiddenAppsScreen
import dev.foxlauncher.feature.settings.HomeSettingsScreen
import dev.foxlauncher.feature.settings.IconsScreen
import dev.foxlauncher.feature.settings.SettingsRoute
import dev.foxlauncher.feature.settings.ShizukuScreen
import dev.foxlauncher.feature.widgets.LocalNativeWidgets
import dev.foxlauncher.feature.widgets.NativeWidgetRegistry
import dev.foxlauncher.feature.widgets.WidgetPickerSheet
import dev.foxlauncher.feature.widgets.WidgetPickerViewModel
import dev.foxlauncher.feature.widgets.app.AppWidgetBindFlow
import dev.foxlauncher.feature.widgets.app.AppWidgetBinder
import dev.foxlauncher.feature.widgets.app.AppWidgetPreview
import dev.foxlauncher.feature.widgets.app.AppWidgetReconciler
import dev.foxlauncher.feature.widgets.app.AppWidgetProviders
import dev.foxlauncher.feature.widgets.app.FoxAppWidgetHost
import dev.foxlauncher.feature.widgets.app.LocalAppWidgetHost
import dev.foxlauncher.feature.widgets.app.BindStep
import dev.foxlauncher.feature.widgets.app.PendingAppWidget
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.settings.WallpaperSettingsScreen
import dev.foxlauncher.feature.settings.WidgetBackgroundScreen
import dev.foxlauncher.feature.wallpaper.LiveWallpaper
import dev.foxlauncher.feature.wallpaper.LiveWallpaperPromptDialog
import javax.inject.Inject

/**
 * The one and only activity. It is `singleTask` and handles every configuration change itself, so
 * it must never be recreated: a launcher that restarts looks like a crash. See
 * docs/PLATFORM-NOTES.md section H.
 */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    @Inject lateinit var iconCache: IconCache

    @Inject lateinit var nativeWidgets: NativeWidgetRegistry

    @Inject lateinit var homeVisibility: HomeVisibility

    @Inject lateinit var appWidgetHost: FoxAppWidgetHost

    @Inject lateinit var appWidgetProviders: AppWidgetProviders

    @Inject lateinit var appWidgetBinder: AppWidgetBinder

    @Inject lateinit var appWidgetReconciler: AppWidgetReconciler

    @Inject lateinit var shizukuAccess: ShizukuAccess

    @Inject lateinit var buttonAppLaunches: ButtonAppLaunches

    // The same instance as hiltViewModel() in the composition: same store, same default key.
    private val homeViewModel: HomeViewModel by viewModels()

    private val appWidgetFlow by lazy { AppWidgetBindFlow(appWidgetBinder) }

    /** An app widget waiting on the bind dialog or its configuration activity (task 6.3); survives process death. */
    private var pendingAppWidget: PendingAppWidget? = null

    private val bindAppWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingAppWidget?.let { follow(appWidgetFlow.onBindResult(it, bound = result.resultCode == RESULT_OK)) }
    }

    /** This instance is the home, not the hand-over of an alias intent that finishes in [onCreate] (ADR-19). */
    private var isHome = false

    /** 120 Hz while touched; MIUI would otherwise hold Fox at 60 Hz (see [HighRefreshRate]). */
    private val refreshRate = HighRefreshRate(this)

    /** Bumped by every Home press that reaches us; the home screen closes whatever is open. */
    private var homeKeyPresses by mutableIntStateOf(0)

    /** The latest Home press came while Fox was already in front, not on the way back from an app. */
    private var homeKeyWhileHome by mutableStateOf(false)

    /** A settings screen asked for from outside, such as the system's live wallpaper preview (task 4.7). */
    private var requestedSettings by mutableStateOf<SettingsRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingAppWidget = savedInstanceState?.pendingAppWidget()
        if (intent?.component?.className == WALLPAPER_SETTINGS_ALIAS) {
            // Opened through the alias while no Fox home was running: this instance sits in an ordinary task (seen on
            // API 37). The home instance takes the request; a launcher must not run twice.
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_OPEN_SETTINGS, SettingsRoute.WALLPAPER.name),
            )
            finish()
            return
        }
        isHome = true
        pendingAppWidget?.let { appWidgetBinder.markPending(it.appWidgetId) }
        appWidgetReconciler.start()
        shizukuAccess.start()
        enableEdgeToEdge()
        // A launcher has nowhere to go back to. Unhandled, Back on a root launcher activity makes Android 12+
        // move the home task behind the last app, which then shows (task 3.6). Added first, so the drawer's,
        // a folder's, a popup's and the settings' handlers, registered later while open, all come before it.
        onBackPressedDispatcher.addCallback(this) {}
        if (savedInstanceState == null) requestedSettings = settingsRequestedBy(intent)

        setContent {
            FoxTheme {
                val viewModel: HomeViewModel = hiltViewModel()
                val homeState by viewModel.uiState.collectAsStateWithLifecycle()
                val view = LocalView.current
                val drawerViewModel: DrawerViewModel = hiltViewModel()
                // Settings screens stack over the home, which stays composed under them (see SettingsRoute).
                var settings by rememberSaveable { mutableStateOf(listOf<SettingsRoute>()) }
                // The native widget picker, over the home (task 5.1).
                var widgetPicker by rememberSaveable { mutableStateOf(false) }
                val widgetPickerViewModel: WidgetPickerViewModel = hiltViewModel()
                // One home grid cell, which sizes app widgets in the picker (task 6.2).
                var cellSize by remember { mutableStateOf(DpSize.Unspecified) }
                LaunchedEffect(requestedSettings) {
                    requestedSettings?.let { settings = listOf(it) }
                    requestedSettings = null
                }
                // Over a settings screen the navigation gestures work as in other apps (task 3.8).
                val settingsOpen = settings.isNotEmpty()
                LaunchedEffect(settingsOpen) { homeVisibility.onSettingsOpen(settingsOpen) }
                // Home goes home: it closes any settings screen and the accessibility dialog, as it closes the drawer.
                LaunchedEffect(homeKeyPresses) {
                    if (homeKeyPresses > 0) {
                        settings = emptyList()
                        widgetPicker = false
                        viewModel.dismissAccessibilityPrompt()
                        viewModel.dismissLiveWallpaperPrompt()
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
                CompositionLocalProvider(
                    LocalIconCache provides iconCache,
                    LocalIconGeneration provides iconGeneration,
                    LocalNativeWidgets provides nativeWidgets,
                    LocalAppWidgetHost provides appWidgetHost,
                ) {
                    HomeRoot(
                        isDefaultLauncher = isDefault,
                        onRequestDefaultLauncher = { requestDefaultLauncherRole(roleRequest) },
                    ) {
                        HomeScreen(
                            homeKeyPresses = homeKeyPresses,
                            // With a settings screen open, Home only closes it.
                            homeKeyWhileHome = homeKeyWhileHome && settings.isEmpty(),
                            onCellSizeChanged = { cellSize = it },
                            onPageSettled = { page ->
                                FoxLog.d("Page settled: $page")
                                viewModel.onPageSettled(page)
                            },
                            onResizeWidget = viewModel::onResizeWidget,
                            onDrawerClosed = drawerViewModel::onDrawerClosed,
                            // Over the open drawer too, as over a settings screen.
                            onDrawerOpenChanged = homeVisibility::onDrawerOpen,
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
                        val liveWallpaperPrompt by viewModel.liveWallpaperPrompt.collectAsStateWithLifecycle()
                        if (liveWallpaperPrompt) {
                            LiveWallpaperPromptDialog(
                                onApply = {
                                    viewModel.dismissLiveWallpaperPrompt()
                                    applyLiveWallpaper()
                                },
                                onDismiss = viewModel::dismissLiveWallpaperPrompt,
                            )
                        }
                        val closeSettings = { settings = settings.dropLast(1) }
                        when (settings.lastOrNull()) {
                            SettingsRoute.HOME -> HomeSettingsScreen(
                                onBack = closeSettings,
                                onOpen = { settings = settings + it },
                                onOpenWidgets = {
                                    settings = emptyList()
                                    widgetPicker = true
                                },
                            )
                            SettingsRoute.GESTURES -> GesturesScreen(
                                viewModel = hiltViewModel(),
                                onBack = closeSettings,
                                onOpenAccessibilitySettings = ::openAccessibilitySettings,
                            )
                            SettingsRoute.WALLPAPER -> WallpaperSettingsScreen(
                                viewModel = hiltViewModel(),
                                onBack = closeSettings,
                                onApplyLiveWallpaper = ::applyLiveWallpaper,
                            )
                            SettingsRoute.HIDDEN_APPS -> HiddenAppsScreen(hiltViewModel(), onBack = closeSettings)
                            SettingsRoute.SHIZUKU -> ShizukuScreen(
                                hiltViewModel(),
                                onBack = closeSettings,
                                onOpenButtonApps = { settings = settings + SettingsRoute.BUTTON_APPS },
                            )
                            SettingsRoute.BUTTON_APPS -> ButtonAppsScreen(hiltViewModel(), onBack = closeSettings)
                            SettingsRoute.ICONS -> IconsScreen(hiltViewModel(), onBack = closeSettings)
                            SettingsRoute.WIDGET_BACKGROUND -> WidgetBackgroundScreen(hiltViewModel(), onBack = closeSettings)
                            null -> Unit
                        }
                        if (widgetPicker) {
                            LaunchedEffect(Unit) { widgetPickerViewModel.reload() }
                            val appWidgets by widgetPickerViewModel.appWidgets.collectAsStateWithLifecycle()
                            WidgetPickerSheet(
                                specs = nativeWidgets.all,
                                onPick = { spec ->
                                    widgetPicker = false
                                    viewModel.addNativeWidget(spec.kind)
                                },
                                onDismiss = { widgetPicker = false },
                                appWidgets = appWidgets,
                                cellSize = cellSize,
                                grid = homeState.gridSpec,
                                onPickAppWidget = { option, spans ->
                                    widgetPicker = false
                                    follow(appWidgetFlow.start(option.provider, option.userSerial, spans.default))
                                },
                                appWidgetPreview = { AppWidgetPreview(it, appWidgetProviders) },
                            )
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
        settingsRequestedBy(intent)?.let {
            // Not a Home press, although the hand-over from the alias comes as a HOME intent.
            requestedSettings = it
            return
        }
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

    /** Carries out one step of adding an app widget (task 6.3). */
    private fun follow(step: BindStep) {
        pendingAppWidget = step.pending.takeIf { step is BindStep.AskToBind || step is BindStep.Configure }
        when (step) {
            is BindStep.AskToBind -> try {
                bindAppWidget.launch(appWidgetBinder.bindIntent(step.pending))
            } catch (e: ActivityNotFoundException) {
                FoxLog.e("No screen to allow widgets", e)
                follow(appWidgetFlow.onBindResult(step.pending, bound = false))
            }
            is BindStep.Configure -> try {
                appWidgetHost.startAppWidgetConfigureActivityForResult(this, step.pending.appWidgetId, 0, REQUEST_CONFIGURE_APP_WIDGET, null)
            } catch (e: ActivityNotFoundException) {
                configurationFailed(step.pending, e)
            } catch (e: SecurityException) {
                configurationFailed(step.pending, e)
            }
            is BindStep.Place -> homeViewModel.addAppWidget(step.pending)
            is BindStep.Cancelled -> FoxLog.d("App widget ${step.pending.provider} not added")
        }
    }

    private fun configurationFailed(pending: PendingAppWidget, e: Exception) {
        FoxLog.e("Cannot configure ${pending.provider}", e)
        follow(appWidgetFlow.onConfigureResult(pending, configured = false))
    }

    @Deprecated("Deprecated in Java")
    // AppWidgetHost.startAppWidgetConfigureActivityForResult answers only here; there is no ActivityResult contract for it.
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_APP_WIDGET) {
            pendingAppWidget?.let { follow(appWidgetFlow.onConfigureResult(it, configured = resultCode == RESULT_OK)) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingAppWidget?.let { outState.putPendingAppWidget(it) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        FoxLog.d("Configuration changed: $newConfig")
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> refreshRate.onInteractionStart()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> refreshRate.onInteractionEnd()
        }
        return super.dispatchTouchEvent(event)
    }

    /** The system's preview of Fox's live wallpaper, where the user applies it (PLATFORM-NOTES W). */
    private fun applyLiveWallpaper() {
        try {
            startActivity(LiveWallpaper.previewIntent(this))
        } catch (e: ActivityNotFoundException) {
            FoxLog.e("No live wallpaper preview on this device", e)
        }
    }

    /**
     * The wallpaper settings, when the system's live wallpaper preview opened us through its settings
     * button: `wallpaper.xml` names the `WallpaperSettings` alias of this activity, so Fox keeps one
     * activity. The running home instance gets the alias intent itself; otherwise the hand-over from
     * [onCreate], which names the screen in [EXTRA_OPEN_SETTINGS].
     */
    private fun settingsRequestedBy(intent: Intent?): SettingsRoute? = when {
        intent == null -> null
        intent.component?.className == WALLPAPER_SETTINGS_ALIAS -> SettingsRoute.WALLPAPER
        else -> intent.getStringExtra(EXTRA_OPEN_SETTINGS)?.let { name -> SettingsRoute.entries.firstOrNull { it.name == name } }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            FoxLog.e("No accessibility settings screen", e)
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.onHomeStarted()
        buttonAppLaunches.onHomeShown()
    }

    override fun onResume() {
        super.onResume()
        homeVisibility.onResumed()
    }

    override fun onPause() {
        super.onPause()
        homeVisibility.onPaused()
        refreshRate.releaseNow()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.onHomeStopped()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isHome) appWidgetHost.clearViews()
    }

    private companion object {
        /** The activity-alias in the manifest, and `settingsActivity` in `res/xml/wallpaper.xml`. */
        const val WALLPAPER_SETTINGS_ALIAS = "dev.foxlauncher.WallpaperSettings"
        const val EXTRA_OPEN_SETTINGS = "dev.foxlauncher.extra.OPEN_SETTINGS"

        /** Below the request codes the ActivityResultRegistry generates (0x10000 and up). */
        const val REQUEST_CONFIGURE_APP_WIDGET = 0x0F06

        const val KEY_PENDING_ID = "pending_app_widget_id"
        const val KEY_PENDING_PROVIDER = "pending_app_widget_provider"
        const val KEY_PENDING_SERIAL = "pending_app_widget_serial"
        const val KEY_PENDING_SPAN = "pending_app_widget_span"

        fun Bundle.putPendingAppWidget(pending: PendingAppWidget) {
            putInt(KEY_PENDING_ID, pending.appWidgetId)
            putString(KEY_PENDING_PROVIDER, pending.provider)
            putLong(KEY_PENDING_SERIAL, pending.userSerial)
            putIntArray(KEY_PENDING_SPAN, intArrayOf(pending.span.x, pending.span.y))
        }

        fun Bundle.pendingAppWidget(): PendingAppWidget? {
            val provider = getString(KEY_PENDING_PROVIDER) ?: return null
            val span = getIntArray(KEY_PENDING_SPAN)?.takeIf { it.size == 2 } ?: return null
            return PendingAppWidget(getInt(KEY_PENDING_ID), provider, getLong(KEY_PENDING_SERIAL), Span(span[0], span[1]))
        }
    }
}

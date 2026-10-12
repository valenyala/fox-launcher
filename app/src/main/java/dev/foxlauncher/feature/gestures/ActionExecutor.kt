package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.feature.wallpaper.WallpaperAdvancer
import javax.inject.Inject

/** The global actions Fox can ask the system for (ADR-4). */
enum class SystemAction { NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, RECENTS, POWER_MENU, SCREENSHOT }

/** System actions through `FoxAccessibilityService.performGlobalAction`. */
interface SystemActions {
    /** The user has turned the service on in the system's accessibility settings. */
    val isEnabled: Boolean

    /** False when the service is not connected (yet) or the system refused. */
    fun perform(action: SystemAction): Boolean
}

/** Starts apps and shortcuts the way home items do: by component or shortcut id, in a profile. */
interface AppLauncher {
    fun launchApp(component: String, userSerial: Long): Boolean
    fun startShortcut(packageName: String, shortcutId: String, userSerial: Long): Boolean
}

/** What the home screen and its activity do for actions that stay inside Fox (task 3.4). */
class ExecContext(
    val openDrawer: () -> Unit,
    val openSearch: () -> Unit,
    val scrollToDefaultPage: () -> Unit,
    val openPageOverview: () -> Unit,
    val openHomeSettings: () -> Unit,
)

enum class ActionResult {
    DONE,

    /** [GestureAction.None]: the gesture is mapped to nothing. */
    NOTHING,

    /** A system action, but the accessibility service is off: the UI shows its disclosure. */
    NEEDS_ACCESSIBILITY,

    /** The app or shortcut is gone, or the system refused. */
    FAILED,
}

/**
 * Runs a [GestureAction] (task 3.4). UI-agnostic: whatever touches the home screen goes through
 * the [ExecContext] its caller passes, system actions through [SystemActions], apps through
 * [AppLauncher], the wallpaper through [WallpaperAdvancer].
 */
class ActionExecutor @Inject constructor(
    private val systemActions: SystemActions,
    private val appLauncher: AppLauncher,
    private val wallpaper: WallpaperAdvancer,
) {

    fun execute(action: GestureAction, context: ExecContext): ActionResult = when (action) {
        GestureAction.None -> ActionResult.NOTHING
        GestureAction.OpenDrawer -> done(context.openDrawer)
        GestureAction.OpenSearch -> done(context.openSearch)
        GestureAction.ScrollToDefaultPage -> done(context.scrollToDefaultPage)
        GestureAction.PageOverview -> done(context.openPageOverview)
        GestureAction.HomeSettings -> done(context.openHomeSettings)
        GestureAction.NextWallpaper -> done { wallpaper.advance(forward = true) }
        GestureAction.PreviousWallpaper -> done { wallpaper.advance(forward = false) }
        GestureAction.Notifications -> system(SystemAction.NOTIFICATIONS)
        GestureAction.QuickSettings -> system(SystemAction.QUICK_SETTINGS)
        GestureAction.LockScreen -> system(SystemAction.LOCK_SCREEN)
        GestureAction.Recents -> system(SystemAction.RECENTS)
        GestureAction.PowerMenu -> system(SystemAction.POWER_MENU)
        GestureAction.Screenshot -> system(SystemAction.SCREENSHOT)
        is GestureAction.OpenApp -> appLauncher.launchApp(action.component, action.userSerial).toResult()
        is GestureAction.OpenShortcut ->
            appLauncher.startShortcut(action.packageName, action.shortcutId, action.userSerial).toResult()
    }

    /**
     * Prompts only when the service is off: right after Fox restarts, an enabled service takes a
     * moment to reconnect, and a gesture then simply does nothing.
     */
    private fun system(action: SystemAction): ActionResult = when {
        !systemActions.isEnabled -> ActionResult.NEEDS_ACCESSIBILITY
        systemActions.perform(action) -> ActionResult.DONE
        else -> ActionResult.FAILED
    }

    private inline fun done(run: () -> Unit): ActionResult {
        run()
        return ActionResult.DONE
    }

    private fun Boolean.toResult() = if (this) ActionResult.DONE else ActionResult.FAILED
}

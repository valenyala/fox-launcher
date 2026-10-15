package dev.foxlauncher.feature.gestures

import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.feature.wallpaper.WallpaperAdvancer
import javax.inject.Inject

/** The global actions Fox can ask the system for (ADR-4). */
enum class SystemAction { NOTIFICATIONS, QUICK_SETTINGS, LOCK_SCREEN, RECENTS, POWER_MENU, SCREENSHOT }

/** Fox's accessibility service as the system has it. */
enum class AccessibilityStatus {
    /** Running for Fox: system actions work. */
    ON,

    /**
     * Turned on in the settings but not running. The system stopped it (MIUI's "clear recent apps"
     * kills Fox, home screen or not) and will not start it again until the user turns it off and
     * on (PLATFORM-NOTES G). Just after Fox starts, a service about to reconnect also looks so.
     */
    STOPPED,

    /** Not turned on. */
    OFF,
}

/** System actions through `FoxAccessibilityService.performGlobalAction`. */
interface SystemActions {
    val status: AccessibilityStatus

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

    /** A system action, but the service is on and stopped: the UI says to turn it off and on. */
    ACCESSIBILITY_STOPPED,

    /** A wallpaper action, but Fox's live wallpaper is not applied: the UI offers it (task 4.5). */
    NEEDS_LIVE_WALLPAPER,

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
        GestureAction.NextWallpaper -> wallpaperResult(wallpaper.advance(forward = true))
        GestureAction.PreviousWallpaper -> wallpaperResult(wallpaper.advance(forward = false))
        GestureAction.Notifications -> perform(SystemAction.NOTIFICATIONS)
        GestureAction.QuickSettings -> perform(SystemAction.QUICK_SETTINGS)
        GestureAction.LockScreen -> perform(SystemAction.LOCK_SCREEN)
        GestureAction.Recents -> perform(SystemAction.RECENTS)
        GestureAction.PowerMenu -> perform(SystemAction.POWER_MENU)
        GestureAction.Screenshot -> perform(SystemAction.SCREENSHOT)
        is GestureAction.OpenApp -> appLauncher.launchApp(action.component, action.userSerial).toResult()
        is GestureAction.OpenShortcut ->
            appLauncher.startShortcut(action.packageName, action.shortcutId, action.userSerial).toResult()
    }

    /** A system action alone, as the Lock screen widget asks for one (task 5.7). */
    fun perform(action: SystemAction): ActionResult = when (systemActions.status) {
        AccessibilityStatus.OFF -> ActionResult.NEEDS_ACCESSIBILITY
        AccessibilityStatus.STOPPED -> ActionResult.ACCESSIBILITY_STOPPED
        AccessibilityStatus.ON -> if (systemActions.perform(action)) ActionResult.DONE else ActionResult.FAILED
    }

    private inline fun done(run: () -> Unit): ActionResult {
        run()
        return ActionResult.DONE
    }

    private fun Boolean.toResult() = if (this) ActionResult.DONE else ActionResult.FAILED

    private fun wallpaperResult(advanced: Boolean) = if (advanced) ActionResult.DONE else ActionResult.NEEDS_LIVE_WALLPAPER
}

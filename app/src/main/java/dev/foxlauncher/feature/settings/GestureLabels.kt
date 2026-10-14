package dev.foxlauncher.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.GestureAction
import dev.foxlauncher.core.model.GestureType

@StringRes
fun GestureType.labelRes(): Int = when (this) {
    GestureType.SWIPE_UP -> R.string.gesture_swipe_up
    GestureType.SWIPE_DOWN -> R.string.gesture_swipe_down
    GestureType.SWIPE_LEFT -> R.string.gesture_swipe_left
    GestureType.SWIPE_RIGHT -> R.string.gesture_swipe_right
    GestureType.SWIPE_UP_2F -> R.string.gesture_swipe_up_2f
    GestureType.SWIPE_DOWN_2F -> R.string.gesture_swipe_down_2f
    GestureType.SWIPE_LEFT_2F -> R.string.gesture_swipe_left_2f
    GestureType.SWIPE_RIGHT_2F -> R.string.gesture_swipe_right_2f
    GestureType.DOUBLE_TAP -> R.string.gesture_double_tap
    GestureType.LONG_PRESS_EMPTY -> R.string.gesture_long_press_empty
    GestureType.PINCH_IN -> R.string.gesture_pinch_in
    GestureType.PINCH_OUT -> R.string.gesture_pinch_out
    GestureType.DOCK_SWIPE_UP -> R.string.gesture_dock_swipe_up
    GestureType.DOCK_SWIPE_DOWN -> R.string.gesture_dock_swipe_down
    GestureType.HOME_KEY -> R.string.gesture_home_key
}

/** The actions the picker lists directly; apps and shortcuts follow as their own entries. */
val PICKABLE_ACTIONS: List<GestureAction> = listOf(
    GestureAction.None,
    GestureAction.OpenDrawer,
    GestureAction.OpenSearch,
    GestureAction.Notifications,
    GestureAction.QuickSettings,
    GestureAction.LockScreen,
    GestureAction.Recents,
    GestureAction.PowerMenu,
    GestureAction.Screenshot,
    GestureAction.NextWallpaper,
    GestureAction.PreviousWallpaper,
    GestureAction.HomeSettings,
    GestureAction.PageOverview,
    GestureAction.ScrollToDefaultPage,
)

/** A human name for [action]; apps are named from [apps], keyed by `AppEntry.key`. */
@Composable
fun actionLabel(action: GestureAction, apps: Map<String, AppEntry>): String = when (action) {
    GestureAction.None -> stringResource(R.string.action_none)
    GestureAction.OpenDrawer -> stringResource(R.string.action_open_drawer)
    GestureAction.OpenSearch -> stringResource(R.string.action_open_search)
    GestureAction.Notifications -> stringResource(R.string.action_notifications)
    GestureAction.QuickSettings -> stringResource(R.string.action_quick_settings)
    GestureAction.LockScreen -> stringResource(R.string.action_lock_screen)
    GestureAction.Recents -> stringResource(R.string.action_recents)
    GestureAction.PowerMenu -> stringResource(R.string.action_power_menu)
    GestureAction.Screenshot -> stringResource(R.string.action_screenshot)
    GestureAction.NextWallpaper -> stringResource(R.string.action_next_wallpaper)
    GestureAction.PreviousWallpaper -> stringResource(R.string.action_previous_wallpaper)
    GestureAction.HomeSettings -> stringResource(R.string.action_home_settings)
    GestureAction.PageOverview -> stringResource(R.string.action_page_overview)
    GestureAction.ScrollToDefaultPage -> stringResource(R.string.action_scroll_to_default_page)
    is GestureAction.OpenApp -> apps["${action.component}|${action.userSerial}"]
        ?.let { stringResource(R.string.action_open_app, it.label) }
        ?: stringResource(R.string.action_missing_app)
    is GestureAction.OpenShortcut -> stringResource(
        R.string.action_open_shortcut,
        action.label.ifBlank { apps.values.firstOrNull { it.packageName == action.packageName }?.label ?: action.shortcutId },
    )
    is GestureAction.Macro -> stringResource(R.string.action_macro, action.steps.map { actionLabel(it.action, apps) }.joinToString(" → "))
}

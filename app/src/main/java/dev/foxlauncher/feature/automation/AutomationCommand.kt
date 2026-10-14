package dev.foxlauncher.feature.automation

/**
 * What other apps may ask Fox to do (task 9.4, ADR-22), by broadcast action. The actions are the
 * public API, documented in the README: never rename one.
 */
enum class AutomationCommand(val action: String) {
    NEXT_WALLPAPER("dev.foxlauncher.NEXT_WALLPAPER"),
    PREV_WALLPAPER("dev.foxlauncher.PREV_WALLPAPER"),
    BOOST("dev.foxlauncher.BOOST"),
    ;

    companion object {
        /** The command for a broadcast's [action], or null when it is none of Fox's. */
        fun of(action: String?): AutomationCommand? = entries.firstOrNull { it.action == action }
    }
}

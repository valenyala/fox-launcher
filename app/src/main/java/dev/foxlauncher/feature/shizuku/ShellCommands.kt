package dev.foxlauncher.feature.shizuku

/**
 * The only commands Fox's shell service runs (tasks 9.1 and 9.6, ADR-21, ADR-23), each as separate
 * arguments that no shell parses. Pure, so they are unit tested.
 */
object ShellCommands {

    /** MIUI's switch between buttons (0) and full-screen gestures (1). */
    const val MIUI_FULL_SCREEN_GESTURES = "force_fsg_nav_bar"

    /** `Settings.Secure.NAVIGATION_MODE` (hidden): 0 three buttons, 1 two buttons, 2 gestures. */
    const val NAVIGATION_MODE = "navigation_mode"
    const val NAVIGATION_MODE_GESTURAL = 2

    const val OVERLAY_GESTURAL = "com.android.internal.systemui.navbar.gestural"
    const val OVERLAY_THREE_BUTTON = "com.android.internal.systemui.navbar.threebutton"

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    /** Closes [packageName]'s background processes (`am kill`), or null for anything that is not a package name. */
    fun killBackgroundProcesses(packageName: String): List<String>? =
        if (PACKAGE_NAME.matches(packageName)) listOf("cmd", "activity", "kill", "--user", "current", packageName) else null

    /**
     * Hides the navigation buttons by turning on full-screen gestures, or brings them back: MIUI's own
     * setting on [miui] phones, which ignore Android's navigation overlays; Android's gesture or
     * three-button overlay elsewhere.
     */
    fun navigationButtons(miui: Boolean, hidden: Boolean): List<String> = if (miui) {
        listOf("settings", "put", "global", MIUI_FULL_SCREEN_GESTURES, if (hidden) "1" else "0")
    } else {
        listOf("cmd", "overlay", "enable-exclusive", "--category", if (hidden) OVERLAY_GESTURAL else OVERLAY_THREE_BUTTON)
    }

    /**
     * Whether the buttons are hidden, from what Fox could read: [miuiSetting] on [miui] phones,
     * [navigationMode] elsewhere. Null when the one that counts could not be read.
     */
    fun navigationButtonsHidden(miui: Boolean, miuiSetting: Int?, navigationMode: Int?): Boolean? =
        if (miui) miuiSetting?.let { it == 1 } else navigationMode?.let { it == NAVIGATION_MODE_GESTURAL }
}

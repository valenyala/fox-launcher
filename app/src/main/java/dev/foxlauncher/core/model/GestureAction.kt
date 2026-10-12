package dev.foxlauncher.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a [GestureType] does (task 3.1). Persisted as JSON with a `type` discriminator: the
 * [SerialName]s are the stored format, so never change one. Apps and shortcuts are referenced the
 * way home items are: flattened component or package + id, and the profile's user serial.
 */
@Immutable
@Serializable
sealed interface GestureAction {

    /** System actions run through `FoxAccessibilityService.performGlobalAction` (ADR-4), so they need it enabled. */
    val requiresAccessibility: Boolean get() = false

    @Serializable @SerialName("none")
    data object None : GestureAction

    @Serializable @SerialName("open_drawer")
    data object OpenDrawer : GestureAction

    @Serializable @SerialName("open_search")
    data object OpenSearch : GestureAction

    @Serializable @SerialName("notifications")
    data object Notifications : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("quick_settings")
    data object QuickSettings : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("lock_screen")
    data object LockScreen : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("recents")
    data object Recents : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("power_menu")
    data object PowerMenu : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("screenshot")
    data object Screenshot : GestureAction {
        override val requiresAccessibility: Boolean get() = true
    }

    @Serializable @SerialName("next_wallpaper")
    data object NextWallpaper : GestureAction

    @Serializable @SerialName("previous_wallpaper")
    data object PreviousWallpaper : GestureAction

    @Serializable @SerialName("home_settings")
    data object HomeSettings : GestureAction

    @Serializable @SerialName("page_overview")
    data object PageOverview : GestureAction

    @Serializable @SerialName("scroll_to_default_page")
    data object ScrollToDefaultPage : GestureAction

    /** Launches the app [component] (`ComponentName.flattenToString()`) in the profile [userSerial]. */
    @Serializable @SerialName("open_app")
    data class OpenApp(val component: String, val userSerial: Long) : GestureAction

    /** Starts the pinned or published shortcut [shortcutId] of [packageName] in the profile [userSerial]. */
    @Serializable @SerialName("open_shortcut")
    data class OpenShortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : GestureAction
}

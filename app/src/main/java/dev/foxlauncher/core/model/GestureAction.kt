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

    /**
     * Starts the pinned or published shortcut [shortcutId] of [packageName] in the profile
     * [userSerial]. [label] is the shortcut's name when it was chosen, for the settings screen.
     */
    @Serializable @SerialName("open_shortcut")
    data class OpenShortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val label: String = "",
    ) : GestureAction

    /**
     * Runs [steps] in order, each after its own delay (task 9.3). A step is never a macro itself;
     * [Macros.sanitize] drops one that is, and caps the number of steps and the delays.
     */
    @Serializable @SerialName("macro")
    data class Macro(val steps: List<MacroStep>) : GestureAction {
        override val requiresAccessibility: Boolean get() = steps.any { it.action.requiresAccessibility }
    }
}

/** One step of a [GestureAction.Macro]: wait [delayMs], then run [action]. */
@Immutable
@Serializable
data class MacroStep(val action: GestureAction, val delayMs: Long = 0)

/** What a macro may hold (task 9.3). Pure, so it is unit tested. */
object Macros {

    const val MAX_STEPS = 8
    const val MAX_DELAY_MS = 10_000L

    /** The delays a step can wait, in the order the settings cycle through them. */
    val DELAYS_MS: List<Long> = listOf(0L, 500L, 1_000L, 2_000L, 5_000L)

    /** [macro] as it may run: no nested macros and no empty steps, at most [MAX_STEPS], delays within 0..[MAX_DELAY_MS]. */
    fun sanitize(macro: GestureAction.Macro): GestureAction.Macro = GestureAction.Macro(
        macro.steps
            .filter { it.action !is GestureAction.Macro && it.action != GestureAction.None }
            .take(MAX_STEPS)
            .map { it.copy(delayMs = it.delayMs.coerceIn(0L, MAX_DELAY_MS)) },
    )

    /** The delay after [delayMs] in [DELAYS_MS], back to the first after the last. */
    fun nextDelay(delayMs: Long): Long = DELAYS_MS.getOrNull(DELAYS_MS.indexOf(delayMs) + 1) ?: DELAYS_MS.first()
}

package dev.foxlauncher.feature.memory

/**
 * Packages a boost must never close (ADR-5): Fox itself, the app in front, the keyboard, the phone
 * and SMS apps, accessibility services and the live wallpaper. Null when not known.
 */
data class ProtectedApps(
    val self: String,
    val foreground: String? = null,
    val inputMethod: String? = null,
    val dialer: String? = null,
    val sms: String? = null,
    val accessibility: Set<String> = emptySet(),
    val liveWallpaper: String? = null,
) {
    val packages: Set<String> get() = setOfNotNull(self, foreground, inputMethod, dialer, sms, liveWallpaper) + accessibility
}

/** Which apps the Android 13-and-lower boost closes (task 5.4, ADR-5). Pure, so it is unit tested. */
object BoostTargets {

    /** The launchable apps that are neither system apps nor [protectedApps], each once, in a stable order. */
    fun choose(launchable: Collection<String>, systemPackages: Set<String>, protectedApps: ProtectedApps): List<String> {
        val protectedPackages = protectedApps.packages
        return launchable.distinct().filter { it !in systemPackages && it !in protectedPackages }.sorted()
    }

    /** The package of a flattened component (`pkg/.Cls`, as `DEFAULT_INPUT_METHOD` holds), or null. */
    fun packageOfComponent(flattened: String?): String? =
        flattened?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() && flattened.contains('/') }

    /** The packages in `ENABLED_ACCESSIBILITY_SERVICES`: components separated by colons. */
    fun accessibilityPackages(setting: String?): Set<String> =
        setting.orEmpty().split(':').mapNotNull(::packageOfComponent).toSet()
}

/** What a boost runs on this Android version (task 5.4). Pure, so the choice is unit tested. */
object BoostStrategies {

    /** Trimming Fox's own memory always; closing other apps only where Android still allows it (≤ 13). */
    fun <S> forSdk(sdkInt: Int, selfTrim: S, closeOthers: S): List<S> =
        if (BoostCapability.canCloseOtherApps(sdkInt)) listOf(selfTrim, closeOthers) else listOf(selfTrim)
}

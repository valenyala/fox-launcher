package dev.foxlauncher.feature.gestures.navigation

/**
 * Apps Fox opens with the navigation buttons while they are hidden everywhere else (task 9.7,
 * ADR-24), kept as `AppEntry.key` (`component|serial`). Before Fox opens one, it shows the buttons
 * if they are hidden; back home, it hides them again if it was Fox that showed them. Pure, so it
 * is unit tested.
 */
object ButtonApps {

    /** Whether [packageName] in the profile [userSerial] is listed: by package, so its shortcuts count too. */
    fun isListed(keys: Set<String>, packageName: String, userSerial: Long): Boolean = keys.any { key ->
        key.substringAfterLast('|').toLongOrNull() == userSerial &&
            key.substringBeforeLast('|').substringBefore('/') == packageName
    }

    /** Before opening a listed app: show them only when they are hidden now (unknown counts as not hidden). */
    fun showBeforeOpening(listed: Boolean, hiddenNow: Boolean?): Boolean = listed && hiddenNow == true

    /** Back home: hide them only when Fox showed them and they are not hidden already. */
    fun hideOnHome(shownByFox: Boolean, hiddenNow: Boolean?): Boolean = shownByFox && hiddenNow != true
}

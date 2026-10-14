package dev.foxlauncher.feature.gestures.navigation

/**
 * Apps Fox opens without navigation buttons (task 9.7, ADR-24), kept as `AppEntry.key`
 * (`component|serial`). Before Fox opens one, it hides the buttons if they show; back home, it
 * shows them again if it was Fox that hid them. Pure, so it is unit tested.
 */
object ButtonlessApps {

    /** Whether [packageName] in the profile [userSerial] is listed: by package, so its shortcuts count too. */
    fun isListed(keys: Set<String>, packageName: String, userSerial: Long): Boolean = keys.any { key ->
        key.substringAfterLast('|').toLongOrNull() == userSerial &&
            key.substringBeforeLast('|').substringBefore('/') == packageName
    }

    /** Before opening a listed app: hide them only when they show now (unknown counts as not showing). */
    fun hideBeforeOpening(listed: Boolean, hiddenNow: Boolean?): Boolean = listed && hiddenNow == false

    /** Back home: show them only when Fox hid them and they are not showing already. */
    fun showOnHome(hiddenByFox: Boolean, hiddenNow: Boolean?): Boolean = hiddenByFox && hiddenNow != false
}

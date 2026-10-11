package dev.foxlauncher.core.model

import androidx.compose.runtime.Immutable

/**
 * One of an app's shortcuts (static from its manifest, or dynamic), as `LauncherApps` reports it.
 * Identified by [packageName], [id] and the profile's [userSerial], like `HomeItem.Shortcut`.
 */
@Immutable
data class AppShortcut(
    val packageName: String,
    val id: String,
    val userSerial: Long,
    val shortLabel: String,
    val longLabel: String?,
) {
    /** Same shape as `HomeItem.Shortcut.shortcutKey`, to find a shortcut item's icon. */
    val key: String get() = keyOf(packageName, id, userSerial)

    companion object {
        fun keyOf(packageName: String, id: String, userSerial: Long): String = "$packageName|$id|$userSerial"
    }
}

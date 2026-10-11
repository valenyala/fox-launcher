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
)

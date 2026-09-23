package dev.foxlauncher.core.model

import androidx.compose.runtime.Immutable

/**
 * One launchable activity for one user profile. This is the identity every other feature uses to
 * refer to an app: [component] is `ComponentName.flattenToString()` and [userSerial] is
 * `UserManager.getSerialNumberForUser`, never a `UserHandle` (see PLATFORM-NOTES A).
 */
@Immutable
data class AppEntry(
    val component: String,
    val userSerial: Long,
    val label: String,
    val packageName: String,
    val isWorkProfile: Boolean,
    val isSuspended: Boolean,
    val isAvailable: Boolean,
    val versionCode: Long,
) {
    /** Stable key for lists, caches and persisted sets: `component|serial`. */
    val key: String get() = "$component|$userSerial"
}

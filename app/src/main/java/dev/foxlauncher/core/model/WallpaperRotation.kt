package dev.foxlauncher.core.model

/** The order wallpapers rotate in (task 4.2). Stored by name, so never rename an entry. */
enum class PlaylistMode { SEQUENTIAL, SHUFFLE }

/**
 * What the rotation remembers between changes (task 4.2): its [mode], the shuffle [seed] (null
 * until one is first chosen), and the entry ids shown, oldest first, the current one last.
 */
data class PlaylistState(
    val mode: PlaylistMode = PlaylistMode.SEQUENTIAL,
    val seed: Long? = null,
    val history: List<Long> = emptyList(),
)

/** What changes the wallpaper besides the user (task 4.4). [intervalMinutes] 0 means no interval. */
data class RotationTriggers(
    val intervalMinutes: Int = 0,
    val onUnlock: Boolean = false,
    val onPageChange: Boolean = false,
)

/** Why the rotation was asked to move on (task 4.4). */
enum class RotationTrigger { INTERVAL, UNLOCK, PAGE_CHANGE, GESTURE, MANUAL }

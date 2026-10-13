package dev.foxlauncher.feature.wallpaper

/** A system wallpaper Fox can set as a still image (tasks 4.5, 4.6). */
enum class StaticTarget { HOME, LOCK }

/** Which wallpapers a change of wallpaper sets as still images. Pure. */
object StaticTargets {

    /**
     * The home screen only while Fox's live wallpaper is not the system's (otherwise it draws the
     * change itself, with a crossfade); the lock screen whenever the user asked for it (task 4.6).
     * [lockOnly]: the home screen already shows the active entry, only the lock screen catches up.
     */
    fun of(foxLiveActive: Boolean, changeLockScreen: Boolean, lockOnly: Boolean = false): Set<StaticTarget> = buildSet {
        if (!foxLiveActive && !lockOnly) add(StaticTarget.HOME)
        if (changeLockScreen) add(StaticTarget.LOCK)
    }
}

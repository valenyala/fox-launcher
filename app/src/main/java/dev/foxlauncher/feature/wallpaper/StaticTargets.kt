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

    /**
     * Whether setting [targets] waits until the screen goes off. Any still image lets SystemUI apply
     * the new Material You colours at once instead of at screen off, and every app, Fox included,
     * then restyles in front of the user (PLATFORM-NOTES W). The lock screen alone is out of sight
     * until then; a home-screen image is not.
     */
    fun waitsForScreenOff(targets: Set<StaticTarget>): Boolean = targets == setOf(StaticTarget.LOCK)

    /** Whether the lock screen, showing [lockEntry], is behind the home screen's [showing] entry. */
    fun lockScreenBehind(changeLockScreen: Boolean, lockEntry: Long?, showing: Long?): Boolean =
        changeLockScreen && showing != null && lockEntry != showing
}

package dev.foxlauncher.feature.wallpaper

/** A system wallpaper Fox can set as a still image (tasks 4.5, 4.6). */
enum class StaticTarget { HOME, LOCK }

/** When the lock screen's still image needs setting. Pure. */
object StaticTargets {

    /**
     * Whether the lock screen, showing [lockEntry], is behind the entry it is to show, [wanted]
     * (null: it is to show nothing of Fox's).
     */
    fun lockScreenBehind(lockEntry: Long?, wanted: Long?): Boolean = wanted != null && lockEntry != wanted
}

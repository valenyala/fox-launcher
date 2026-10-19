package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.WallpaperTarget

/** Which images each screen rotates over, from where the user put them. Pure. */
object EntryTargets {

    /**
     * The home screen's images, [entries] being (id, target) in rotation order. With none aimed at
     * the home screen it takes them all, so the live wallpaper never goes blank.
     */
    fun home(entries: List<Pair<Long, WallpaperTarget>>): List<Long> =
        entries.filter { it.second.onHome }.ifEmpty { entries }.map { it.first }

    /** The lock screen's images; empty when none is aimed at it, and the lock screen is then left alone. */
    fun lock(entries: List<Pair<Long, WallpaperTarget>>): List<Long> =
        entries.filter { it.second.onLock }.map { it.first }
}

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

    /** The picture the live wallpaper draws: [id], and whether it is the lock screen's ([lockPicture]). */
    data class Showing(val id: Long?, val lockPicture: Boolean)

    /**
     * What the live wallpaper draws. While the lock screen is up and shows Fox's live wallpaper
     * ([foxOnLock]) it is the lock screen's picture, when some image is aimed there; otherwise the home
     * screen's. [homeActive] and [lockActive] are the rotations' current entries.
     */
    fun showing(
        locked: Boolean,
        foxOnLock: Boolean,
        homeActive: Long?,
        lockActive: Long?,
        entries: List<Pair<Long, WallpaperTarget>>,
    ): Showing {
        if (locked && foxOnLock) {
            WallpaperPlaylist.showing(lockActive, lock(entries))?.let { return Showing(it, lockPicture = true) }
        }
        return Showing(WallpaperPlaylist.showing(homeActive, home(entries)), lockPicture = false)
    }

    /** The lock screen's images; empty when none is aimed at it, and the lock screen is then left alone. */
    fun lock(entries: List<Pair<Long, WallpaperTarget>>): List<Long> =
        entries.filter { it.second.onLock }.map { it.first }
}

package dev.foxlauncher.feature.wallpaper

/** Moves the wallpaper rotation one image on or back; the gesture actions use it (task 3.4). */
interface WallpaperAdvancer {
    /**
     * False, changing nothing, while Fox's live wallpaper is not applied: a gesture must not replace
     * the user's own wallpaper with a static image (task 4.5); the caller offers the live wallpaper.
     */
    fun advance(forward: Boolean): Boolean
}

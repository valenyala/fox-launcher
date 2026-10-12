package dev.foxlauncher.feature.wallpaper

/** Moves the wallpaper rotation one image on or back; the gesture actions use it (task 3.4). */
interface WallpaperAdvancer {
    fun advance(forward: Boolean)
}

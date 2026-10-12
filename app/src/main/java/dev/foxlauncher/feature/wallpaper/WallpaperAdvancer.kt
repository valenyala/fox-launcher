package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.common.FoxLog
import javax.inject.Inject

/** Moves the wallpaper rotation one image on or back; the gesture actions use it (task 3.4). */
interface WallpaperAdvancer {
    fun advance(forward: Boolean)
}

/** TODO(phase4): `RotationScheduler` replaces this; until then there is no rotation to advance. */
class NoOpWallpaperAdvancer @Inject constructor() : WallpaperAdvancer {
    override fun advance(forward: Boolean) {
        FoxLog.d("Wallpaper ${if (forward) "next" else "previous"}: no rotation before phase 4")
    }
}

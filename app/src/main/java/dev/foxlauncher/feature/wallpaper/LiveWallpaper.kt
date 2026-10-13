package dev.foxlauncher.feature.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.foxlauncher.feature.wallpaper.engine.FoxWallpaperService

/** Whether Fox's live wallpaper is the system's, and how to offer it (PLATFORM-NOTES W). */
object LiveWallpaper {

    fun isFoxActive(context: Context): Boolean =
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)

    /** The system's preview of Fox's live wallpaper, where the user applies it. */
    fun previewIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))

    private fun component(context: Context) = ComponentName(context, FoxWallpaperService::class.java)
}

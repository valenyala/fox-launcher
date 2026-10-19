package dev.foxlauncher.feature.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.foxlauncher.feature.wallpaper.engine.FoxWallpaperService

/** Whether Fox's live wallpaper is the system's, and how to offer it (PLATFORM-NOTES W). */
object LiveWallpaper {

    fun isFoxActive(context: Context): Boolean =
        WallpaperManager.getInstance(context).wallpaperInfo?.component == component(context)

    /**
     * Whether the lock screen shows Fox's live wallpaper: applied to the lock screen too, with no still
     * image of its own (`getWallpaperId` is -1 for a lock screen that follows the system's wallpaper), or,
     * from Android 14, as the lock screen's own live wallpaper.
     */
    fun isFoxOnLock(context: Context): Boolean {
        val manager = WallpaperManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == component(context)
        ) {
            return true
        }
        return isFoxActive(context) && manager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0
    }

    /** The system's preview of Fox's live wallpaper, where the user applies it. */
    fun previewIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))

    private fun component(context: Context) = ComponentName(context, FoxWallpaperService::class.java)
}

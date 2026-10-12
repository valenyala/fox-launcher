package dev.foxlauncher.feature.wallpaper.source

import android.content.ContentResolver
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.R
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperSourceEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import javax.inject.Inject
import javax.inject.Singleton

/** The wallpaper sources (task 4.1). Adding folders and pictures arrives with the settings screen (task 4.7). */
@Singleton
class WallpaperSources @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WallpaperDao,
) {
    /**
     * Makes sure the built-in source exists and lists exactly the wallpapers this version ships, so
     * rotation has images with no setup. Their URIs name the resource, which stays valid across builds.
     */
    suspend fun ensureBuiltIn() {
        val source = dao.getSourceOfType(WallpaperSourceType.BUILT_IN)
        val sourceId = source?.id ?: dao.insertSource(
            WallpaperSourceEntity(type = WallpaperSourceType.BUILT_IN, displayName = BUILT_IN_NAME),
        )
        val shipped = BUILT_IN.map { (resId, name) ->
            val entry = context.resources.getResourceEntryName(resId)
            FoundImage(uri = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/$entry", displayName = name)
        }
        val diff = diffScan(dao.getEntries(sourceId), shipped)
        if (!diff.isEmpty) dao.applyScan(sourceId, diff.added.map { it.toEntry(sourceId) }, diff.changed, diff.removedIds)
    }

    private companion object {
        const val BUILT_IN_NAME = "Fox"
        val BUILT_IN = listOf(
            R.raw.wallpaper_dawn to "Dawn",
            R.raw.wallpaper_dusk to "Dusk",
            R.raw.wallpaper_forest to "Forest",
        )
    }
}

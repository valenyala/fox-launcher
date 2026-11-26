package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import dev.foxlauncher.core.data.settings.FoxSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The pictures the rotation walks and the settings list (see [WallpaperDao.observeRotationEntries]),
 * private ones only while the "private wallpapers" switch is on. The one place that applies the switch.
 */
@Singleton
class RotationEntries @Inject constructor(private val dao: WallpaperDao, private val settings: FoxSettings) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: Flow<List<WallpaperEntryEntity>> =
        settings.showPrivateWallpapers.distinctUntilChanged().flatMapLatest { dao.observeRotationEntries(it) }
}

package dev.foxlauncher.feature.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.data.db.FoxDatabase
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import dev.foxlauncher.core.data.db.WallpaperSourceEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import dev.foxlauncher.feature.wallpaper.engine.WallpaperLoader
import dev.foxlauncher.feature.wallpaper.source.WallpaperSources
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The static wallpaper fallback (task 4.5) and the lock screen (task 4.6) on the real WallpaperManager. It replaces the device's
 * wallpaper, so it runs only on an emulator, never on a personal phone.
 */
@RunWith(AndroidJUnit4::class)
class StaticWallpaperApplierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FoxDatabase
    private lateinit var applier: StaticWallpaperApplier

    @Before
    fun setUp() {
        assumeTrue("replaces the wallpaper: emulators only", Build.MODEL.startsWith("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        db = Room.inMemoryDatabaseBuilder(context, FoxDatabase::class.java).build()
        applier = StaticWallpaperApplier(
            context,
            db.wallpaperDao(),
            WallpaperLoader(context),
            WallpaperSources(context, db.wallpaperDao(), Dispatchers.IO),
            Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun anEntryBecomesTheStaticHomeWallpaper() = runBlocking {
        val manager = WallpaperManager.getInstance(context)
        val before = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val id = insertEntry("android.resource://${context.packageName}/raw/wallpaper_forest")

        assertTrue(applier.apply(id, setOf(StaticTarget.HOME)))

        assertNull(manager.wallpaperInfo, "a static wallpaper has no live wallpaper info")
        assertNotEquals(before, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
    }

    @Test
    fun anEntryBecomesTheLockWallpaperAndTheHomeScreenStays() = runBlocking {
        val manager = WallpaperManager.getInstance(context)
        val homeBefore = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockBefore = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val id = insertEntry("android.resource://${context.packageName}/raw/wallpaper_dusk")

        assertTrue(applier.apply(id, setOf(StaticTarget.LOCK)))

        assertNotEquals(lockBefore, manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(homeBefore, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
    }

    @Test
    fun aHomeChangeLeavesTheLockImageAlone() = runBlocking {
        val manager = WallpaperManager.getInstance(context)
        assertTrue(applier.apply(insertEntry("android.resource://${context.packageName}/raw/wallpaper_dawn"), setOf(StaticTarget.LOCK)))
        val lock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)

        assertTrue(applier.apply(insertEntry("android.resource://${context.packageName}/raw/wallpaper_forest"), setOf(StaticTarget.HOME)))

        assertEquals(lock, manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
    }

    @Test
    fun anUnreadableEntryChangesNothingAndCountsAFailure() = runBlocking {
        val id = insertEntry("content://dev.foxlauncher.missing/nothing.jpg")

        assertFalse(applier.apply(id, setOf(StaticTarget.HOME, StaticTarget.LOCK)))

        assertEquals(1, db.wallpaperDao().getEntry(id)?.failedCount)
    }

    private suspend fun insertEntry(uri: String): Long {
        val dao = db.wallpaperDao()
        val source = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.BUILT_IN, displayName = "Test"))
        dao.applyScan(source, listOf(WallpaperEntryEntity(sourceId = source, uri = uri, displayName = "Test")), emptyList(), emptyList())
        return dao.getEntries(source).single().id
    }
}

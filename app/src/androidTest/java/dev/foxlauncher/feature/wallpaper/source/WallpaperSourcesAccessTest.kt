package dev.foxlauncher.feature.wallpaper.source

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.data.db.FoxDatabase
import dev.foxlauncher.core.data.db.WallpaperDao
import dev.foxlauncher.core.data.db.WallpaperEntryEntity
import dev.foxlauncher.core.data.db.WallpaperSourceEntity
import dev.foxlauncher.core.data.db.WallpaperSourceType
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lost access (task 4.9): sources whose URIs Fox holds no lasting grant for, as after the grant was
 * revoked. The test app holds none at all, so every user URI here counts as revoked.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperSourcesAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: FoxDatabase
    private lateinit var dao: WallpaperDao
    private lateinit var sources: WallpaperSources

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FoxDatabase::class.java).build()
        dao = db.wallpaperDao()
        sources = WallpaperSources(context, dao, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun aFolderWithoutItsGrantMustBePickedAgain() = runBlocking {
        val folder = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.FOLDER_TREE, uri = TREE, displayName = "Holidays"))

        sources.checkAccess()

        assertTrue(dao.getSource(folder)?.needsReauth == true)
    }

    @Test
    fun pickedPicturesWithoutTheirGrantAreSkipped() = runBlocking {
        val picked = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.PICKED_IMAGES, displayName = "Pictures"))
        dao.applyScan(picked, listOf(WallpaperEntryEntity(sourceId = picked, uri = "content://media/picker/0/x/1", displayName = "1")), emptyList(), emptyList())

        sources.checkAccess()

        assertEquals(WallpaperEntryEntity.MAX_FAILURES, dao.getEntries(picked).single().failedCount)
    }

    @Test
    fun theBuiltInPicturesNeedNoGrant() = runBlocking {
        sources.ensureBuiltIn()

        sources.checkAccess()

        val builtIn = checkNotNull(dao.getSourceOfType(WallpaperSourceType.BUILT_IN))
        assertEquals(false, builtIn.needsReauth)
        assertTrue(dao.getEntries(builtIn.id).all { it.failedCount == 0 })
    }

    @Test
    fun anUnreadableFolderPictureFlagsItsFolderAtOnce() = runBlocking {
        val folder = dao.insertSource(WallpaperSourceEntity(type = WallpaperSourceType.FOLDER_TREE, uri = TREE, displayName = "Holidays"))
        dao.applyScan(folder, listOf(WallpaperEntryEntity(sourceId = folder, uri = "$TREE/document/x", displayName = "x")), emptyList(), emptyList())
        val entry = dao.getEntries(folder).single()

        sources.reportUnreadable(entry.id)

        assertEquals(1, dao.getEntry(entry.id)?.failedCount)
        assertTrue(dao.getSource(folder)?.needsReauth == true)
    }

    private companion object {
        const val TREE = "content://com.android.externalstorage.documents/tree/primary%3APictures%2FHolidays"
    }
}

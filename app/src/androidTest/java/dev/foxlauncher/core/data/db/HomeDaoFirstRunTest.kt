package dev.foxlauncher.core.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.foxlauncher.core.model.HomeContainer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The first-run layout (task 2.7) only ever lands on an empty home. */
@RunWith(AndroidJUnit4::class)
class HomeDaoFirstRunTest {

    private lateinit var db: FoxDatabase
    private lateinit var dao: HomeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoxDatabase::class.java).build()
        dao = db.homeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun anEmptyHomeGetsTheDefaultDock() = runBlocking {
        assertTrue(dao.insertIntoEmptyHome(listOf(dockApp(0), dockApp(1), dockApp(3), dockApp(4))))

        assertEquals(listOf(0, 1, 3, 4), dao.getAll().map { it.col }.sorted())
    }

    @Test
    fun aHomeWithAnythingOnItIsLeftAlone() = runBlocking {
        dao.insert(dockApp(2))

        assertFalse(dao.insertIntoEmptyHome(listOf(dockApp(0), dockApp(1))))

        assertEquals(listOf(2), dao.getAll().map { it.col })
    }

    @Test
    fun runningTwiceDoesNotDoubleTheDock() = runBlocking {
        dao.insertIntoEmptyHome(listOf(dockApp(0), dockApp(1)))
        dao.insertIntoEmptyHome(listOf(dockApp(0), dockApp(1)))

        assertEquals(2, dao.count())
    }

    private fun dockApp(slot: Int) = HomeItemEntity(
        container = HomeContainer.DOCK,
        page = 0,
        col = slot,
        row = 0,
        type = HomeItemType.APP,
        component = "com.example/.App$slot",
        userSerial = 0,
        createdAt = 0,
    )
}

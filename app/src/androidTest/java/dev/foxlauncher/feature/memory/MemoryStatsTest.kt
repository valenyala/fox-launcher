package dev.foxlauncher.feature.memory

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Memory as the device reports it (task 5.2). */
@RunWith(AndroidJUnit4::class)
class MemoryStatsTest {

    private val stats = MemoryStats(ApplicationProvider.getApplicationContext(), Dispatchers.IO)

    @Test
    fun aSnapshotIsSane(): Unit = runBlocking {
        val snapshot = stats.snapshot()

        assertTrue(snapshot.totalBytes > 512L * 1024 * 1024, "total $snapshot")
        assertTrue(snapshot.availableBytes in 1..snapshot.totalBytes, "available $snapshot")
        assertNotNull(snapshot.cachedBytes, "/proc/meminfo is readable")
        assertNotNull(snapshot.swapTotalBytes)
    }

    @Test
    fun androidsTotalIsTheKernelsTotal(): Unit = runBlocking {
        val kernel = checkNotNull(MemInfoParser.parse(java.io.File("/proc/meminfo").readText()))

        assertEquals(kernel.totalKb * 1024, stats.snapshot().totalBytes)
    }

    @Test
    fun observingKeepsReading(): Unit = runBlocking {
        // Memory moves all the time, so readings 300 ms apart differ and each one comes through.
        val readings = stats.observe(intervalMs = 300).take(2).toList()

        assertEquals(2, readings.size)
    }
}

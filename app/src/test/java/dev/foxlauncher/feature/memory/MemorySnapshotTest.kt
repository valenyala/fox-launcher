package dev.foxlauncher.feature.memory

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class MemorySnapshotTest {

    private val gb = 1024L * 1024 * 1024
    private val info = MemInfo(
        totalKb = 6 * 1024 * 1024,
        freeKb = 200 * 1024,
        availableKb = 1024 * 1024,
        cachedKb = 900 * 1024,
        buffersKb = 2 * 1024,
        swapTotalKb = 4 * 1024 * 1024,
        swapFreeKb = 1024 * 1024,
    )

    @Test
    fun androidsFiguresLeadAndTheKernelFillsInTheRest() {
        val snapshot = MemorySnapshot.of(totalMem = 6 * gb, availMem = 2 * gb, threshold = gb / 4, lowMemory = false, memInfo = info)

        assertEquals(2 * gb, snapshot.availableBytes, "ActivityManager's available, not the kernel's")
        assertEquals(4 * gb, snapshot.usedBytes)
        assertEquals(4f / 6f, snapshot.usedFraction, 0.0001f)
        assertEquals(900L * 1024 * 1024, snapshot.cachedBytes)
        assertEquals(3 * gb, snapshot.swapUsedBytes)
    }

    @Test
    fun withoutAndroidsFiguresTheKernelStandsIn() {
        val snapshot = MemorySnapshot.of(totalMem = 0, availMem = 0, threshold = 0, lowMemory = false, memInfo = info)

        assertEquals(6 * gb, snapshot.totalBytes)
        assertEquals(gb, snapshot.availableBytes)
    }

    @Test
    fun anOldKernelWithoutMemAvailableEstimatesIt() {
        val snapshot = MemorySnapshot.of(0, 0, 0, false, info.copy(availableKb = null))

        assertEquals((200L + 900 + 2) * 1024 * 1024, snapshot.availableBytes, "free + cached + buffers")
    }

    @Test
    fun withoutTheKernelsBreakdownItIsMissingNotZero() {
        val snapshot = MemorySnapshot.of(6 * gb, 2 * gb, 0, false, memInfo = null)

        assertNull(snapshot.cachedBytes)
        assertNull(snapshot.swapUsedBytes)
        assertEquals(4 * gb, snapshot.usedBytes)
    }

    @Test
    fun availableNeverExceedsTotalAndNothingIsNegative() {
        val snapshot = MemorySnapshot.of(totalMem = gb, availMem = 3 * gb, threshold = 0, lowMemory = true, memInfo = info.copy(swapFreeKb = 9 * 1024 * 1024))

        assertEquals(gb, snapshot.availableBytes)
        assertEquals(0, snapshot.usedBytes)
        assertEquals(0f, snapshot.usedFraction)
        assertEquals(0, snapshot.swapUsedBytes)
    }

    @Test
    fun nothingKnownIsAnEmptySnapshot() {
        val snapshot = MemorySnapshot.of(0, 0, 0, false, null)

        assertEquals(0, snapshot.totalBytes)
        assertEquals(0f, snapshot.usedFraction)
    }
}

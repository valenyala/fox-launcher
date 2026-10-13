package dev.foxlauncher.feature.memory

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class MemInfoParserTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("meminfo/$name")) { "missing test resource $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun aRealPhonesMeminfoParses() {
        // Read from a POCO F3 (Android 13) on 2026-10-13.
        val info = assertNotNull(MemInfoParser.parse(resource("poco-f3-api33.txt")))

        assertEquals(5_654_748, info.totalKb)
        assertEquals(209_808, info.freeKb)
        assertEquals(642_668, info.availableKb)
        assertEquals(954_540, info.cachedKb, "Cached, not SwapCached")
        assertEquals(1_532, info.buffersKb)
        assertEquals(4_194_300, info.swapTotalKb)
        assertEquals(1_052_924, info.swapFreeKb)
    }

    @Test
    fun anEmulatorsMeminfoParses() {
        val info = assertNotNull(MemInfoParser.parse(resource("emulator-api37.txt")))

        assertEquals(4_008_500, info.totalKb)
        assertEquals(2_327_668, info.availableKb)
    }

    @Test
    fun aTruncatedReadGivesWhatItHolds() {
        val truncated = resource("poco-f3-api33.txt").lines().take(3).joinToString("\n") + "\nBuffers:            15"

        val info = assertNotNull(MemInfoParser.parse(truncated))

        assertEquals(5_654_748, info.totalKb)
        assertEquals(642_668, info.availableKb)
        assertNull(info.buffersKb, "cut off before its unit")
        assertNull(info.cachedKb)
        assertNull(info.swapTotalKb)
    }

    @Test
    fun withoutMemTotalThereIsNothing() {
        assertNull(MemInfoParser.parse("MemFree:  1000 kB\nMemAvailable:  2000 kB\n"))
        assertNull(MemInfoParser.parse(""))
    }

    @Test
    fun brokenAndForeignLinesAreSkipped() {
        val text = """
            MemTotal:        8000 kB
            garbage line
            MemFree:         lots kB
            MemAvailable:    -5 kB
            Cached:          300 MB
            HugePages_Total:       0
            SwapTotal:       100 kB
        """.trimIndent()

        val info = assertNotNull(MemInfoParser.parse(text))

        assertEquals(MemInfo(totalKb = 8000, freeKb = null, availableKb = null, cachedKb = null, buffersKb = null, swapTotalKb = 100, swapFreeKb = null), info)
    }
}

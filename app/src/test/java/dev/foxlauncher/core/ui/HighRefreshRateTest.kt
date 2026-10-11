package dev.foxlauncher.core.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class HighRefreshRateTest {

    private val at60 = DisplayModeSpec(1, 1080, 2400, 60f)
    private val at90 = DisplayModeSpec(2, 1080, 2400, 90f)
    private val at120 = DisplayModeSpec(3, 1080, 2400, 120f)

    @Test
    fun picksTheFastestModeAtTheSameResolution() {
        assertEquals(at120, fastestModeAtSameSize(listOf(at60, at120, at90), current = at60))
    }

    @Test
    fun neverSwitchesResolutionForSpeed() {
        val fasterButSmaller = DisplayModeSpec(4, 720, 1600, 144f)

        assertEquals(at120, fastestModeAtSameSize(listOf(at60, at120, fasterButSmaller), current = at60))
    }

    @Test
    fun nothingWhenAlreadyAtTheFastest() {
        assertNull(fastestModeAtSameSize(listOf(at60, at120), current = at120))
    }
}

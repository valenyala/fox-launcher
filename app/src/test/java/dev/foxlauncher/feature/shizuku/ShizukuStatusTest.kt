package dev.foxlauncher.feature.shizuku

import kotlin.test.assertEquals
import org.junit.Test

class ShizukuStatusTest {

    private fun status(probe: ShizukuProbe) = ShizukuStatus.of(probe)

    @Test
    fun eachStepToReady() {
        assertEquals(ShizukuStatus.NOT_INSTALLED, status(ShizukuProbe(installed = false, running = false)))
        assertEquals(ShizukuStatus.NOT_RUNNING, status(ShizukuProbe(installed = true, running = false)))
        assertEquals(ShizukuStatus.NEEDS_PERMISSION, status(ShizukuProbe(installed = true, running = true)))
        assertEquals(ShizukuStatus.READY, status(ShizukuProbe(installed = true, running = true, granted = true)))
    }

    @Test
    fun aRefusalForGoodIsNotAskedAgain() {
        assertEquals(ShizukuStatus.DENIED, status(ShizukuProbe(installed = true, running = true, deniedForGood = true)))
    }

    @Test
    fun anOldShizukuIsTooOldWhateverElse() {
        assertEquals(ShizukuStatus.TOO_OLD, status(ShizukuProbe(installed = true, running = true, preV11 = true, granted = true)))
    }

    @Test
    fun aRunningServiceCountsAsInstalled() {
        assertEquals(ShizukuStatus.READY, status(ShizukuProbe(installed = false, running = true, granted = true)))
    }
}

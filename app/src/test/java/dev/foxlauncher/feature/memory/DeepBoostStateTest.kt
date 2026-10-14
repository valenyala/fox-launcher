package dev.foxlauncher.feature.memory

import dev.foxlauncher.feature.shizuku.ShizukuStatus
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DeepBoostStateTest {

    @Test
    fun deepBoostIsActiveOnlyWhenOnAndReady() {
        assertTrue(DeepBoostState(ShizukuStatus.READY, enabled = true).active)
        assertFalse(DeepBoostState(ShizukuStatus.READY, enabled = false).active)
        ShizukuStatus.entries.filter { it != ShizukuStatus.READY }.forEach {
            assertFalse(DeepBoostState(it, enabled = true).active, "$it")
        }
    }
}

package dev.foxlauncher.core.common

import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

/** Smoke test for the shared rule: without it, touching `Dispatchers.Main` off-device throws. */
class MainDispatcherRuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun mainDispatcherRunsEagerlyInTests() {
        val events = mutableListOf<String>()
        CoroutineScope(Dispatchers.Main).launch { events += "ran" }
        assertEquals(listOf("ran"), events)
    }
}

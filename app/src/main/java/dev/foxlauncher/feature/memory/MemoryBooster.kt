package dev.foxlauncher.feature.memory

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/** What a boost did (ADR-5): the measured rise in available memory, never negative, never estimated. */
data class BoostResult(val freedBytes: Long, val closedApps: Int = 0)

/**
 * Runs a boost and measures it (task 5.3, ADR-5): available memory before, the work, a pause for the
 * system to settle, available memory after. The widget shows that delta and nothing else. Until
 * task 5.4 brings the strategies, the work is only asking the runtime to collect garbage.
 */
@Singleton
class MemoryBooster @Inject constructor(private val stats: MemoryStats) {

    /** Whether this Android lets a boost close other apps' background processes. */
    val canCloseOtherApps: Boolean get() = BoostCapability.canCloseOtherApps(Build.VERSION.SDK_INT)

    suspend fun boost(): BoostResult {
        val before = stats.snapshot().availableBytes
        Runtime.getRuntime().gc()
        delay(SETTLE_MS)
        val after = stats.snapshot().availableBytes
        return BoostResult(freedBytes = (after - before).coerceAtLeast(0))
    }

    private companion object {
        const val SETTLE_MS = 500L
    }
}

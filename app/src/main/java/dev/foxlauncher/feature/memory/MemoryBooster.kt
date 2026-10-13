package dev.foxlauncher.feature.memory

import android.os.Build
import dev.foxlauncher.core.common.FoxLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/** What a boost did (ADR-5): the measured rise in available memory, never negative, never estimated. */
data class BoostResult(val freedBytes: Long, val closedApps: Int = 0)

/**
 * Runs a boost and measures it (tasks 5.3, 5.4, ADR-5): available memory before, every strategy
 * this Android allows ([BoostStrategies.forSdk]: Fox's own caches always, other apps' background
 * processes up to Android 13), a pause for the system to settle, available memory after. The
 * widget shows that delta and nothing else; less than before counts as nothing freed.
 */
@Singleton
class MemoryBooster @Inject constructor(
    private val stats: MemoryStats,
    selfTrim: SelfTrimStrategy,
    closeOthers: LegacyKillStrategy,
) {
    private val strategies: List<BoostStrategy> = BoostStrategies.forSdk(Build.VERSION.SDK_INT, selfTrim, closeOthers)

    /** Whether this Android lets a boost close other apps' background processes. */
    val canCloseOtherApps: Boolean get() = BoostCapability.canCloseOtherApps(Build.VERSION.SDK_INT)

    suspend fun boost(): BoostResult {
        val before = stats.snapshot().availableBytes
        val closed = strategies.sumOf { it.boost() }
        delay(SETTLE_MS)
        val after = stats.snapshot().availableBytes
        val result = BoostResult(freedBytes = (after - before).coerceAtLeast(0), closedApps = closed)
        FoxLog.d("Boost (${strategies.joinToString { it.name }}): ${MemoryFormat.mb(after - before)} MB measured, $closed apps asked to close")
        return result
    }

    private companion object {
        const val SETTLE_MS = 500L
    }
}

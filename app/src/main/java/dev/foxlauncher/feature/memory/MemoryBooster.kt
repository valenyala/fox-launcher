package dev.foxlauncher.feature.memory

import android.os.Build
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.memory.shizuku.DeepBoost
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What a boost did (ADR-5): the measured rise in available memory, never negative, never estimated. */
data class BoostResult(val freedBytes: Long, val closedApps: Int = 0)

/**
 * Runs a boost and measures it (tasks 5.3, 5.4, 9.1, ADR-5): available memory before, every strategy
 * this phone allows ([BoostStrategies.choose]: the deep boost through Shizuku when the user has it on
 * and Shizuku allows Fox; else other apps' background processes up to Android 13 except on MIUI; Fox's
 * own caches always), a pause for the system to settle, available memory after. The widget shows that
 * delta and nothing else; less than before counts as nothing freed.
 */
@Singleton
class MemoryBooster @Inject constructor(
    private val stats: MemoryStats,
    private val selfTrim: SelfTrimStrategy,
    private val closeOthers: LegacyKillStrategy,
    private val deep: ShizukuBoostStrategy,
    private val deepBoost: DeepBoost,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val platformLimit: BoostLimit? = BoostCapability.limit(Build.VERSION.SDK_INT, Build.MANUFACTURER)

    /** Why a boost cannot close other apps here, or null when it can (also while the deep boost is active). */
    val limit: StateFlow<BoostLimit?> = deepBoost.state
        .map { BoostStrategies.limit(platformLimit, it.active) }
        .stateIn(scope, SharingStarted.Eagerly, BoostStrategies.limit(platformLimit, deepBoost.state.value.active))

    suspend fun boost(): BoostResult {
        val strategies = BoostStrategies.choose(platformLimit, deepBoost.state.value.active, selfTrim, closeOthers, deep)
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

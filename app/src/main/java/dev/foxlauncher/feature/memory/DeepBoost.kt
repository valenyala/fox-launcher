package dev.foxlauncher.feature.memory

import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.data.settings.FoxSettings
import dev.foxlauncher.feature.shizuku.ShizukuAccess
import dev.foxlauncher.feature.shizuku.ShizukuStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The deep boost's state: Shizuku's [status] and whether the user turned it on. */
data class DeepBoostState(val status: ShizukuStatus, val enabled: Boolean) {
    /** A boost goes through Shizuku only when both hold (ADR-9: opt-in, and degrades without Shizuku). */
    val active: Boolean get() = enabled && status == ShizukuStatus.READY
}

/** The deep boost (task 9.1): Shizuku's status with the user's opt-in, which is off until turned on (ADR-9). */
@Singleton
class DeepBoost @Inject constructor(
    private val settings: FoxSettings,
    private val shizuku: ShizukuAccess,
    @param:ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<DeepBoostState> = combine(shizuku.status, settings.deepBoost, ::DeepBoostState)
        .stateIn(scope, SharingStarted.Eagerly, DeepBoostState(shizuku.status.value, enabled = false))

    suspend fun setEnabled(enabled: Boolean) = settings.setDeepBoost(enabled)
}

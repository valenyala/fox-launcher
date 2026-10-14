package dev.foxlauncher.feature.memory.shizuku

import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.data.settings.FoxSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

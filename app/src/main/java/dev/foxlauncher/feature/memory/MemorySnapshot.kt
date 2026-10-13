package dev.foxlauncher.feature.memory

/**
 * Memory at one moment, as the memory widgets show it (task 5.2), in bytes. Total and available
 * are Android's own (`ActivityManager.MemoryInfo`, what Settings shows); the breakdown comes from
 * `/proc/meminfo` and is null when that could not be read.
 */
data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    /** Below this much available memory Android starts killing background apps. */
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val cachedBytes: Long?,
    val buffersBytes: Long?,
    val swapTotalBytes: Long?,
    val swapFreeBytes: Long?,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)

    /** Used memory as a fraction of total, 0–1. */
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val swapUsedBytes: Long? get() = if (swapTotalBytes == null || swapFreeBytes == null) null else (swapTotalBytes - swapFreeBytes).coerceAtLeast(0)

    companion object {
        private const val BYTES_PER_KB = 1024L

        /**
         * Combines `ActivityManager`'s figures with [memInfo]. When Android reports nothing (zero
         * total, as a broken service would), `/proc/meminfo` stands in: its total, and its available
         * figure or, on kernels without one, free + cached + buffers.
         */
        fun of(totalMem: Long, availMem: Long, threshold: Long, lowMemory: Boolean, memInfo: MemInfo?): MemorySnapshot {
            val kernelTotal = memInfo?.totalKb?.times(BYTES_PER_KB)
            val kernelAvailable = memInfo?.let { info ->
                (info.availableKb ?: listOfNotNull(info.freeKb, info.cachedKb, info.buffersKb).takeIf { it.isNotEmpty() }?.sum())
                    ?.times(BYTES_PER_KB)
            }
            val total = if (totalMem > 0) totalMem else kernelTotal ?: 0L
            val available = if (totalMem > 0) availMem else kernelAvailable ?: 0L
            return MemorySnapshot(
                totalBytes = total,
                availableBytes = available.coerceIn(0, total),
                thresholdBytes = threshold,
                lowMemory = lowMemory,
                cachedBytes = memInfo?.cachedKb?.times(BYTES_PER_KB),
                buffersBytes = memInfo?.buffersKb?.times(BYTES_PER_KB),
                swapTotalBytes = memInfo?.swapTotalKb?.times(BYTES_PER_KB),
                swapFreeBytes = memInfo?.swapFreeKb?.times(BYTES_PER_KB),
            )
        }
    }
}

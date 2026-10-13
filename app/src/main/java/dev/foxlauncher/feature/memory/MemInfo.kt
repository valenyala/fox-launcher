package dev.foxlauncher.feature.memory

/**
 * The parts of `/proc/meminfo` Fox shows (task 5.2), in kB as the kernel reports them. Everything
 * but [totalKb] may be missing from a kernel or a truncated read, hence null.
 */
data class MemInfo(
    val totalKb: Long,
    val freeKb: Long?,
    val availableKb: Long?,
    val cachedKb: Long?,
    val buffersKb: Long?,
    val swapTotalKb: Long?,
    val swapFreeKb: Long?,
)

/** Reads `/proc/meminfo` text (PLATFORM-NOTES M). Pure, so it is unit tested. */
object MemInfoParser {

    /**
     * The fields of [text]: lines of `Name:   value kB`. Unknown lines, other units and broken lines
     * are skipped, so a truncated read gives what it holds. Null without `MemTotal`, which nothing
     * can stand in for.
     */
    fun parse(text: String): MemInfo? {
        val fields = text.lineSequence().mapNotNull(::field).toMap()
        val total = fields[MEM_TOTAL] ?: return null
        return MemInfo(
            totalKb = total,
            freeKb = fields[MEM_FREE],
            availableKb = fields[MEM_AVAILABLE],
            cachedKb = fields[CACHED],
            buffersKb = fields[BUFFERS],
            swapTotalKb = fields[SWAP_TOTAL],
            swapFreeKb = fields[SWAP_FREE],
        )
    }

    private fun field(line: String): Pair<String, Long>? {
        val name = line.substringBefore(':', missingDelimiterValue = "").trim()
        if (name !in WANTED) return null
        val parts = line.substringAfter(':').trim().split(WHITESPACE)
        // A value cut off mid-read has no unit yet: it cannot be trusted.
        if (parts.size != 2 || parts[1] != "kB") return null
        return parts[0].toLongOrNull()?.takeIf { it >= 0 }?.let { name to it }
    }

    private const val MEM_TOTAL = "MemTotal"
    private const val MEM_FREE = "MemFree"
    private const val MEM_AVAILABLE = "MemAvailable"
    private const val CACHED = "Cached"
    private const val BUFFERS = "Buffers"
    private const val SWAP_TOTAL = "SwapTotal"
    private const val SWAP_FREE = "SwapFree"
    private val WANTED = setOf(MEM_TOTAL, MEM_FREE, MEM_AVAILABLE, CACHED, BUFFERS, SWAP_TOTAL, SWAP_FREE)
    private val WHITESPACE = Regex("\\s+")
}

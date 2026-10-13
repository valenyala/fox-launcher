package dev.foxlauncher.feature.memory

import java.util.Locale
import kotlin.math.roundToInt

/** Numbers the memory widgets print (task 5.3). Pure, so it is unit tested. */
object MemoryFormat {

    private const val MB = 1024.0 * 1024
    private const val GB = MB * 1024

    /** Gigabytes with one decimal: "2.1". */
    fun gb(bytes: Long, locale: Locale): String = String.format(locale, "%.1f", bytes / GB)

    /** Whole megabytes: "123". */
    fun mb(bytes: Long): Long = (bytes / MB).roundToLong()

    /** A size in GB from 1 GB up, else in MB: ("2.1", true) or ("640", false). */
    fun size(bytes: Long, locale: Locale): Pair<String, Boolean> =
        if (bytes >= GB) gb(bytes, locale) to true else mb(bytes).toString() to false

    /** Whole percent, 0–100. */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).roundToInt()

    private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
}

/** What a boost can do on this Android version (ADR-5). Pure, so it is unit tested. */
object BoostCapability {

    /** Android 14 (API 34) took away closing other apps' background processes. */
    const val LAST_API_THAT_KILLS_OTHERS = 33

    fun canCloseOtherApps(sdkInt: Int): Boolean = sdkInt <= LAST_API_THAT_KILLS_OTHERS
}

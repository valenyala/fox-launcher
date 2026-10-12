package dev.foxlauncher.core.data.settings

/** A list of ids as one DataStore string, `"3,1,2"` (task 4.2). Unreadable parts are skipped. */
object IdListCodec {

    fun encode(ids: List<Long>): String = ids.joinToString(",")

    fun decode(stored: String?): List<Long> =
        stored.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}

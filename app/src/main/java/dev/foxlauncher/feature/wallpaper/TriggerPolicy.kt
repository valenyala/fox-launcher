package dev.foxlauncher.feature.wallpaper

import dev.foxlauncher.core.model.RotationTrigger
import dev.foxlauncher.core.model.RotationTriggers

/** Whether a trigger should change the wallpaper now (task 4.4). Pure, so it is unit tested. */
object TriggerPolicy {

    /** Unlocks this close together change the wallpaper once. */
    const val UNLOCK_DEBOUNCE_MS = 5_000L

    /** A page change this soon after the last change does nothing (paging back and forth). */
    const val PAGE_DEBOUNCE_MS = 1_000L

    /** Interval choices offered in the settings, in minutes: 1, 5, 10, 15, 30, 60 min, 3 h, a day. */
    val INTERVALS = listOf(1, 5, 10, 15, 30, 60, 180, 1440)

    /** WorkManager's shortest period; shorter intervals run on the live wallpaper's own timer. */
    const val WORK_MANAGER_MIN_MINUTES = 15

    /** WorkManager runs periodic work within a window, so a run a little early still counts. */
    private const val INTERVAL_TOLERANCE = 0.9

    private const val MS_PER_MINUTE = 60_000L

    /**
     * [trigger] fires at [now]; the wallpaper last changed at [lastChangeAt] (null: never). The
     * user's own requests always go through; the others follow [triggers] and keep their distance
     * from the last change. A clock set back counts as long ago.
     */
    fun shouldAdvance(trigger: RotationTrigger, triggers: RotationTriggers, lastChangeAt: Long?, now: Long): Boolean {
        val since = sinceLastChange(lastChangeAt, now)
        return when (trigger) {
            RotationTrigger.GESTURE, RotationTrigger.MANUAL -> true
            RotationTrigger.INTERVAL ->
                triggers.intervalMinutes > 0 && since >= (triggers.intervalMinutes * MS_PER_MINUTE * INTERVAL_TOLERANCE).toLong()
            RotationTrigger.UNLOCK -> triggers.onUnlock && since >= UNLOCK_DEBOUNCE_MS
            RotationTrigger.PAGE_CHANGE -> triggers.onPageChange && since >= PAGE_DEBOUNCE_MS
        }
    }

    /** Ms until the next interval change is due (0: now), for the live wallpaper's short-interval timer. */
    fun untilNextInterval(intervalMinutes: Int, lastChangeAt: Long?, now: Long): Long {
        val since = sinceLastChange(lastChangeAt, now)
        return (intervalMinutes * MS_PER_MINUTE - since).coerceAtLeast(0)
    }

    private fun sinceLastChange(lastChangeAt: Long?, now: Long): Long {
        val since = if (lastChangeAt == null) Long.MAX_VALUE else now - lastChangeAt
        return if (since < 0) Long.MAX_VALUE else since
    }
}

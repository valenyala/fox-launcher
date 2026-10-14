package dev.foxlauncher.feature.shizuku

/** Where Shizuku stands for Fox (tasks 9.1, 9.6), from the first thing missing to ready. */
enum class ShizukuStatus {
    /** The Shizuku app is not installed. */
    NOT_INSTALLED,

    /** Installed, but its service is not running (it must be started after every reboot). */
    NOT_RUNNING,

    /** A Shizuku older than version 11, which Fox does not support. */
    TOO_OLD,

    /** Running; Fox has not been allowed yet and can ask. */
    NEEDS_PERMISSION,

    /** Running; the user refused Fox for good, so only Shizuku's own app can allow it now. */
    DENIED,

    /** Running and Fox is allowed. */
    READY,
    ;

    companion object {
        /** Pure, so it is unit tested. */
        fun of(probe: ShizukuProbe): ShizukuStatus = when {
            // A running service proves the app is there, even if the package lookup missed it (another profile).
            !probe.running -> if (probe.installed) NOT_RUNNING else NOT_INSTALLED
            probe.preV11 -> TOO_OLD
            probe.granted -> READY
            probe.deniedForGood -> DENIED
            else -> NEEDS_PERMISSION
        }
    }
}

/** What Fox found out about Shizuku, raw. */
data class ShizukuProbe(
    val installed: Boolean,
    val running: Boolean,
    val preV11: Boolean = false,
    val granted: Boolean = false,
    val deniedForGood: Boolean = false,
)

package dev.foxlauncher.feature.memory.shizuku

/** Where Shizuku stands for Fox's deep boost (task 9.1), from the first thing missing to ready. */
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
}

/** What Fox found out about Shizuku, raw. */
data class ShizukuProbe(
    val installed: Boolean,
    val running: Boolean,
    val preV11: Boolean = false,
    val granted: Boolean = false,
    val deniedForGood: Boolean = false,
)

/** The deep boost's state: Shizuku's [status] and whether the user turned it on. */
data class DeepBoostState(val status: ShizukuStatus, val enabled: Boolean) {
    /** A boost goes through Shizuku only when both hold (ADR-9: opt-in, and degrades without Shizuku). */
    val active: Boolean get() = enabled && status == ShizukuStatus.READY
}

/** Pure, so it is unit tested. */
object DeepBoostStatus {

    fun of(probe: ShizukuProbe): ShizukuStatus = when {
        // A running service proves the app is there, even if the package lookup missed it (another profile).
        !probe.running -> if (probe.installed) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        probe.preV11 -> ShizukuStatus.TOO_OLD
        probe.granted -> ShizukuStatus.READY
        probe.deniedForGood -> ShizukuStatus.DENIED
        else -> ShizukuStatus.NEEDS_PERMISSION
    }
}

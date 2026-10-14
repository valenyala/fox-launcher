package dev.foxlauncher.feature.memory.shizuku

import kotlin.system.exitProcess

/**
 * Fox's Shizuku user service (task 9.1, ADR-21). Shizuku starts it in a process of its own that runs
 * as the shell, with Fox's APK on the class path, and hands its binder to Fox alone. It only runs
 * the shell's `cmd activity kill`, which MIUI and Android 14+ refuse to apps but allow the shell
 * (PLATFORM-NOTES M), so Fox's own process touches no hidden API. Nothing of Fox but this class and
 * the AIDL stub runs there: no Hilt, no Android context.
 */
class FoxShellService : IFoxShell.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun killBackgroundProcesses(packages: Array<String>): Int = packages.count { pkg ->
        // Each name is its own argument, never parsed by a shell; the check is a second guard.
        PACKAGE_NAME.matches(pkg) && run("cmd", "activity", "kill", "--user", "current", pkg)
    }

    private fun run(vararg command: String): Boolean {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.inputStream.use { it.readBytes() }
        return process.waitFor() == 0
    }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    }
}

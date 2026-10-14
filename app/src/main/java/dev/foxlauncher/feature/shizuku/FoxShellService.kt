package dev.foxlauncher.feature.shizuku

import kotlin.system.exitProcess

/**
 * Fox's Shizuku user service (tasks 9.1 and 9.6, ADR-21, ADR-23). Shizuku starts it in a process of
 * its own that runs as the shell, with Fox's APK on the class path, and hands its binder to Fox
 * alone. It runs only the [ShellCommands], which MIUI and Android 14+ refuse to apps but allow the
 * shell (PLATFORM-NOTES M), so Fox's own process touches no hidden API. Nothing of Fox but this
 * class, [ShellCommands] and the AIDL stub runs there: no Hilt, no Android context.
 */
class FoxShellService : IFoxShell.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun killBackgroundProcesses(packages: Array<String>): Int =
        packages.count { pkg -> ShellCommands.killBackgroundProcesses(pkg)?.let(::run) == true }

    override fun setNavigationButtonsHidden(hidden: Boolean, miui: Boolean): Boolean = run(ShellCommands.navigationButtons(miui, hidden))

    private fun run(command: List<String>): Boolean {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.inputStream.use { it.readBytes() }
        return process.waitFor() == 0
    }
}

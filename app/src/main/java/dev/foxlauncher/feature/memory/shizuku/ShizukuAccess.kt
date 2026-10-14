package dev.foxlauncher.feature.memory.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.BuildConfig
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.common.MainDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Fox's link to Shizuku for the deep boost (task 9.1, ADR-9, ADR-21): whether it is installed,
 * running and allows Fox ([status]), asking it for permission, and running code in Fox's shell
 * service ([withShell]). Nothing else in Fox uses Shizuku.
 */
@Singleton
class ShizukuAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:MainDispatcher private val main: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var following = false
    private val onBinderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val onBinderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    /** From `HomeActivity.onCreate`: follows Shizuku from now on. Main thread; later calls only [refresh]. */
    fun start() {
        if (!following) {
            following = true
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermissionResult)
        }
        refresh()
    }

    /** Reads [status] again, off the main thread (it looks the app up). */
    fun refresh() {
        scope.launch { _status.value = withContext(io) { DeepBoostStatus.of(probe()) } }
    }

    private fun probe(): ShizukuProbe {
        val installed = isInstalled()
        if (!Shizuku.pingBinder()) return ShizukuProbe(installed, running = false)
        if (Shizuku.isPreV11()) return ShizukuProbe(installed, running = true, preV11 = true)
        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        return ShizukuProbe(installed, running = true, granted = granted, deniedForGood = !granted && Shizuku.shouldShowRequestPermissionRationale())
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Shizuku shows its own dialog; the answer comes back through [status]. */
    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_REQUEST)
    }

    /** The Shizuku app, where it is started and Fox allowed; its page in a store when it is missing. Off the main thread. */
    suspend fun shizukuAppIntent(): Intent = withContext(io) {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$SHIZUKU_PACKAGE"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Binds Fox's shell service in Shizuku, runs [block] with it off the main thread, and lets the
     * service go again. Null when Shizuku is not ready, the service does not come up in time, or the
     * call fails.
     */
    suspend fun <T> withShell(block: (IFoxShell) -> T): T? {
        if (_status.value != ShizukuStatus.READY) return null
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, FoxShellService::class.java.name))
            .daemon(false)
            .processNameSuffix(PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        val connected = CompletableDeferred<IFoxShell>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) connected.complete(IFoxShell.Stub.asInterface(binder))
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        return try {
            withContext(main) { Shizuku.bindUserService(args, connection) }
            val shell = withTimeoutOrNull(BIND_TIMEOUT_MS) { connected.await() }
            if (shell == null) {
                FoxLog.w("Fox's Shizuku service did not start in time")
                null
            } else {
                withContext(io) { block(shell) }
            }
        } catch (e: RemoteException) {
            FoxLog.w("Fox's Shizuku service failed", e)
            null
        } catch (e: RuntimeException) {
            // Shizuku throws IllegalStateException when its binder died meanwhile.
            FoxLog.w("Shizuku went away", e)
            null
        } finally {
            // Even when the boost was cancelled: the service must not stay up.
            withContext(NonCancellable + main) { runCatching { Shizuku.unbindUserService(args, connection, true) } }
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST = 0x0F09
        private const val PROCESS_SUFFIX = "shell"
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}

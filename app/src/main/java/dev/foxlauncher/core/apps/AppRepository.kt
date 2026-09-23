package dev.foxlauncher.core.apps

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.Collation
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.model.AppEntry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single source of truth for installed, launchable apps across every user profile. Built from
 * `LauncherApps` (never `PackageManager.queryIntentActivities`, which breaks work profiles) and
 * kept current by a `LauncherApps.Callback`. See PLATFORM-NOTES A.
 */
@Singleton
class AppRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<ImmutableList<AppEntry>>(persistentListOf())

    /** Every launchable activity, sorted by label. Empty until the first [refresh] finishes. */
    val apps: StateFlow<ImmutableList<AppEntry>> = _apps.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)

    /** False until the first [refresh] has completed, so consumers can tell "loading" from "no apps". */
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _packageChanges = MutableSharedFlow<PackageChange>(
        extraBufferCapacity = PACKAGE_CHANGE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every package added, removed or changed, as it happens (not debounced). The icon cache listens here. */
    val packageChanges: SharedFlow<PackageChange> = _packageChanges.asSharedFlow()

    private val _workProfile = MutableStateFlow<WorkProfile?>(null)

    /** The work (managed) profile, or null when the device has none. */
    val workProfile: StateFlow<WorkProfile?> = _workProfile.asStateFlow()

    /** `UserHandle` objects are not stable across reboots; they live here, keyed by serial, for launching. */
    private val users = ConcurrentHashMap<Long, UserHandle>()

    private val refreshMutex = Mutex()
    private val refreshRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val callbackThread = HandlerThread("FoxLauncherApps").apply { start() }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = packageChanged(packageName, user)
        override fun onPackageAdded(packageName: String, user: UserHandle) = packageChanged(packageName, user)
        override fun onPackageChanged(packageName: String, user: UserHandle) = packageChanged(packageName, user)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            packageNames.forEach { packageChanged(it, user) }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) =
            requestRefresh()
        override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = requestRefresh()
        override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = requestRefresh()
    }

    /** Profiles come and go (work profile created, removed, paused, resumed) without any package event. */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FoxLog.d("Profile broadcast: ${intent.action}")
            requestRefresh()
        }
    }

    init {
        scope.launch { debouncedRefreshes() }
        val callbackHandler = Handler(callbackThread.looper)
        launcherApps.registerCallback(callback, callbackHandler)
        ContextCompat.registerReceiver(
            context,
            profileReceiver,
            IntentFilter().apply { PROFILE_ACTIONS.forEach(::addAction) },
            null,
            callbackHandler,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Directly, not via requestRefresh(): the debounce collector may not be subscribed yet.
        scope.launch { refresh() }
    }

    private fun packageChanged(packageName: String, user: UserHandle) {
        _packageChanges.tryEmit(PackageChange(packageName, userManager.getSerialNumberForUser(user)))
        requestRefresh()
    }

    @OptIn(FlowPreview::class)
    private suspend fun debouncedRefreshes() {
        refreshRequests.debounce(REFRESH_DEBOUNCE_MS).collect { refresh() }
    }

    /** Coalesces bursts (an update fires removed + added + changed) into one [refresh]. */
    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }

    /** Rebuilds the app list from `LauncherApps` for every profile. Safe to call concurrently. */
    suspend fun refresh() = withContext(io) {
        refreshMutex.withLock {
            val myUser = Process.myUserHandle()
            val versionCodes = HashMap<String, Long>()
            var work: WorkProfile? = null
            val entries = buildList {
                for (user in userManager.userProfiles) {
                    val serial = userManager.getSerialNumberForUser(user)
                    users[serial] = user
                    val available = !userManager.isQuietModeEnabled(user)
                    if (user != myUser && work == null) work = WorkProfile(serial, isPaused = !available)
                    for (info in launcherApps.getActivityList(null, user)) {
                        val pkg = info.componentName.packageName
                        val versionCode = versionCodes.getOrPut(pkg) { versionCodeOf(pkg) }
                        add(info.toEntry(serial, user != myUser, available, versionCode))
                    }
                }
            }
            val sorted = sortEntries(entries, Collation.labelComparator()).toImmutableList()
            _apps.value = sorted
            _workProfile.value = work
            _isLoaded.value = true
            FoxLog.d("AppRepository: ${sorted.size} apps across ${userManager.userProfiles.size} profiles")
        }
    }

    /** Starts [entry]'s main activity in its own profile. Suspended apps still go through: the system shows its dialog. */
    fun launch(entry: AppEntry, sourceBounds: Rect?, opts: Bundle?) {
        val component = ComponentName.unflattenFromString(entry.component) ?: return
        val user = userFor(entry.userSerial) ?: return
        try {
            launcherApps.startMainActivity(component, user, sourceBounds, opts)
        } catch (e: ActivityNotFoundException) {
            FoxLog.w("Launch failed, ${entry.component} is gone", e)
            requestRefresh()
        } catch (e: SecurityException) {
            FoxLog.w("Launch of ${entry.component} not allowed", e)
        }
    }

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect?) {
        val component = ComponentName.unflattenFromString(entry.component) ?: return
        val user = userFor(entry.userSerial) ?: return
        try {
            launcherApps.startAppDetailsActivity(component, user, sourceBounds, null)
        } catch (e: ActivityNotFoundException) {
            FoxLog.w("No app details screen for ${entry.component}", e)
        } catch (e: SecurityException) {
            FoxLog.w("App details for ${entry.component} not allowed", e)
        }
    }

    /** Opens the system uninstall confirmation for [entry]'s package in its own profile. */
    fun uninstall(entry: AppEntry) {
        val user = userFor(entry.userSerial) ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            FoxLog.w("No uninstaller for ${entry.packageName}", e)
        }
    }

    /**
     * Pauses or resumes the work profile. Only the default launcher may do this; resuming can make
     * the system ask for the work challenge first, in which case the profile changes later and the
     * profile broadcast refreshes us.
     */
    suspend fun setWorkPaused(paused: Boolean) = withContext(io) {
        val user = _workProfile.value?.let { userFor(it.userSerial) } ?: return@withContext
        try {
            userManager.requestQuietModeEnabled(paused, user)
        } catch (e: SecurityException) {
            FoxLog.w("Not allowed to change work profile quiet mode (not the default launcher?)", e)
        }
        requestRefresh()
    }

    /** True for apps that ship with the system image and so cannot be uninstalled from the launcher. */
    suspend fun isSystemApp(entry: AppEntry): Boolean = withContext(io) {
        val user = userFor(entry.userSerial) ?: return@withContext true
        try {
            val info = launcherApps.getApplicationInfo(entry.packageName, 0, user)
            info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }
    }

    /** Resolves a persisted serial back to a live `UserHandle`, or null when that profile no longer exists. */
    fun userFor(serial: Long): UserHandle? =
        users[serial] ?: userManager.getUserForSerialNumber(serial)?.also { users[serial] = it }

    private fun LauncherActivityInfo.toEntry(
        serial: Long,
        isWorkProfile: Boolean,
        available: Boolean,
        versionCode: Long,
    ) = AppEntry(
        component = componentName.flattenToString(),
        userSerial = serial,
        label = label.toString(),
        packageName = componentName.packageName,
        isWorkProfile = isWorkProfile,
        isSuspended = applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
        isAvailable = available,
        versionCode = versionCode,
    )

    /**
     * Version codes only feed icon-cache keys. `MATCH_UNINSTALLED_PACKAGES` also finds packages that
     * are installed only in another profile; anything still unresolvable keys as 0.
     */
    private fun versionCodeOf(packageName: String): Long = try {
        context.packageManager
            .getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        0L
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 250L
        const val PACKAGE_CHANGE_BUFFER = 64

        val PROFILE_ACTIONS = listOf(
            Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED,
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
        )
    }
}

/** A package that was added, removed or changed in the profile with serial [userSerial]. */
data class PackageChange(val packageName: String, val userSerial: Long)

/** The work profile's serial and whether it is paused (quiet mode). */
data class WorkProfile(val userSerial: Long, val isPaused: Boolean)

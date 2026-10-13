package dev.foxlauncher.feature.memory

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.model.AppEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The apps the detail widget lists (task 5.3): null without usage access, which only the user can grant. */
data class RecentApps(val apps: List<AppEntry>?)

/**
 * Recently used apps from `UsageStatsManager` (PLATFORM-NOTES M), which needs the special usage
 * access permission: [read] says so with `apps = null`, and [accessSettings] is where the user
 * grants it. Only apps in the drawer are listed, Fox itself never.
 */
@Singleton
class RecentAppsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** The [limit] apps used most recently in the last [windowMs], newest first. */
    suspend fun read(limit: Int, windowMs: Long): RecentApps = withContext(io) {
        if (!hasAccess()) return@withContext RecentApps(null)
        val now = System.currentTimeMillis()
        val usage = context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - windowMs, now)
            .filter { it.lastTimeUsed >= now - windowMs && it.packageName != context.packageName }
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
        val byPackage = appRepository.apps.value.groupBy { it.packageName }
        val apps = usage.entries
            .sortedByDescending { it.value }
            .mapNotNull { (pkg, _) -> byPackage[pkg]?.firstOrNull() }
            .take(limit)
        RecentApps(apps)
    }

    fun accessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

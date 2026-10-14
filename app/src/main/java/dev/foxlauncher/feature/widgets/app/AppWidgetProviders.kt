package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.RemoteViews
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext

/**
 * The installed app widget providers of every profile (task 6.2, PLATFORM-NOTES AW), read off the
 * main thread. It keeps what the latest [load] found, so the picker's previews and the bind flow
 * can look a provider up by [AppWidgetOption.key].
 */
@Singleton
class AppWidgetProviders @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    @Volatile private var loaded: Map<String, Loaded> = emptyMap()

    /** Inflates preview layouts off the main thread (`AppWidgetHostView.setExecutor`). */
    val inflateExecutor: Executor = io.asExecutor()

    /** Every provider that may go on a home screen, in no particular order. */
    suspend fun load(): List<AppWidgetOption> = withContext(io) {
        val myUser = Process.myUserHandle()
        val pm = context.packageManager
        val found = HashMap<String, Loaded>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            for (info in appWidgetManager.getInstalledProvidersForProfile(user)) {
                if (!info.isForHomeScreen()) continue
                val option = info.toOption(serial, user, isWorkProfile = user != myUser, pm)
                found[option.key] = Loaded(option, info, previewViews(info))
            }
        }
        loaded = found
        FoxLog.d("AppWidgetProviders: ${found.size} providers")
        found.values.map { it.option }
    }

    /** The provider behind [key] as of the latest [load], or null. */
    fun info(key: String): AppWidgetProviderInfo? = loaded[key]?.info

    /** [key]'s preview layout, ready to apply (API 31+), or null when it has none. */
    fun previewViews(key: String): RemoteViews? = loaded[key]?.previewViews

    private fun AppWidgetProviderInfo.isForHomeScreen(): Boolean =
        widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
            widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0

    private fun AppWidgetProviderInfo.toOption(serial: Long, user: UserHandle, isWorkProfile: Boolean, pm: PackageManager): AppWidgetOption {
        val atLeastS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val label = loadLabel(pm)
        return AppWidgetOption(
            provider = provider.flattenToString(),
            userSerial = serial,
            packageName = provider.packageName,
            appLabel = appLabel(provider.packageName, user, pm) ?: label,
            label = label,
            description = if (atLeastS) loadDescription(context)?.toString() else null,
            isWorkProfile = isWorkProfile,
            size = providerSize(),
            previewImage = previewImage,
            previewLayout = if (atLeastS) previewLayout else 0,
            icon = icon,
        )
    }

    /** Through `LauncherApps`, which finds a package in another profile too. */
    private fun appLabel(packageName: String, user: UserHandle, pm: PackageManager): String? = try {
        launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(pm).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Made here, off the main thread: the constructor looks the package up. */
    private fun previewViews(info: AppWidgetProviderInfo): RemoteViews? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || info.previewLayout == 0) return null
        return try {
            RemoteViews(info.provider.packageName, info.previewLayout)
        } catch (e: IllegalArgumentException) {
            // A package installed only in the work profile cannot be looked up from here; its image or icon stands in.
            FoxLog.w("No preview layout for ${info.provider}", e)
            null
        }
    }

    private class Loaded(val option: AppWidgetOption, val info: AppWidgetProviderInfo, val previewViews: RemoteViews?)
}

/** This provider's sizes in the shape [AppWidgetSizing] takes. */
fun AppWidgetProviderInfo.providerSize(): ProviderSize {
    val atLeastS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return ProviderSize(
        minWidth = minWidth,
        minHeight = minHeight,
        minResizeWidth = minResizeWidth,
        minResizeHeight = minResizeHeight,
        maxResizeWidth = if (atLeastS) maxResizeWidth else 0,
        maxResizeHeight = if (atLeastS) maxResizeHeight else 0,
        targetCellWidth = if (atLeastS) targetCellWidth else 0,
        targetCellHeight = if (atLeastS) targetCellHeight else 0,
        resizesHorizontally = resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
        resizesVertically = resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
    )
}

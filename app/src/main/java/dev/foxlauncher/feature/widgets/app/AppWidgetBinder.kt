package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.FoxLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppWidgetBindPort] on Fox's [FoxAppWidgetHost] (task 6.3, PLATFORM-NOTES AW). Main thread: these
 * are quick calls to the widget service, made as the user taps, as Launcher3 makes them.
 */
@Singleton
class AppWidgetBinder @Inject constructor(
    @param:ApplicationContext context: Context,
    private val host: FoxAppWidgetHost,
    private val appRepository: AppRepository,
) : AppWidgetBindPort {

    private val appWidgetManager = AppWidgetManager.getInstance(context)

    override fun allocateId(): Int = host.allocateAppWidgetId()

    override fun bindIfAllowed(pending: PendingAppWidget): BindAttempt {
        val provider = ComponentName.unflattenFromString(pending.provider) ?: return BindAttempt.FAILED
        val user = appRepository.userFor(pending.userSerial) ?: return BindAttempt.FAILED
        return try {
            if (appWidgetManager.bindAppWidgetIdIfAllowed(pending.appWidgetId, user, provider, null)) BindAttempt.BOUND else BindAttempt.ASK
        } catch (e: IllegalArgumentException) {
            // Uninstalled since the picker listed it.
            FoxLog.w("Cannot bind ${pending.provider}", e)
            BindAttempt.FAILED
        }
    }

    /** Asks the user to let Fox bind [pending]'s widget; the system answers `RESULT_OK` when they do. */
    fun bindIntent(pending: PendingAppWidget): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName.unflattenFromString(pending.provider))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, appRepository.userFor(pending.userSerial))

    /**
     * It has a configuration activity, unless the widget says that step may be skipped and done
     * later: reconfigurable with optional configuration (API 28), Launcher3's rule.
     */
    override fun needsConfiguration(appWidgetId: Int): Boolean {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return false
        if (info.configure == null) return false
        val skippable = AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE or AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
        return info.widgetFeatures and skippable != skippable
    }

    override fun deleteId(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)
}

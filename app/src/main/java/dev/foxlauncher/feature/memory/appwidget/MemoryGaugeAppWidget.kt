package dev.foxlauncher.feature.memory.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.os.ConfigurationCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.foxlauncher.R
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.feature.memory.MemoryFormat
import dev.foxlauncher.feature.memory.MemorySnapshot
import dev.foxlauncher.feature.memory.MemoryStats
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The RAM gauge for other launchers (task 5.5): the same figures as Fox's own gauge, as a classic
 * app widget. Android updates it every 30 minutes (the shortest period it honours, see
 * `res/xml/memory_gauge_appwidget_info.xml`); a tap refreshes it at once.
 */
class MemoryGaugeAppWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun stats(): MemoryStats

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, MemoryGaugeAppWidget::class.java)))
        }
    }

    /** Reads memory off the main thread, then shows it on every placed gauge. */
    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        dependencies.scope().launch {
            try {
                val snapshot = dependencies.stats().snapshot()
                manager.updateAppWidget(ids, MemoryGaugeViews.build(context, snapshot))
                FoxLog.d("RAM gauge app widget: ${ids.size} updated, ${MemoryFormat.percent(snapshot.usedFraction)} % in use")
            } catch (e: IllegalStateException) {
                FoxLog.w("RAM gauge app widget not updated", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "dev.foxlauncher.action.REFRESH_MEMORY_GAUGE"
    }
}

/** The gauge's RemoteViews for [snapshot] (task 5.5); a tap anywhere on it asks for a refresh. */
object MemoryGaugeViews {

    fun build(context: Context, snapshot: MemorySnapshot): RemoteViews {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.ROOT
        val percent = MemoryFormat.percent(snapshot.usedFraction)
        return RemoteViews(context.packageName, R.layout.memory_gauge_appwidget).apply {
            setProgressBar(R.id.memory_gauge_ring, PERCENT, percent, false)
            setTextViewText(R.id.memory_gauge_percent, context.getString(R.string.memory_percent, percent))
            setTextViewText(R.id.memory_gauge_free, context.getString(R.string.memory_gb_free, MemoryFormat.gb(snapshot.availableBytes, locale)))
            setOnClickPendingIntent(R.id.memory_gauge_root, refreshIntent(context))
        }
    }

    private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MemoryGaugeAppWidget::class.java).setAction(MemoryGaugeAppWidget.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val PERCENT = 100
}

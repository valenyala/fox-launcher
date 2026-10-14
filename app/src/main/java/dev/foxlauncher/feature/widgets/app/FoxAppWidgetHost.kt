package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.DeadObjectException
import android.os.TransactionTooLargeException
import android.util.SparseArray
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Fox's one `AppWidgetHost` for third-party widgets (task 6.1, PLATFORM-NOTES AW). One per process:
 * Hilt creates it when `HomeActivity.onCreate` injects it, and the activity drives listening, so
 * widgets get updates only while the home is started.
 *
 * It counts started instances rather than keeping a flag, as [dev.foxlauncher.feature.home.HomeVisibility]
 * does: during the hand-over of ADR-19 a second instance briefly exists. Main thread only, except
 * [providerInfo].
 */
@Singleton
class FoxAppWidgetHost @Inject constructor(
    @ApplicationContext context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AppWidgetHost(context, HOST_ID) {

    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private var started = 0
    private val views = SparseArray<FoxAppWidgetHostView>() // Guarded by itself: ids are also deleted off the main thread.
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A provider was installed, updated or removed, or Android deleted one of Fox's widgets (task 6.5). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()
    private val infos = ConcurrentHashMap<Int, AppWidgetProviderInfo>()

    /** From `HomeActivity.onStart`. */
    fun onHomeStarted() {
        if (started++ == 0) listen()
    }

    /** From `HomeActivity.onStop`. */
    fun onHomeStopped() {
        if (started == 0) return
        if (--started == 0) stopListening()
    }

    /** From `HomeActivity.onDestroy`: the views made by [createView] hold the activity's context. */
    public override fun clearViews() {
        super.clearViews()
        synchronized(views) { views.clear() }
    }

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        FoxAppWidgetHostView(context).also { synchronized(views) { views.put(appWidgetId, it) } }

    /** The latest view [createView] made for [appWidgetId] (task 6.4), or null. */
    fun viewOf(appWidgetId: Int): FoxAppWidgetHostView? = synchronized(views) { views[appWidgetId] }

    override fun deleteAppWidgetId(appWidgetId: Int) {
        super.deleteAppWidgetId(appWidgetId)
        synchronized(views) { views.remove(appWidgetId) }
        infos.remove(appWidgetId)
    }

    override fun onProvidersChanged() {
        _changes.tryEmit(Unit)
    }

    override fun onAppWidgetRemoved(appWidgetId: Int) {
        _changes.tryEmit(Unit)
    }

    /** The provider bound to [appWidgetId], read off the main thread; null when there is none (any more). */
    suspend fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? = withContext(io) {
        appWidgetManager.getAppWidgetInfo(appWidgetId)?.also { infos[appWidgetId] = it } ?: run {
            infos.remove(appWidgetId)
            null
        }
    }

    /** What the latest [providerInfo] found for [appWidgetId], without waiting. */
    fun cachedInfo(appWidgetId: Int): AppWidgetProviderInfo? = infos[appWidgetId]

    private fun listen() {
        try {
            startListening()
        } catch (e: RuntimeException) {
            // The pending updates of every widget come back in the one reply, which can outgrow the binder buffer.
            // The host is registered by then and each view gets its update when created; Launcher3 lets it slide too.
            if (e.cause !is TransactionTooLargeException && e.cause !is DeadObjectException) throw e
            FoxLog.w("AppWidgetHost started without its pending updates", e)
        }
    }

    companion object {
        /** Keys every widget id Fox allocates. Never change it: placed widgets would stop resolving. */
        const val HOST_ID = 0x0F0C
    }
}

/** The host, for the home grid's app widgets (task 6.4); null where none is provided, as in tests. */
val LocalAppWidgetHost = staticCompositionLocalOf<FoxAppWidgetHost?> { null }

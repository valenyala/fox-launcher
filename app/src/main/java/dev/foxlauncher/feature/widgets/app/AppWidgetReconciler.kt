package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.HomeDao
import dev.foxlauncher.core.data.db.HomeItemType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Fox's widget ids and the home's app widget items in step (task 6.5, PLATFORM-NOTES AW), as
 * [AppWidgetCleanup] plans: an item whose app was uninstalled leaves the home, and the id of an
 * item that left (removed by the user, or gone with its app) is freed. It runs at start, whenever
 * the home's set of app widgets changes, when the host hears of provider changes, and when a
 * package changes, a little after the last of a burst.
 */
@Singleton
class AppWidgetReconciler @Inject constructor(
    @param:ApplicationContext context: Context,
    private val dao: HomeDao,
    private val host: FoxAppWidgetHost,
    private val binder: AppWidgetBinder,
    private val appRepository: AppRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private var started = false

    /** From `HomeActivity.onCreate`; later calls do nothing. Main thread. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val placedIds = dao.observeAll()
                .map { rows -> rows.filter { it.type == HomeItemType.APP_WIDGET }.mapTo(HashSet()) { it.appWidgetId } }
                .distinctUntilChanged()
            merge(placedIds.map { }, host.changes, appRepository.packageChanges.map { })
                .debounce(SETTLE_MS)
                .collect { reconcile() }
        }
    }

    private suspend fun reconcile() = withContext(io) {
        val placed = dao.getAll()
            .filter { it.type == HomeItemType.APP_WIDGET && it.appWidgetId != null }
            .map { row ->
                val id = checkNotNull(row.appWidgetId)
                PlacedAppWidget(row.id, id, providerFound = appWidgetManager.getAppWidgetInfo(id) != null)
            }
        val plan = AppWidgetCleanup.plan(host.appWidgetIds.toSet(), placed, binder.pendingIds)
        if (plan.isEmpty) return@withContext
        FoxLog.i("App widgets: removing items ${plan.itemsToRemove}, freeing ids ${plan.idsToDelete}")
        if (plan.itemsToRemove.isNotEmpty()) dao.deleteByIds(plan.itemsToRemove)
        plan.idsToDelete.forEach(host::deleteAppWidgetId)
    }

    private companion object {
        const val SETTLE_MS = 500L
    }
}

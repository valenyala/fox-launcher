package dev.foxlauncher.feature.home

import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.DefaultDispatcher
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.HomeDao
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePosition
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * The home layout: Room rows in, [HomeLayout] out. Room runs its own queries off the main thread;
 * the mapping runs on the default dispatcher.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val dao: HomeDao,
    appRepository: AppRepository,
    @param:ApplicationScope scope: CoroutineScope,
    @param:DefaultDispatcher default: CoroutineDispatcher,
) {
    val layout: Flow<HomeLayout> =
        combine(dao.observeAll(), dao.observeFolders(), HomeLayoutMapper::toLayout).flowOn(default)

    init {
        // Drop items whose app was uninstalled. 2.8 replaces this with a full reconcile.
        scope.launch {
            appRepository.apps
                .combine(appRepository.isLoaded) { apps, loaded -> apps.takeIf { loaded } }
                .filterNotNull()
                .collect { apps -> pruneOrphans(apps.mapTo(HashSet()) { it.key }) }
        }
    }

    suspend fun addApp(entry: AppEntry, position: HomePosition): Long {
        val item = HomeItem.App(id = 0, position = position, component = entry.component, userSerial = entry.userSerial)
        return dao.insert(HomeLayoutMapper.toEntity(item, createdAt = System.currentTimeMillis()))
    }

    /** Persists new positions for [items] in one transaction. */
    suspend fun move(items: List<Pair<HomeItem, HomePosition>>) {
        val existing = dao.getAll().associateBy { it.id }
        val rows = items.mapNotNull { (item, position) ->
            existing[item.id]?.copy(
                container = position.container,
                page = position.page,
                col = position.col,
                row = position.row,
                spanX = position.spanX,
                spanY = position.spanY,
            )
        }
        dao.moveItems(rows)
    }

    suspend fun remove(itemIds: List<Long>) {
        dao.deleteByIds(itemIds)
    }

    suspend fun lastPageIndex(): Int = dao.maxPage() ?: 0

    private suspend fun pruneOrphans(installed: Set<String>) {
        val orphans = orphanedAppItemIds(dao.getAll(), installed)
        if (orphans.isEmpty()) return
        FoxLog.i("Removing ${orphans.size} home items of uninstalled apps")
        dao.deleteByIds(orphans)
    }
}

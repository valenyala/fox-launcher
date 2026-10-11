package dev.foxlauncher.feature.home

import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.DefaultDispatcher
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.data.db.HomeDao
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.feature.home.drag.DragResolver
import dev.foxlauncher.feature.home.drag.DropAction
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

    /** Persists a drop that [DragResolver] decided for the existing home item [item]. */
    suspend fun applyDrop(item: HomeItem, action: DropAction) {
        when (action) {
            is DropAction.MoveToPage -> {
                val cell = action.cell
                move(listOf(item to HomePosition(HomeContainer.PAGE, action.page, cell.col, cell.row, cell.spanX, cell.spanY)))
            }
            is DropAction.MoveToDock -> move(listOf(item to HomePosition(HomeContainer.DOCK, 0, action.slot, 0)))
            is DropAction.CreateFolder ->
                dao.createFolder(action.targetId, item.id, title = "", createdAt = System.currentTimeMillis())
            is DropAction.AddToFolder -> dao.addToFolder(action.folderItemId, item.id)
            is DropAction.Remove ->
                if (item is HomeItem.Folder) dao.deleteFolderWithItems(item.id) else dao.deleteByIds(listOf(item.id))
            DropAction.Cancel -> Unit
        }
    }

    suspend fun lastPageIndex(): Int = dao.maxPage() ?: 0

    private suspend fun pruneOrphans(installed: Set<String>) {
        val orphans = orphanedAppItemIds(dao.getAll(), installed)
        if (orphans.isEmpty()) return
        FoxLog.i("Removing ${orphans.size} home items of uninstalled apps")
        dao.deleteByIds(orphans)
    }
}

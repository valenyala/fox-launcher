package dev.foxlauncher.feature.home

import dev.foxlauncher.core.apps.AppRepository
import dev.foxlauncher.core.common.ApplicationScope
import dev.foxlauncher.core.common.DefaultDispatcher
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.core.data.db.FolderEntity
import dev.foxlauncher.core.data.db.HomeDao
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import dev.foxlauncher.feature.home.grid.GridSpec
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.home.grid.WidgetPlacement
import dev.foxlauncher.feature.home.drag.DragResolver
import dev.foxlauncher.feature.home.drag.DropAction
import dev.foxlauncher.feature.home.drag.NEW_ITEM_ID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home layout: Room rows in, [HomeLayout] out. Room runs its own queries off the main thread;
 * the mapping runs on the default dispatcher.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val dao: HomeDao,
    private val appRepository: AppRepository,
    private val defaultApps: DeviceDefaultApps,
    @param:ApplicationScope scope: CoroutineScope,
    @param:DefaultDispatcher default: CoroutineDispatcher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
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

    /**
     * Fills the dock of an empty home with the device's default apps (task 2.7) once the catalog has
     * loaded. A home with anything on it is left alone. Returns how many apps it placed.
     */
    suspend fun placeDefaultLayout(dockSlots: Int): Int {
        appRepository.isLoaded.first { it }
        val placements = withContext(io) { DefaultLayoutBuilder.build(defaultApps, appRepository.apps.value, dockSlots) }
        val now = System.currentTimeMillis()
        val rows = placements.map { (entry, position) ->
            val item = HomeItem.App(id = 0, position = position, component = entry.component, userSerial = entry.userSerial)
            HomeLayoutMapper.toEntity(item, createdAt = now)
        }
        if (!dao.insertIntoEmptyHome(rows)) return 0
        FoxLog.i("First run: placed ${rows.size} default apps in the dock")
        return rows.size
    }

    /** Persists new positions for [items] in one transaction. */
    suspend fun move(items: List<Pair<HomeItem, HomePosition>>) {
        val existing = dao.getAll().associateBy { it.id }
        val rows = items.mapNotNull { (item, position) ->
            existing[item.id]?.copy(
                container = position.container,
                // Leaving a folder: the row must not still claim to be inside it.
                folderId = existing[item.id]?.folderId?.takeIf { position.container == HomeContainer.FOLDER },
                page = position.page,
                col = position.col,
                row = position.row,
                spanX = position.spanX,
                spanY = position.spanY,
            )
        }
        dao.moveItems(rows)
    }

    /**
     * Places a new native widget of [kind] (task 5.1): [span] cells on [preferredPage] if they are
     * free there, else on the first page with room, else on a new page after the last (see
     * `WidgetPlacement`). Returns where it went.
     */
    suspend fun addNativeWidget(kind: String, span: Span, preferredPage: Int, grid: GridSpec): HomePosition =
        addWidget(span, preferredPage, grid) { HomeItem.NativeWidget(id = 0, position = it, kind = kind) }

    /** Places a bound app widget (task 6.3) as [addNativeWidget] places a native one. */
    suspend fun addAppWidget(appWidgetId: Int, provider: String, userSerial: Long, span: Span, preferredPage: Int, grid: GridSpec): HomePosition =
        addWidget(span, preferredPage, grid) { HomeItem.AppWidget(id = 0, position = it, appWidgetId, provider, userSerial) }

    private suspend fun addWidget(span: Span, preferredPage: Int, grid: GridSpec, item: (HomePosition) -> HomeItem): HomePosition {
        val onPages = dao.getAll().filter { it.container == HomeContainer.PAGE }
        val taken = onPages.groupBy { it.page }.mapValues { (_, rows) -> rows.map { CellRect(it.col, it.row, it.spanX, it.spanY) } }
        val pageCount = (onPages.maxOfOrNull { it.page } ?: 0) + 1
        val (page, cell) = WidgetPlacement.place(GridLayoutEngine(grid), taken, pageCount, preferredPage, span)
        val position = HomePosition(HomeContainer.PAGE, page, cell.col, cell.row, cell.spanX, cell.spanY)
        dao.insert(HomeLayoutMapper.toEntity(item(position), createdAt = System.currentTimeMillis()))
        return position
    }

    suspend fun remove(itemIds: List<Long>) {
        dao.deleteByIds(itemIds)
    }

    /**
     * Persists a drop that [DragResolver] decided. [item] is new (a drawer app) when its id is
     * [NEW_ITEM_ID]. Afterwards folders down to one item collapse, empty ones go, pages left empty
     * between others are closed up, and the shortcuts Fox keeps pinned follow what is on the home.
     */
    suspend fun applyDrop(item: HomeItem, action: DropAction) {
        if (action == DropAction.Cancel) return
        val pinnedBefore = pinnedShortcutIds(dao.getAll())
        if (item.id == NEW_ITEM_ID) addDropped(item, action) else moveDropped(item, action)
        closeFolders()
        closePageGaps()
        pinChanges(pinnedBefore, pinnedShortcutIds(dao.getAll())).forEach { (owner, ids) ->
            appRepository.pinShortcuts(owner.packageName, owner.userSerial, ids.toList())
        }
    }

    suspend fun renameFolder(folderId: Long, title: String) {
        dao.updateFolder(FolderEntity(id = folderId, title = title))
    }

    private suspend fun moveDropped(item: HomeItem, action: DropAction) {
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
            is DropAction.MoveInFolder -> reorderInFolder(item.id, action.rank)
            DropAction.Cancel -> Unit
        }
    }

    private suspend fun addDropped(item: HomeItem, action: DropAction) {
        val now = System.currentTimeMillis()
        val row = HomeLayoutMapper.toEntity(item, createdAt = now)
        when (action) {
            is DropAction.MoveToPage -> {
                val cell = action.cell
                dao.insert(
                    row.copy(
                        container = HomeContainer.PAGE,
                        page = action.page,
                        col = cell.col,
                        row = cell.row,
                        spanX = cell.spanX,
                        spanY = cell.spanY,
                    ),
                )
            }
            is DropAction.MoveToDock -> dao.insert(row.copy(container = HomeContainer.DOCK, page = 0, col = action.slot, row = 0))
            is DropAction.CreateFolder -> dao.insertIntoNewFolder(row, action.targetId, title = "", createdAt = now)
            is DropAction.AddToFolder -> dao.insertIntoFolder(row, action.folderItemId)
            // A new item has nothing to remove and is never inside a folder; the resolver never pairs these.
            is DropAction.Remove, is DropAction.MoveInFolder, DropAction.Cancel -> Unit
        }
    }

    private suspend fun reorderInFolder(itemId: Long, rank: Int) {
        val folderId = dao.getById(itemId)?.folderId ?: return
        // The same order the layout shows: by rank, oldest first on a tie.
        val children = dao.getFolderChildren(folderId).sortedWith(compareBy({ it.col }, { it.id }))
        val order = moveToRank(children.map { it.id }, itemId, rank)
        dao.moveItems(children.mapNotNull { row -> order.indexOf(row.id).takeIf { it != row.col }?.let { row.copy(col = it) } })
    }

    private suspend fun closeFolders() {
        val cleanup = folderCleanup(dao.getAll())
        if (!cleanup.isEmpty) dao.applyFolderCleanup(cleanup.promoted, cleanup.deletedItemIds, cleanup.deletedFolderIds)
    }

    private suspend fun closePageGaps() {
        val onPages = dao.getAll().filter { it.container == HomeContainer.PAGE }
        val renumbering = pageRenumbering(onPages.map { it.page })
        if (renumbering.isEmpty()) return
        dao.moveItems(onPages.mapNotNull { row -> renumbering[row.page]?.let { row.copy(page = it) } })
    }

    suspend fun lastPageIndex(): Int = dao.maxPage() ?: 0

    private suspend fun pruneOrphans(installed: Set<String>) {
        val orphans = orphanedAppItemIds(dao.getAll(), installed)
        if (orphans.isEmpty()) return
        FoxLog.i("Removing ${orphans.size} home items of uninstalled apps")
        dao.deleteByIds(orphans)
    }
}

package dev.foxlauncher.feature.home.drag

import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import kotlin.math.abs

/** What is being dragged, reduced to what drop rules care about. */
enum class DragKind {
    APP,
    SHORTCUT,
    FOLDER,
    WIDGET,
    ;

    /** Apps and shortcuts can be merged into folders; folders and widgets cannot. */
    val canJoinFolder: Boolean get() = this == APP || this == SHORTCUT
}

/**
 * The dragged item. [id] is null for something new (dragged in from the drawer), which has
 * nothing to remove and no cell of its own to vacate.
 */
data class DraggedItem(val id: Long?, val kind: DragKind, val spanX: Int = 1, val spanY: Int = 1)

/** An item already on a page or in the dock. Dock items use [cell]`.col` as their slot. */
data class PlacedItem(val id: Long, val kind: DragKind, val cell: CellRect)

/** Where the pointer was released. */
sealed interface DropTarget {
    data class Cell(val page: Int, val cell: CellRect) : DropTarget
    data class DockSlot(val slot: Int) : DropTarget
    data object RemoveZone : DropTarget
    /** Position [rank] in the open folder's grid (task 2.5). */
    data class FolderRank(val rank: Int) : DropTarget
    data object Outside : DropTarget
}

/** What a drop does. The drag layer applies it; nothing here touches the database. */
sealed interface DropAction {
    data class MoveToPage(val page: Int, val cell: CellRect) : DropAction
    data class MoveToDock(val slot: Int) : DropAction
    /** Merge the dragged app with the app/shortcut [targetId] into a new folder in its place. */
    data class CreateFolder(val targetId: Long) : DropAction
    /** Add the dragged item to the folder whose tile is the home item [folderItemId] (not `HomeItem.Folder.folderId`). */
    data class AddToFolder(val folderItemId: Long) : DropAction
    data class Remove(val id: Long) : DropAction
    /** Reorder the dragged item inside its open folder to position [rank]. */
    data class MoveInFolder(val rank: Int) : DropAction
    /** Snap back (existing item) or discard (new item). */
    data object Cancel : DropAction
}

/**
 * Pure drop rules (task 2.4): given the dragged item, the release target and what is already
 * there, decide the [DropAction]. The dragged item's own cell counts as free, so dropping an item
 * next to itself or back where it was always works.
 */
class DragResolver(private val engine: GridLayoutEngine) {

    fun resolve(
        dragged: DraggedItem,
        target: DropTarget,
        pageItems: List<PlacedItem>,
        dockItems: List<PlacedItem>,
    ): DropAction = when (target) {
        DropTarget.Outside -> DropAction.Cancel
        DropTarget.RemoveZone -> dragged.id?.let(DropAction::Remove) ?: DropAction.Cancel
        // Only a folder's own items are dragged while it is open, and only apps and shortcuts live there.
        is DropTarget.FolderRank ->
            if (dragged.id != null && dragged.kind.canJoinFolder) DropAction.MoveInFolder(target.rank) else DropAction.Cancel
        is DropTarget.Cell -> onPage(dragged, target, pageItems.filter { it.id != dragged.id })
        is DropTarget.DockSlot -> onDock(dragged, target.slot, dockItems.filter { it.id != dragged.id })
    }

    private fun onPage(dragged: DraggedItem, target: DropTarget.Cell, others: List<PlacedItem>): DropAction {
        val occupant = others.firstOrNull { it.cell.overlaps(CellRect(target.cell.col, target.cell.row)) }
        mergeInto(dragged, occupant)?.let { return it }

        val occ = engine.occupancy(others.map { it.cell })
        val wanted = CellRect(target.cell.col, target.cell.row, dragged.spanX, dragged.spanY)
        val cell = if (engine.isFree(wanted, occ)) wanted else engine.nearestFreeTo(wanted, dragged.spanX, dragged.spanY, occ)
        return cell?.let { DropAction.MoveToPage(target.page, it) } ?: DropAction.Cancel
    }

    private fun onDock(dragged: DraggedItem, slot: Int, others: List<PlacedItem>): DropAction {
        // The dock is 1×1 only.
        if (dragged.kind == DragKind.WIDGET || dragged.spanX != 1 || dragged.spanY != 1) return DropAction.Cancel
        if (slot !in 0 until engine.spec.dockSlots) return DropAction.Cancel
        val occupant = others.firstOrNull { it.cell.col == slot }
        mergeInto(dragged, occupant)?.let { return it }
        if (occupant == null) return DropAction.MoveToDock(slot)

        val taken = others.mapTo(HashSet()) { it.cell.col }
        val nearest = (0 until engine.spec.dockSlots)
            .filter { it !in taken }
            .minWithOrNull(compareBy({ abs(it - slot) }, { it }))
        return nearest?.let(DropAction::MoveToDock) ?: DropAction.Cancel
    }

    /** Folder rules shared by pages and dock; null when the drop is not a merge. */
    private fun mergeInto(dragged: DraggedItem, occupant: PlacedItem?): DropAction? {
        if (occupant == null || !dragged.kind.canJoinFolder) return null
        return when {
            occupant.kind == DragKind.FOLDER -> DropAction.AddToFolder(occupant.id)
            occupant.kind.canJoinFolder && occupant.cell.spanX == 1 && occupant.cell.spanY == 1 ->
                DropAction.CreateFolder(occupant.id)
            else -> null
        }
    }
}

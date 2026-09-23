package dev.foxlauncher.feature.home.drag

import dev.foxlauncher.feature.home.drag.DropAction.AddToFolder
import dev.foxlauncher.feature.home.drag.DropAction.Cancel
import dev.foxlauncher.feature.home.drag.DropAction.CreateFolder
import dev.foxlauncher.feature.home.drag.DropAction.MoveToDock
import dev.foxlauncher.feature.home.drag.DropAction.MoveToPage
import dev.foxlauncher.feature.home.drag.DropAction.Remove
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import dev.foxlauncher.feature.home.grid.GridSpec
import kotlin.test.assertEquals
import org.junit.Test

class DragResolverTest {

    private val resolver = DragResolver(GridLayoutEngine(GridSpec(cols = 4, rows = 5, dockSlots = 4)))

    private val app = DraggedItem(id = 1, kind = DragKind.APP)
    private val newApp = DraggedItem(id = null, kind = DragKind.APP)

    @Test
    fun dropOnAFreeCellMovesThere() {
        assertEquals(MoveToPage(0, CellRect(2, 3)), resolve(app, cell(2, 3)))
    }

    @Test
    fun dropBackOnItsOwnCellIsAMoveNotAMerge() {
        val self = PlacedItem(1, DragKind.APP, CellRect(2, 3))
        assertEquals(MoveToPage(0, CellRect(2, 3)), resolve(app, cell(2, 3), page = listOf(self)))
    }

    @Test
    fun appOnAppCreatesAFolder() {
        val target = PlacedItem(7, DragKind.APP, CellRect(1, 1))
        assertEquals(CreateFolder(7), resolve(app, cell(1, 1), page = listOf(target)))
    }

    @Test
    fun shortcutOnAppCreatesAFolder() {
        val target = PlacedItem(7, DragKind.APP, CellRect(1, 1))
        assertEquals(CreateFolder(7), resolve(DraggedItem(1, DragKind.SHORTCUT), cell(1, 1), page = listOf(target)))
    }

    @Test
    fun appOnFolderJoinsIt() {
        val folder = PlacedItem(9, DragKind.FOLDER, CellRect(1, 1))
        assertEquals(AddToFolder(9), resolve(app, cell(1, 1), page = listOf(folder)))
    }

    @Test
    fun folderOnAppDoesNotNestAndMovesToTheNearestFreeCell() {
        val target = PlacedItem(7, DragKind.APP, CellRect(1, 1))
        val folder = DraggedItem(2, DragKind.FOLDER)
        assertEquals(MoveToPage(0, CellRect(1, 0)), resolve(folder, cell(1, 1), page = listOf(target)))
    }

    @Test
    fun appOnAWidgetMovesToTheNearestFreeCell() {
        // (2,1) and (1,2) are both one step away; the upper one wins.
        val widget = PlacedItem(5, DragKind.WIDGET, CellRect(0, 0, spanX = 2, spanY = 2))
        assertEquals(MoveToPage(0, CellRect(2, 1)), resolve(app, cell(1, 1), page = listOf(widget)))
    }

    @Test
    fun widgetThatDoesNotFitAtTheTargetShiftsToFit() {
        val widget = DraggedItem(3, DragKind.WIDGET, spanX = 2, spanY = 2)
        // Top-left at (3,4) would hang off the grid; nearest fitting block is (2,3).
        assertEquals(MoveToPage(1, CellRect(2, 3, 2, 2)), resolve(widget, cell(3, 4, page = 1)))
    }

    @Test
    fun fullPageCancels() {
        val wall = PlacedItem(5, DragKind.WIDGET, CellRect(0, 0, spanX = 4, spanY = 5))
        assertEquals(Cancel, resolve(app, cell(0, 0), page = listOf(wall)))
    }

    @Test
    fun removeZoneRemovesExistingItemsAndDiscardsNewOnes() {
        assertEquals(Remove(1), resolve(app, DropTarget.RemoveZone))
        assertEquals(Cancel, resolve(newApp, DropTarget.RemoveZone))
    }

    @Test
    fun outsideCancels() {
        assertEquals(Cancel, resolve(app, DropTarget.Outside))
    }

    @Test
    fun newItemFromTheDrawerLandsLikeAnyOther() {
        assertEquals(MoveToPage(0, CellRect(0, 0)), resolve(newApp, cell(0, 0)))
    }

    @Test
    fun dropOnAnEmptyDockSlotMovesThere() {
        assertEquals(MoveToDock(2), resolve(app, DropTarget.DockSlot(2)))
    }

    @Test
    fun appOnDockAppCreatesAFolder() {
        val docked = PlacedItem(8, DragKind.APP, CellRect(2, 0))
        assertEquals(CreateFolder(8), resolve(app, DropTarget.DockSlot(2), dock = listOf(docked)))
    }

    @Test
    fun folderOnATakenDockSlotGoesToTheNearestFreeSlotPreferringTheLeft() {
        val docked = listOf(PlacedItem(8, DragKind.APP, CellRect(2, 0)))
        assertEquals(MoveToDock(1), resolve(DraggedItem(2, DragKind.FOLDER), DropTarget.DockSlot(2), dock = docked))
    }

    @Test
    fun widgetsNeverGoInTheDock() {
        assertEquals(Cancel, resolve(DraggedItem(3, DragKind.WIDGET), DropTarget.DockSlot(0)))
    }

    @Test
    fun fullDockCancelsANonMergingDrop() {
        val docked = (0 until 4).map { PlacedItem(10L + it, DragKind.FOLDER, CellRect(it, 0)) }
        assertEquals(Cancel, resolve(DraggedItem(2, DragKind.FOLDER), DropTarget.DockSlot(1), dock = docked))
    }

    @Test
    fun dockSlotOutOfRangeCancels() {
        assertEquals(Cancel, resolve(app, DropTarget.DockSlot(4)))
    }

    private fun cell(col: Int, row: Int, page: Int = 0) = DropTarget.Cell(page, CellRect(col, row))

    private fun resolve(
        dragged: DraggedItem,
        target: DropTarget,
        page: List<PlacedItem> = emptyList(),
        dock: List<PlacedItem> = emptyList(),
    ) = resolver.resolve(dragged, target, page, dock)
}

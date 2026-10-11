package dev.foxlauncher.feature.home.drag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.toSize
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomeLayout
import dev.foxlauncher.core.model.HomePosition
import dev.foxlauncher.feature.home.folderColumns
import dev.foxlauncher.feature.home.grid.CellRect
import dev.foxlauncher.feature.home.grid.GridLayoutEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long the overlay takes to glide into its cell (or back) after a drop. */
private const val SETTLE_MS = 180

/**
 * Id of a dragged item that is not on the home screen yet (an app from the drawer). Room assigns
 * a real id on insert, so no stored item ever has it.
 */
const val NEW_ITEM_ID: Long = 0L

/** A drawer app as a home item that does not exist yet, ready to be dragged in. */
fun newAppItem(entry: AppEntry): HomeItem.App = HomeItem.App(
    id = NEW_ITEM_ID,
    position = HomePosition(HomeContainer.PAGE, page = 0, col = 0, row = 0),
    component = entry.component,
    userSerial = entry.userSerial,
)

/** Where releasing now would put the dragged item, and what that would do. */
@Immutable
data class DropPreview(val target: DropTarget, val action: DropAction)

/**
 * Home drag and drop (task 2.4). An item's long press calls [start]; a drawer app's long press
 * calls [arm], which starts the drag once the finger moves. From then on [homeDragLayer] owns the
 * gesture, moves the overlay and drops on release. The finger is tracked
 * at the root rather than on the item, so the drag survives the source page leaving composition
 * during an edge flip.
 *
 * Geometry (the drag layer, the composed pages, the dock) is registered from layout callbacks into
 * plain fields: it is only read while dragging and must not cause recomposition.
 */
@Stable
class HomeDragState internal constructor(
    private val scope: CoroutineScope,
    private val layout: () -> HomeLayout,
    private val engine: () -> GridLayoutEngine,
    private val onDrop: (HomeItem, DropAction) -> Unit,
) {
    /** The dragged item. It stays set while the overlay settles, so its home view stays hidden. */
    var dragging: HomeItem? by mutableStateOf(null)
        private set

    /** Where a release would land right now; null when not dragging or while settling. */
    var preview: DropPreview? by mutableStateOf(null)
        private set

    /** The action of the last release, kept while the overlay settles (an open folder keeps its new order). */
    var droppedAction: DropAction? by mutableStateOf(null)
        private set

    /** The finger in drag-layer coordinates; only updated while dragging. */
    var pointer: Offset by mutableStateOf(Offset.Zero)
        private set

    /** The dragged item's size at pick-up, in px. */
    var itemSize: Size by mutableStateOf(Size.Zero)
        private set

    private var grabOffset = Offset.Zero

    /** The overlay gliding to its destination after a release; a fresh one per drop. */
    private var settle: Animatable<Offset, AnimationVector2D>? by mutableStateOf(null)

    /** True between release and the overlay reaching its destination. */
    val isSettling: Boolean get() = settle != null

    /** True while the dragged item is a drawer app that is not on the home screen yet. */
    val isNewItem: Boolean get() = dragging?.id == NEW_ITEM_ID

    /** The overlay's scale once it has settled: destination size over [itemSize] (pages shrink while dragging). */
    var settleScale: Float by mutableFloatStateOf(1f)
        private set

    /** Top-left of the overlay in drag-layer coordinates. */
    val overlayTopLeft: Offset get() = settle?.value ?: (pointer - grabOffset)

    private var layer: LayoutCoordinates? = null
    private var dock: LayoutCoordinates? = null
    private val pages = HashMap<Int, LayoutCoordinates>()
    private var removeZone: LayoutCoordinates? = null

    /** The open folder (task 2.5), its card and its item grid; see [setOpenFolder]. */
    private var openFolder: HomeItem.Folder? = null
    private var folderCard: LayoutCoordinates? = null
    private var folderGrid: LayoutCoordinates? = null

    /** The finger currently down when no drag runs, so a long press knows where it is. */
    private var pressed: PointerId? = null
    private var pressedAt = Offset.Zero

    /** The pointer a drag owns. Its events are swallowed until it lifts, even after [cancel]. */
    private var owned: PointerId? = null

    /** An [arm]ed item waiting for the finger to move, with where it was pressed. */
    private var armed: HomeItem? = null
    private var armedAt = Offset.Zero
    private var onArmedDragStart: () -> Unit = {}

    fun registerPage(index: Int, coordinates: LayoutCoordinates) {
        pages[index] = coordinates
    }

    fun unregisterPage(index: Int) {
        pages.remove(index)
    }

    fun registerDock(coordinates: LayoutCoordinates) {
        dock = coordinates
    }

    fun registerRemoveZone(coordinates: LayoutCoordinates) {
        removeZone = coordinates
    }

    fun registerFolderCard(coordinates: LayoutCoordinates) {
        folderCard = coordinates
    }

    fun registerFolderGrid(coordinates: LayoutCoordinates) {
        folderGrid = coordinates
    }

    /**
     * The folder whose sheet is showing, or null. Set after each composition; a change re-resolves
     * the target, because the sheet closing under a still finger reveals the home grid.
     */
    fun setOpenFolder(folder: HomeItem.Folder?) {
        if (folder == openFolder) return
        openFolder = folder
        refresh()
    }

    /** True while a drag's finger is off the open folder's card: held there, the folder closes. */
    fun isOutsideOpenFolder(): Boolean {
        if (dragging == null || isSettling) return false
        val layer = layer?.takeIf { it.isAttached } ?: return false
        val card = openCard() ?: return false
        return !layer.localBoundingBoxOf(card).contains(pointer)
    }

    /**
     * Picks [item] up from where [itemCoordinates] places it. Ignored when no finger is down (an
     * accessibility long click has nothing to follow) or a drag is already running.
     */
    fun start(item: HomeItem, itemCoordinates: LayoutCoordinates) {
        val layer = layer?.takeIf { it.isAttached } ?: return
        val finger = pressed ?: return
        if (dragging != null || owned != null || !itemCoordinates.isAttached) return
        owned = finger
        begin(item, pressedAt, itemCoordinates.size.toSize(), pressedAt - layer.localPositionOf(itemCoordinates, Offset.Zero))
    }

    /**
     * Readies a drag of [item] (a drawer app, see [newAppItem]) that only begins once the finger
     * moves past touch slop, so lifting without moving leaves the drawer's popup open.
     * [onDragStart] runs when it begins; the caller gets the drawer out of the way there.
     */
    fun arm(item: HomeItem, onDragStart: () -> Unit) {
        val finger = pressed ?: return
        if (dragging != null || owned != null) return
        owned = finger
        armed = item
        armedAt = pressedAt
        onArmedDragStart = onDragStart
    }

    private fun begin(item: HomeItem, at: Offset, size: Size, grab: Offset) {
        itemSize = size
        grabOffset = grab
        pointer = at
        dragging = item
        preview = computePreview()
    }

    /** Drops nothing and puts the item back: rotation, Home, anything that invalidates the drag. */
    fun cancel() {
        armed = null
        dragging = null
        preview = null
        droppedAction = null
        settle = null
    }

    /** Re-resolves the target when the pages moved under a still finger (edge flip). */
    fun refresh() {
        if (dragging != null && !isSettling) preview = computePreview()
    }

    /**
     * -1 or 1 while the finger rests within [edgePx] of the left or right screen edge above the
     * dock, else 0.
     */
    fun edgeDirection(edgePx: Float): Int {
        if (dragging == null || isSettling || openCard() != null) return 0
        val layer = layer?.takeIf { it.isAttached } ?: return 0
        val dockTop = dockBounds(layer)?.top ?: Float.POSITIVE_INFINITY
        val finger = pointer
        return when {
            finger.y >= dockTop -> 0
            finger.x < edgePx -> -1
            finger.x > layer.size.width - edgePx -> 1
            else -> 0
        }
    }

    /** The cell or slot the current [preview] would fill, in drag-layer coordinates. */
    fun highlightBounds(): Rect? {
        val preview = preview ?: return null
        return when (val action = preview.action) {
            is DropAction.MoveToPage -> cellBounds(action.page, action.cell)
            is DropAction.MoveToDock -> slotBounds(action.slot)
            is DropAction.CreateFolder, is DropAction.AddToFolder -> targetBounds(preview.target)
            is DropAction.MoveInFolder -> folderCellBounds(action.rank)
            is DropAction.Remove, DropAction.Cancel -> null
        }
    }

    internal fun attachLayer(coordinates: LayoutCoordinates) {
        layer = coordinates
    }

    /** Every pointer event of the home screen, in the Initial pass, before anything below sees it. */
    internal fun onPointerEvent(event: PointerEvent, touchSlop: Float) {
        val ownedId = owned
        if (ownedId == null) {
            val down = event.changes.firstOrNull { it.pressed }
            pressed = down?.id
            if (down != null) pressedAt = down.position
            return
        }
        // A drag owns this gesture: the pager, the drawer swipe and the items must not react.
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull { it.id == ownedId }
        val pending = armed
        if (change == null || !change.pressed) {
            owned = null
            pressed = null
            armed = null
            if (dragging != null && !isSettling) drop()
        } else if (pending != null) {
            if ((change.position - armedAt).getDistance() > touchSlop) {
                armed = null
                // A drawer cell is not a home cell: carry it at home size, centred on the finger.
                val size = homeCellSize() ?: Size.Zero
                begin(pending, change.position, size, Offset(size.width / 2, size.height / 2))
                onArmedDragStart()
            }
        } else if (dragging != null && !isSettling) {
            pointer = change.position
            preview = computePreview()
        }
    }

    private fun drop() {
        val item = dragging ?: return
        val dropped = computePreview()
        val action = dropped?.action ?: DropAction.Cancel
        preview = null
        droppedAction = action
        onDrop(item, action)
        val bounds = destinationBounds(item, action, dropped?.target)
        if (bounds == null || itemSize.width <= 0f) {
            dragging = null
            droppedAction = null
            return
        }
        settleScale = bounds.width / itemSize.width
        // Centred on the destination; the overlay scales around its centre.
        val destination = bounds.center - Offset(itemSize.width / 2, itemSize.height / 2)
        val glide = Animatable(overlayTopLeft, Offset.VectorConverter)
        settle = glide
        scope.launch {
            try {
                glide.animateTo(destination, tween(SETTLE_MS))
            } finally {
                // Cancelled mid-glide: cancel() has already reset everything.
                if (settle === glide) {
                    settle = null
                    dragging = null
                    droppedAction = null
                }
            }
        }
    }

    /** Where the item ends up, in drag-layer coordinates; null when there is nothing to glide to. */
    private fun destinationBounds(item: HomeItem, action: DropAction, target: DropTarget?): Rect? =
        when (action) {
            is DropAction.MoveToPage -> cellBounds(action.page, action.cell)
            is DropAction.MoveToDock -> slotBounds(action.slot)
            is DropAction.CreateFolder, is DropAction.AddToFolder -> target?.let(::targetBounds)
            is DropAction.MoveInFolder -> folderCellBounds(action.rank)
            DropAction.Cancel -> sourceBounds(item)
            is DropAction.Remove -> null
        }

    private fun computePreview(): DropPreview? {
        val item = dragging ?: return null
        val target = locate(item)
        val layout = layout()
        val page = (target as? DropTarget.Cell)?.page
        val action = DragResolver(engine()).resolve(
            dragged = item.toDragged(),
            target = target,
            pageItems = page?.let { layout.pages.getOrNull(it) }?.items.orEmpty().map { it.toPlaced() },
            dockItems = layout.dock.items.map { it.toPlaced() },
        )
        return DropPreview(target, action)
    }

    /**
     * The target under the overlay's centre. Below the top of the dock it is a dock slot (or
     * Outside, under the dock). Above it, it is the nearest cell of whichever page is under the
     * centre, clamped into the grid, so the gaps around a page still map to its edge cells.
     */
    private fun locate(item: HomeItem): DropTarget {
        val layer = layer?.takeIf { it.isAttached } ?: return DropTarget.Outside
        val engine = engine()
        // The remove zone goes by the finger: it is a strip the finger is aimed at, not a cell.
        removeZone?.takeIf { it.isAttached }?.let { zone ->
            if (layer.localBoundingBoxOf(zone).contains(pointer)) return DropTarget.RemoveZone
        }
        val center = overlayTopLeft + Offset(itemSize.width / 2, itemSize.height / 2)
        // An open folder covers the home: its grid, or nothing until the finger has left long enough to close it.
        openCard()?.let { card ->
            if (!layer.localBoundingBoxOf(card).contains(pointer)) return DropTarget.Outside
            return folderRankAt(layer, center) ?: DropTarget.Outside
        }

        dockBounds(layer)?.let { bounds ->
            if (center.y >= bounds.top) {
                if (!bounds.contains(center)) return DropTarget.Outside
                return engine.dockSlotAt(center.x - bounds.left, bounds.width)
                    ?.let(DropTarget::DockSlot) ?: DropTarget.Outside
            }
        }

        val (index, coordinates) = pages.entries
            .filter { it.value.isAttached }
            .map { it.key to it.value }
            .filter { (_, coordinates) -> layer.localBoundingBoxOf(coordinates).width > 0f }
            .minByOrNull { (_, coordinates) ->
                val visible = layer.localBoundingBoxOf(coordinates)
                maxOf(visible.left - center.x, center.x - visible.right, 0f)
            } ?: return DropTarget.Outside
        val size = coordinates.size
        val cellW = size.width.toFloat() / engine.spec.cols
        val cellH = size.height.toFloat() / engine.spec.rows
        // The centre of the item's top-left cell: back off half of every extra cell it spans.
        val anchor = coordinates.localPositionOf(layer, center) -
            Offset((item.position.spanX - 1) * cellW / 2, (item.position.spanY - 1) * cellH / 2)
        val cell = engine.cellAt(
            anchor.x.coerceIn(0f, size.width - 1f),
            anchor.y.coerceIn(0f, size.height - 1f),
            cellW,
            cellH,
        ) ?: return DropTarget.Outside
        return DropTarget.Cell(index, cell)
    }

    private fun targetBounds(target: DropTarget): Rect? = when (target) {
        is DropTarget.Cell -> cellBounds(target.page, CellRect(target.cell.col, target.cell.row))
        is DropTarget.DockSlot -> slotBounds(target.slot)
        is DropTarget.FolderRank -> folderCellBounds(target.rank)
        DropTarget.RemoveZone, DropTarget.Outside -> null
    }

    private fun sourceBounds(item: HomeItem): Rect? {
        if (item.id == NEW_ITEM_ID) return null
        val p = item.position
        return when (p.container) {
            HomeContainer.PAGE -> cellBounds(p.page, CellRect(p.col, p.row, p.spanX, p.spanY))
            HomeContainer.DOCK -> slotBounds(p.col)
            // Back to its place in the open folder, if that is where it came from.
            HomeContainer.FOLDER -> openFolder?.items?.indexOfFirst { it.id == item.id }?.takeIf { it >= 0 }?.let(::folderCellBounds)
        }
    }

    /**
     * [cell] of page [page] in drag-layer coordinates; null when that page is not composed. Both
     * corners are mapped, so a page scaled while dragging still gives its on-screen cell.
     */
    private fun cellBounds(page: Int, cell: CellRect): Rect? {
        val layer = layer?.takeIf { it.isAttached } ?: return null
        val coordinates = pages[page]?.takeIf { it.isAttached } ?: return null
        val engine = engine()
        val px = engine.pxOf(
            cell,
            coordinates.size.width.toFloat() / engine.spec.cols,
            coordinates.size.height.toFloat() / engine.spec.rows,
        )
        return Rect(
            layer.localPositionOf(coordinates, Offset(px.left, px.top)),
            layer.localPositionOf(coordinates, Offset(px.left + px.width, px.top + px.height)),
        )
    }

    private fun slotBounds(slot: Int): Rect? {
        val layer = layer?.takeIf { it.isAttached } ?: return null
        val bounds = dockBounds(layer) ?: return null
        val slotW = bounds.width / engine().spec.dockSlots
        return Rect(Offset(bounds.left + slot * slotW, bounds.top), Size(slotW, bounds.height))
    }

    private fun openCard(): LayoutCoordinates? =
        folderCard?.takeIf { openFolder != null && it.isAttached }

    /** The open folder's grid shape: its coordinates, columns, rows and item count. */
    private fun folderShape(): FolderShape? {
        val grid = folderGrid?.takeIf { it.isAttached && openCard() != null } ?: return null
        val count = openFolder?.items?.size?.takeIf { it > 0 } ?: return null
        val cols = folderColumns(count)
        return FolderShape(grid, cols, (count + cols - 1) / cols, count)
    }

    private fun folderRankAt(layer: LayoutCoordinates, point: Offset): DropTarget.FolderRank? {
        val shape = folderShape() ?: return null
        val local = shape.grid.localPositionOf(layer, point)
        val col = (local.x / (shape.grid.size.width.toFloat() / shape.cols)).toInt().coerceIn(0, shape.cols - 1)
        val row = (local.y / (shape.grid.size.height.toFloat() / shape.rows)).toInt().coerceIn(0, shape.rows - 1)
        return DropTarget.FolderRank((row * shape.cols + col).coerceAtMost(shape.count - 1))
    }

    /** Cell [rank] of the open folder's grid, in drag-layer coordinates. */
    private fun folderCellBounds(rank: Int): Rect? {
        val layer = layer?.takeIf { it.isAttached } ?: return null
        val shape = folderShape() ?: return null
        val w = shape.grid.size.width.toFloat() / shape.cols
        val h = shape.grid.size.height.toFloat() / shape.rows
        val left = rank % shape.cols * w
        val top = rank / shape.cols * h
        return Rect(
            layer.localPositionOf(shape.grid, Offset(left, top)),
            layer.localPositionOf(shape.grid, Offset(left + w, top + h)),
        )
    }

    private class FolderShape(val grid: LayoutCoordinates, val cols: Int, val rows: Int, val count: Int)

    /** One home cell, measured from any composed page. */
    private fun homeCellSize(): Size? {
        val page = pages.values.firstOrNull { it.isAttached } ?: return null
        val spec = engine().spec
        return Size(page.size.width.toFloat() / spec.cols, page.size.height.toFloat() / spec.rows)
    }

    private fun dockBounds(layer: LayoutCoordinates): Rect? =
        dock?.takeIf { it.isAttached }?.let { layer.localBoundingBoxOf(it) }
}

@Composable
fun rememberHomeDragState(
    layout: HomeLayout,
    engine: GridLayoutEngine,
    onDrop: (HomeItem, DropAction) -> Unit,
): HomeDragState {
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    val currentEngine by rememberUpdatedState(engine)
    val currentOnDrop by rememberUpdatedState(onDrop)
    return remember {
        HomeDragState(scope, { currentLayout }, { currentEngine }, { item, action -> currentOnDrop(item, action) })
    }
}

/**
 * Makes this node the drag layer: overlay and highlight coordinates are relative to it, and it
 * watches every pointer event in the Initial pass so a running drag wins over the pager and the
 * drawer swipe below it.
 */
fun Modifier.homeDragLayer(state: HomeDragState): Modifier = this
    .onPlaced(state::attachLayer)
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) state.onPointerEvent(awaitPointerEvent(PointerEventPass.Initial), viewConfiguration.touchSlop)
        }
    }

private val HomeItem.dragKind: DragKind
    get() = when (this) {
        is HomeItem.App -> DragKind.APP
        is HomeItem.Shortcut -> DragKind.SHORTCUT
        is HomeItem.Folder -> DragKind.FOLDER
        is HomeItem.AppWidget, is HomeItem.NativeWidget -> DragKind.WIDGET
    }

private fun HomeItem.toDragged() =
    DraggedItem(id.takeIf { it != NEW_ITEM_ID }, dragKind, position.spanX, position.spanY)

private fun HomeItem.toPlaced() =
    PlacedItem(id, dragKind, CellRect(position.col, position.row, position.spanX, position.spanY))

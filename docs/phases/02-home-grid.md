# Phase 2 — Home grid

**Goal:** multi-page home screen with a dock, drag-and-drop placement, folders and app shortcuts, persisted in Room.
**Prereqs:** Phase 1 complete.
**Definition of done:** user can add apps from the drawer to any page/dock, move them, make folders, use app shortcuts, and
all of it survives a reboot; removing an app removes its items.
**Out of scope:** app widgets (Phase 6), native widgets (Phase 5), gesture remapping (Phase 3).

Read `PLATFORM-NOTES.md` sections A (shortcuts), C, S (uninstall, app info) before starting.

## 2.1 Room schema + repository

- **Why:** the layout is the user's most valuable state; get the schema right first.
- **Do:**
  - Entities: `HomeItemEntity` exactly as described in `CLAUDE.md` (Persistence shape) plus `createdAt`; `FolderEntity(id, title)`.
    Index on `(container, page)`. Foreign key folder→items is not needed; items store `folderId` and `container = FOLDER`.
  - `HomeDao`: `observeAll(): Flow<List<HomeItemEntity>>`, `insert`, `update`, `delete`, `deleteByPackage(pkg, serial)`,
    `moveItems(list)` in a `@Transaction`, `maxPage()`.
  - `feature/home/HomeRepository`: maps entities to `core/model/HomeItem` sealed class (`App`, `Shortcut`, `Folder(items)`,
    `AppWidget`, `NativeWidget`) grouped as `HomeLayout(pages: ImmutableList<Page>, dock: Page)`. Also listens to
    `AppRepository.apps` and deletes items whose app no longer exists (2.8 refines this).
  - Migration from Phase 0's placeholder schema: version 2 with `AutoMigration` if possible, else manual `Migration(1,2)`.
- **Files:** `core/data/db/*`, `feature/home/HomeRepository.kt`, `core/model/HomeItem.kt`, `app/schemas/.../2.json`.
- **Verify:** build; a unit test for the entity↔model mapper (pure).
- **Done when:** schema 2 JSON committed and mapper test passes.

## 2.2 GridLayoutEngine (pure Kotlin)

- **Why:** all placement math in one tested place so drag-and-drop stays sane.
- **Do:** `feature/home/grid/GridSpec(cols, rows, dockSlots)`, `CellRect(col,row,spanX,spanY)`, `GridLayoutEngine(spec)` with:
  `occupancy(items): Array<BooleanArray>`, `isFree(rect, occ)`, `firstFree(spanX, spanY, occ): CellRect?`, `cellAt(px, py, cellW, cellH)`,
  `pxOf(cell, cellW, cellH)`, `nearestFreeTo(target, spanX, spanY, occ)` (spiral search), `dockSlotAt(px)`. Tests: empty grid,
  full grid, spans, edges, nearest search order.
- **Files:** `feature/home/grid/*` (no Android imports), `test/.../GridLayoutEngineTest.kt`.
- **Verify:** `./gradlew :app:testDebugUnitTest --tests "*GridLayoutEngineTest"`.
- **Done when:** ≥ 12 test cases pass.

## 2.3 Page + dock rendering, wallpaper offsets

- **Why:** visible home screen.
- **Do:**
  - `HomeScreen` = `Column { HorizontalPager(pages) ; PageIndicator ; Dock }` inside the insets; each page is a custom
    `Layout` placing `HomeItemView`s by cell from `GridLayoutEngine.pxOf`. Cell size computed from available size and
    `GridSpec` (default 5×6 phones; `GridSpec` in settings). Item view: icon + label (label toggle in settings).
  - Tapping an item: App → launch with reveal; Shortcut → `startShortcut`; Folder → 2.5.
  - Report wallpaper offsets as the pager scrolls: `WallpaperManager.setWallpaperOffsetSteps` and `setWallpaperOffsets(window.decorView.windowToken, offset, 0f)`
    from a `snapshotFlow { pagerState.currentPage + currentPageOffsetFraction }` collector, throttled to frame rate.
  - Default page (index 0 for now; setting later). `onNewIntent` → animate to default page and close folder/drawer.
- **Files:** `feature/home/HomeScreen.kt`, `feature/home/HomePage.kt`, `feature/home/Dock.kt`, `feature/home/HomeItemView.kt`.
- **Verify:** build; device: pages swipe, wallpaper parallax moves if the live wallpaper supports it (system default does).
- **Done when:** items from a hand-inserted Room row render in the right cell (use a temporary debug menu action "add test items",
  remove after 2.7).

## 2.4 Drag & drop

- **Why:** core home-screen interaction. Biggest task in the phase; split across sessions using the sub-bullets.
- **Do:**
  - `HomeDragState` (hoisted, `@Stable`): `dragging: DragItem?`, `pointerPx`, `targetCell`, `sourcePage`, `isOverRemove`.
  - Pick-up: long-press on an item (`detectDragGesturesAfterLongPress` on the item, Initial pass so it wins over the pager),
    haptic `LONG_PRESS`, the item disappears from the grid and a `DragOverlay` (drawn in a `Box` above the pager) follows the
    pointer with a slight scale-up.
  - Move: compute `targetCell` with `GridLayoutEngine.cellAt` on the page under the pointer; highlight it; if occupied by a
    1×1 app, show folder-merge highlight; if near left/right edge for > 400 ms, `animateScrollToPage(±1)`.
  - Drop: free cell → `moveItems`; on app → create folder (2.5); on dock slot → move to dock (dock is 1×1 only); outside
    grid → snap back. Top "Remove" zone appears while dragging; dropping there deletes the item (folders: confirm dialog).
  - Drag from drawer: long-press in drawer → drawer collapses to 0 alpha while keeping the pointer → same overlay; drop
    inserts an `App` item. Cancel by dropping on the drawer handle area.
  - Page creation: dragging past the last page creates a new empty page; empty trailing pages are removed on drop.
- **Files:** `feature/home/drag/*`, `feature/home/HomeScreen.kt`, drawer.
- **Verify:** unit tests for `DragResolver` (pure: given target, occupancy, item kind → `DropAction` sealed result).
- **Device checklist:** long-press picks up with haptic; move across pages; drop swaps into free cell; drop on app makes folder;
  drop on Remove deletes; drag from drawer places; rotation mid-drag cancels cleanly (no stuck overlay).
- **Done when:** checklist passes.

## 2.5 Folders

- **Do:** `Folder` item renders a 2×2 mini-grid of up to 4 icons; tap opens `FolderSheet` (centered card, title editable,
  grid of items, drag within/out of the folder using the same drag system); folder with 1 item collapses to that item; empty
  folder deleted; `BackHandler` closes.
- **Verify:** build; device: create, rename, reorder inside, drag out, auto-collapse.
- **Done when:** checklist passes.

## 2.6 Item popup: shortcuts, app info, remove, uninstall

- **Do:** long-press without moving (release before drag threshold) shows a popup anchored to the icon: app shortcuts
  (`LauncherApps.getShortcuts` when `hasShortcutHostPermission`, up to 4, with icons), "App info", "Remove", "Uninstall"
  (non-system, `REQUEST_DELETE_PACKAGES`). Long-press on a shortcut row drags it to the home as a `Shortcut` item (pinned:
  call `launcherApps.pinShortcuts(pkg, ids + existing, user)` and keep pinned ids in sync when removing).
- **Verify:** build; device: shortcuts appear for Chrome/Messages; pin one; it survives reboot; remove unpins.
- **Done when:** checklist passes.

## 2.7 First-run default layout

- **Do:** on `isFirstRun`, resolve defaults: dialer (`Intent.ACTION_DIAL`), SMS (`Telephony.Sms.getDefaultSmsPackage`), browser
  (`ACTION_VIEW http://`), camera (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`), plus the app drawer pill in the middle dock
  slot; place on dock; nothing on page 0. Set `isFirstRun=false`. Remove the "add test items" debug action.
- **Verify:** unit test for `DefaultLayoutBuilder` given fake resolvers.
- **Done when:** clear app data → relaunch → dock populated.

## 2.8 Missing / suspended app handling

- **Do:** `HomeRepository` reconciles on every `AppRepository.apps` emission: items whose `(component, serial)` are gone →
  delete; suspended/unavailable → render dimmed (state comes from `AppEntry`). `onShortcutsChanged` → drop pinned shortcuts
  no longer returned. Package updated with a component renamed → delete (acceptable).
- **Verify:** unit test for `reconcile(layout, apps): ReconcileResult` (pure).
- **Done when:** uninstalling an app on the home removes its icon within a second.

## Phase device checklist

1. Fresh install → dock has 4 apps, middle slot free (no search pill on the home since ADR-16).
2. Drag 6 apps to page 0 and 2 to page 1; reboot; layout identical.
3. Make a folder, rename it, add 3 apps, remove down to 1 → collapses.
4. Pin a shortcut; reboot; still opens.
5. Uninstall an app that's on the home → icon disappears.
6. Rotate with folder open → no restart, folder still open.

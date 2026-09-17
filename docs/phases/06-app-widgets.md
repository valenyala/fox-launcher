# Phase 6 — App widgets (third-party)

**Goal:** host third-party AppWidgets on the grid with bind/configure/resize/remove and persistence.
**Prereqs:** Phases 2, 3, 5.1.
**Definition of done:** add a clock and a calendar widget, resize, reboot, both still render; removing deletes the id.

Read `PLATFORM-NOTES.md` section AW.

## 6.1 AppWidgetHost lifecycle
- `feature/widgets/app/FoxAppWidgetHost` created in `HomeActivity.onCreate` (`HOST_ID = 0x0F0X` — pick a constant and never change it),
  `startListening` in `onStart`, `stopListening` in `onStop`; provided to composables via `CompositionLocal`.
- Verify: build; no `AppWidgetHost` leaks on rotation (single instance per activity).

## 6.2 Widget picker
- Add an "App widgets" tab to the picker from 5.1: providers grouped by app label (all profiles), preview via
  `loadPreviewImage`/`previewLayout`, min/target cell size computed from `minWidth/minHeight` + `targetCellWidth/Height` (API 31).
- Verify: Compose UI test with fake provider list groups correctly.

## 6.3 Bind + configure flow
- `allocateAppWidgetId` → `bindAppWidgetIdIfAllowed` → else `ACTION_APPWIDGET_BIND` via `ActivityResultLauncher` → configure if
  needed → insert `HomeItem.AppWidget(appWidgetId, provider, spanX, spanY)` at `firstFree` (or the cell the user dragged to). Cancel
  path deletes the id.
- Verify on device with a widget that needs configuration (e.g. a weather/notes widget).

## 6.4 Render, size, resize, touch coexistence
- `AppWidgetItem` composable: `AndroidView(factory = { host.createView(...) })` keyed by id; `updateAppWidgetSize` on cell change;
  resize handles like native widgets; long-press-to-drag detected by the grid in the Initial pass with the long-press timer, while
  taps/scrolls pass to the widget. Also honor `AppWidgetProviderInfo.resizeMode`.
- Verify device: scrollable widget scrolls; long-press drags; resize obeys min sizes.

## 6.5 Persistence and provider removal
- Ids persist via Room; on `AppWidgetHost.onProvidersChanged`/package removed, drop items whose provider no longer resolves and
  `deleteAppWidgetId`. On app data clear, `host.deleteHost()` is not needed. Widgets in a work profile carry badge in picker.
- Verify: reboot keeps widgets; uninstalling the widget's app removes it.

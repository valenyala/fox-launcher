# STATUS

Update this file at the end of every session. Keep it terse. Newest session notes on top.

**Current phase:** 2 — Home grid (phases 0 and 1 code complete; both device checklists still owed)
**Next task:** 2.7 First-run default layout (the performance pass is done; baseline profiles and stability reports stay in 8.1/8.2)

## Environment facts (verified 2026-09-17)

- Host: NixOS x86_64, 12 cores, 31 GB RAM, git 2.54. No JDK, Gradle, adb or Android SDK on PATH → Phase 0.1 must create the flake.
- Devices: POCO F3 (M2012K11AG, `alioth`), MIUI 14 V816.0.5.0.TKHMIXM, Android 13 (API 33), 1080×2400 @ 440 dpi,
  wireless adb. First used 2026-10-10. MIUI asks "Install via USB" on the phone for a *new* package (the test APK's
  first install fails with `INSTALL_FAILED_USER_RESTRICTED` if nobody taps Install). 120 Hz display with a faster touch-sampling rate, so one MotionEvent can carry
  several batched samples (see the 2026-10-10 note). Three-button navigation (`force_fsg_nav_bar` = 0).
- Dev shell (0.1): nixpkgs pinned to b1b8759 (nixos-unstable, 2026-09-16); JDK 17.0.20.1, Android platform 37, build-tools 37.0.0 (bumped from 36 in 0.2, see ADR-12), platform-tools/adb 37.0.1, Gradle 8.14.4 from the shell. The build itself uses the wrapper, Gradle 9.7.1.
- Emulators (2026-10-12, ADR-15): `nix develop .#emulator` adds emulator 37.1.11 and x86_64 Google APIs images.
  `scripts/emulator.sh` AVDs: fox-api29 / fox-api33 / fox-api37 = emulator-5554 / -5556 / -5558, Pixel 7 profile
  (1080×2400 @ 420 dpi), 4 GB RAM, `adb root` works. Cold boot about 30 s. API 29 has three-button navigation,
  33 and 37 gesture navigation. Rendering is software (the emulator rejects the host GPU driver and falls back to
  swangle/lavapipe), so no performance numbers from emulators. `--window` opens through XWayland.
- Toolchain (0.2): AGP 9.4.0, Kotlin 2.3.21, KSP 2.3.12, compose-bom 2026.09.00, compileSdk/targetSdk 37, minSdk 29. AGP 9 rejects the `kotlin.android` plugin (ADR-11).

## Phase 0 — Toolchain & scaffold
- [x] 0.1 Nix dev shell
- [x] 0.2 Gradle project skeleton
- [x] 0.3 HOME activity, theme, default-launcher prompt
- [x] 0.4 Base libraries and DI
- [x] 0.5 Test infrastructure
- [x] 0.6 CI workflow
- [ ] Device checklist passed on: —

## Phase 1 — App catalog & drawer
- [x] 1.1 AppEntry + AppRepository
- [x] 1.2 IconCache
- [x] 1.3 App drawer screen
- [x] 1.4 Minimal home + swipe-up drawer
- [x] 1.5 Work profiles (not verified on device)
- [x] 1.6 Hidden apps
- [ ] Device checklist passed on: —

## Phase 2 — Home grid
- [x] 2.1 Room schema + repository
- [x] 2.2 GridLayoutEngine
- [x] 2.3 Page + dock rendering, wallpaper offsets (device-verified 2026-10-10)
- [x] 2.4 Drag & drop (device checklist passed 2026-10-10, POCO F3)
  - [x] `DragResolver` + tests (pure drop rules)
  - [x] `HomeDragState`, long-press pick-up, `DragOverlay`, target highlight, edge page-flip (user: works, flips smoothly)
  - [x] Apply `DropAction`s in `HomeRepository` (move, dock, create/add folder, remove) (not device-verified)
  - [x] Remove zone, drag from drawer, page creation / empty-page cleanup (user: drawer drag, Remove, new page, rotation OK)
  - [x] Device checklist (user, 2026-10-10: pick-up, page flips, free cell, folder from a top-row app, Remove, drawer drag, rotation)
- [x] 2.5 Folders (device checklist passed 2026-10-11, POCO F3)
- [x] 2.6 Item popup: shortcuts, app info, remove, uninstall (device checklist passed 2026-10-11)
  - [x] Popup (App info / Remove / Uninstall); a long press arms the drag (not device-verified)
  - [x] App shortcuts in the popup (UI tested; the LauncherApps query not yet seen on the phone)
  - [x] Drag a shortcut out → pinned `Shortcut` item, pin sync, shortcut icons (not device-verified)
  - [x] Device checklist (user: shortcuts for Messages/Chrome, open, pin by drag, survives reboot, remove)
- [ ] 2.7 First-run default layout
- [ ] 2.8 Missing / suspended app handling
- [ ] Device checklist passed on: —

## Phase 3 — Gesture engine
- [ ] 3.1 Gesture and action model + defaults
- [ ] 3.2 GestureClassifier + tests
- [ ] 3.3 GestureArena composable
- [ ] 3.4 FoxAccessibilityService + ActionExecutor
- [ ] 3.5 Gesture settings UI
- [ ] 3.6 System gesture coexistence, predictive back, home intent
- [ ] 3.7 Drag-follow drawer opening
- [ ] Device checklist passed on: —

## Phase 4 — Wallpaper rotation
- [ ] 4.1 Sources and entries data layer
- [ ] 4.2 WallpaperPlaylist + tests
- [ ] 4.3 FoxWallpaperService crossfade engine
- [ ] 4.4 RotationScheduler and triggers
- [ ] 4.5 Static fallback
- [ ] 4.6 Lock screen handling
- [ ] 4.7 Wallpaper settings UI
- [ ] 4.8 Memory and performance budget
- [ ] 4.9 URI permission resilience
- [ ] Device checklist passed on: —

## Phase 5 — Memory widgets
- [ ] 5.1 Native widget framework
- [ ] 5.2 MemoryStats + MemInfoParser tests
- [ ] 5.3 RAM gauge, boost and detail widgets
- [ ] 5.4 BoostStrategy per API level
- [ ] 5.5 (stretch) AppWidgetProvider export
- [ ] Device checklist passed on: —

## Phase 6 — App widgets
- [ ] 6.1 AppWidgetHost lifecycle
- [ ] 6.2 Widget picker
- [ ] 6.3 Bind + configure flow
- [ ] 6.4 Render, size, resize, touch coexistence
- [ ] 6.5 Persistence and provider removal
- [ ] Device checklist passed on: —

## Phase 7 — Polish & personalization
- [ ] 7.1 Icon packs and icon options
- [ ] 7.2 Dynamic color and theming
- [ ] 7.3 Notification dots
- [ ] 7.4 Animations
- [ ] 7.5 Pin requests (shortcuts, widgets)
- [ ] 7.6 Backup / restore
- [ ] 7.7 Unified search
- [ ] 7.8 Accessibility
- [ ] Device checklist passed on: —

## Phase 8 — Performance & release
- [ ] 8.1 Baseline profiles + macrobenchmark
- [ ] 8.2 Jank pass
- [ ] 8.3 R8, keep rules, signing
- [ ] 8.4 Battery pass
- [ ] 8.5 Play listing and disclosures
- [ ] 8.6 Crash reporting decision

## Phase 9 — Power features (optional)
- [ ] 9.1 Shizuku boost strategy
- [ ] 9.2 Online wallpaper sources
- [ ] 9.3 Per-app gestures and macros
- [ ] 9.4 External intent API
- [ ] 9.5 Extra transitions, lock-screen live wallpaper

## Blockers

(none)

## Known issues

- `connectedDebugAndroidTest` (AGP 9.4, android-test-engine 1.0.1) ends in BUILD FAILED / "There were failing tests" on
  the POCO F3 even when every test passes: the JUnit XML and HTML report show 32/32 passing, and `am instrument` prints
  `OK (32 tests)`, `INSTRUMENTATION_CODE: -1`. `test-result-exit-code.txt` is 1 and `--info` shows no cause. The
  additional-output dir exists and pulls fine. Until it is understood, judge a run by
  `app/build/reports/androidTests/connected/debug/index.html` or by
  `adb shell am instrument -w -r dev.foxlauncher.test/androidx.test.runner.AndroidJUnitRunner`. Always pass
  `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` on a personal phone, or the launcher is uninstalled.

## Performance (POCO F3, measured with scripts/perf)

Always measure the `profiling` build type (release + R8, debug-signed, installs over debug); debug builds run
Compose 2–4× slower and mislead. Janky % depends on the refresh rate MIUI picks (see PLATFORM-NOTES P), so compare
per-frame work (`frame-work.awk`) across runs, and A/B with `ab.sh` (alternating installs) because
thermal state drifts.

| 2026-10-11 | debug | profiling, before | profiling, after |
|---|---|---|---|
| Cold start TotalTime (median of 5) | 770 ms | 323 ms | 265 ms |
| Page swipes: janky / p99 | 2.4 % / 21 ms | 0.15 % / 11 ms | 0.29 % / 12 ms |
| Drawer open + scroll: p90 / p95 / p99 | 48 / 81 / 150 ms | 16 / 20 / 34 ms | 11 / 14 / 25 ms |
| Total PSS | 175 MB | 119 MB | 141 MB (drawer kept composed) |

Return home after 12 s in Settings (`ab.sh return`, fresh install, 2 A/Bs × 6 returns): TotalTime median 89 → 78 ms,
Fox's first frame on the UI thread 34 → 27 ms (2026-10-11, closed drawer out of the draw tree).

## Session notes

- 2026-10-12 — **Emulators** (user request, before 2.7). `flake.nix` composes the SDK through `composeSdk`; the
  default shell derivation is unchanged (checked: same `.drv`), and `devShells.emulator` adds the emulator, platforms
  29/33 and the API 29/33/37 Google APIs x86_64 images (about 5 GB). The API 37 image is keyed `37.0` in androidenv,
  so `platformVersions = [ "37" ]` would silently fetch no image. New `scripts/emulator.sh create|start|stop|list`
  (re-enters `.#emulator` like `ci.sh`); avdmanager's "Could not load devices.xml" lines are harmless and filtered.
  All three booted headless, took `installDebug` (with `ANDROID_SERIAL`) and showed Fox as home: an empty page and the
  search pill, since nothing places a first-run layout yet (2.7). Windowed mode checked on API 29. Left running:
  fox-api37. ADR-15; CLAUDE.md commands.

- 2026-10-11 — Perf: **icons appeared late when returning home** (user report). Until Fox's first frame after a
  return, the system shows the bare wallpaper. Traced on the POCO F3: Home press to that frame took about 50 ms after
  3 s away, 63 ms after 12 s and 178 ms after 150 s. Two causes were Fox's own. (1) **The hidden drawer was redrawn**:
  a window that turns invisible drops the display lists of its whole render tree, and the closed drawer (alpha 0, one
  screen down) was in it, so every return re-recorded the drawer before the home's first frame. `DrawerLayer` now puts
  the content in its own layer and draws nothing while closed (Compose still records a changed layer that nothing
  draws, so opening costs no more: two `open` A/Bs showed no consistent difference). (2) **`WhileSubscribed(5 s)`**
  on the home and drawer state re-ran the Room query, settings reads, drawer sort and shortcut icons on every return
  after 5 s away, on worker threads alongside the first frame. Shortcut items also showed their app's icon until
  theirs reloaded, because `shortcutIcons` starts empty. Both are now `SharingStarted.Eagerly`; on its own this
  showed no timing gain in the 12 s A/B (95 vs 89 ms, within noise). The rest of the delay is outside Fox: the
  system's launch work (about 60 ms of the 150 s case) and page faults re-reading Fox's evicted code (the main
  thread waits on `filemap_fault`, up to 19 ms at a time); MIUI also swaps most of Fox out while it is backgrounded.
  A/B: see the Performance section. New `ab.sh return` and `first-frame.awk`. New instrumented test
  `comingBackHomeDoesNotRedrawTheClosedDrawer` (moves the activity to CREATED and back); it fails on the old code
  (3 drawer draws, expected 1). `HomeScreenTest` now uses `createAndroidComposeRule<ComponentActivity>()`. 51/51 pass.

- 2026-10-11 — Perf: per-item drawer and home costs. `AppIcon` read the icon generation with a lifecycle-aware
  collector per icon; `HomeActivity` now collects it once and provides `LocalIconGeneration`. Drawer and home icons
  tracked their window bounds with `onGloballyPositioned` (recomputed on every scroll or swipe frame); they keep the
  node from `onPlaced` and compute bounds only when tapped. A/B (`ab.sh`, 2 rounds × 3): UI thread p90, drawer scroll
  7.1 → 6.6 ms, page swipes 4.1 → 3.6 ms; medians slightly down; maxima within noise. Scroll GPU p90 also fell
  (18.1 → 5.6 ms), which this change cannot cause: likely the shader cache, which a reinstall clears, so GPU numbers
  right after an install are noisy. `drawer-open-ab.sh` became `ab.sh <open|scroll|pages>`. 50/50 instrumented pass.

- 2026-10-11 — **Performance pass** (user request, before 2.7; pulls parts of Phase 8 forward). Found by measuring,
  in order of effect: (1) debug vs release, which is why the new `profiling` build type exists (R8 cuts the APK from
  37.6 to 3.1 MB). (2) **MIUI held Fox at 60 Hz** on a 120 Hz panel set to 120 Hz. `HighRefreshRate` sets
  `preferredDisplayModeId` to the fastest mode at the current size from finger down until 3 s after the last finger
  up (and on Home), then gives the choice back, so an idle home does not hold 120 Hz. Verified by log: the display
  lists 60/120/90 Hz modes (ids 1/2/3) and Fox requests id 2; one run had every frame at 8.3 ms. MIUI's own
  `PRIORITY_USER_SETTING_PEAK_REFRESH_RATE` vote can still cap at 60 (it did later in the session, likely thermal) and
  outranks app requests. Pure `fastestModeAtSameSize` (+3 tests). (3) **Opening the drawer built it** (about 60 ms
  frames): it is now composed after the home's first frame and kept, one screen below and alpha 0, with no semantics
  (so tests and TalkBack see it as absent), and its container (`DrawerLayer`) is its own recomposition scope. Because
  it stays composed, closing clears focus (the keyboard drops), re-arms search auto-focus, and the grid scrolls to the
  top (`DrawerScope.isShown`, now an interface; `DrawerContent(isShown)`). (4) Both full-screen fades use
  `CompositingStrategy.ModulateAlpha`. A/B over 6 opens each: GPU p90 7.5 → 6.5 ms, max 59 → 41 ms; UI p90 5.0 → 4.3 ms.
  A single earlier run said the opposite: thermal noise. Cost: about +20 MB PSS for the kept drawer.
  Not done yet: baseline profiles (8.1; the first launches after an install measured 1.1–1.3 s before ART compiled
  the app), Compose compiler stability reports (8.2), and per-item drawer costs (`AppIcon` collects `generation` per
  icon; items track `onGloballyPositioned` every scroll frame). +3 instrumented tests (drawer built before first open,
  pill re-focuses search, closing scrolls to top); 50/50 pass.

- 2026-10-11 — 2.6 checkpoint 3 of 3, **code done; device checklist pending.** Long-pressing a shortcut row arms a
  drag of `newShortcutItem(shortcut)` (id `NEW_ITEM_ID`, title = short label); moving on closes the popup. A drop
  inserts it through the usual `addDropped`. **Pinning:** `applyDrop` snapshots `pinnedShortcutIds` (pure: every
  SHORTCUT row on pages, dock and in folders, per package+profile) before and after, and calls
  `AppRepository.pinShortcuts` for each owner whose set changed (`pinChanges`, pure, +4 tests). `pinShortcuts`
  replaces an owner's whole set, so the last one leaving pins an empty set: unpinned. Removal by popup, by the Remove
  strip, or by deleting a folder holding shortcuts all go through `applyDrop`. Shortcut items now show the shortcut's
  own icon with the app icon as a 40 % badge; folder previews use it too. The view model loads the icons
  (`HomeUiState.shortcutIcons`, keyed by `HomeItem.Shortcut.shortcutKey` = `AppShortcut.key`) again whenever the set
  of shortcuts or the icon cache generation changes. Not handled yet: `LauncherApps.Callback.onShortcutsChanged`
  (an app disabling a pinned shortcut leaves a dead item; 2.8 covers missing items). On the phone,
  `dumpsys shortcut` shows Messages (15), WhatsApp (11), Gmail (5) and others publishing shortcuts, and lists
  `dev.foxlauncher` as a launcher. Tests: `HomeScreenPopupTest` +1 (7); 47 instrumented and 109 unit tests pass.

- 2026-10-11 — 2.6 checkpoint 2 of 3: app shortcuts in the popup. `AppRepository.shortcutsFor(entry)` queries
  `LauncherApps` (`setActivity` = the app's component, `FLAG_MATCH_DYNAMIC | FLAG_MATCH_MANIFEST`), keeps enabled ones,
  manifest before dynamic, each by rank, at most 4 (Launcher3's count). With no host permission it returns empty;
  `SecurityException` and `IllegalStateException` (locked profile) are logged. `shortcutInfo(pkg, id, serial)` finds any
  kind, pinned included (for icons). `IconCache.shortcutIcon` renders `getShortcutIconDrawable` with the app-icon mask
  and size, in memory only. Its key starts `<pkg>/|<serial>|`, so `invalidate()` drops it with the app's icons. The
  view model loads icons into `ItemPopupModel.shortcuts` (`PopupShortcut`), so the popup stays pure and tests need no
  `IconCache`. Rows: icon, long label (falling back to the short one), above a divider. A shortcut item's own popup
  lists no shortcuts. `HomeScreenPopupTest` +1 (6, all pass on the phone).

- 2026-10-11 — 2.6 checkpoint 1 of 3. A long press on a home or folder item now opens `ItemPopup` and **arms** the
  drag (`HomeDragState.start` is gone): `arm(item, itemCoordinates, onDragStart)` begins once the finger passes touch
  slop. With coordinates the item keeps its size and the finger its grip; without (drawer apps) it is carried at
  home-cell size, centred. Moving on closes the popup (Launcher3 behaviour). The popup is drawn in the drag layer, not
  a window, so checkpoint 3 can drag shortcut rows out of it. It sits above the item if there is room below the status
  bar, else below, clamped on screen; outside taps, Back, Home and resizes close it. Contents come from
  `HomeViewModel.popupFor` (App info for apps and shortcuts; Uninstall only for non-system apps; Remove always).
  Remove goes through `applyDrop(Remove)`, so folders collapse and pages close up as after a drag; a folder asks
  first, through the same dialog. `swallowPointers` moved to `Pointers.kt`. `HomeScreenPopupTest` (5); the full
  suite (45) passes on the phone.

- 2026-10-11 — **2.5 done.** User confirmed on the phone: create, rename, reorder inside, add a third app, drag out
  down to one item (collapses). Rotation with a folder open is retired by ADR-14.

- 2026-10-11 — **Portrait only (ADR-14)**, at the user's request: `screenOrientation="portrait"` (+ a
  `LockedOrientationActivity` lint ignore, mirrored in PLATFORM-NOTES H). Checked on the phone: `dumpsys activity`
  reports `mOrientation=SCREEN_ORIENTATION_PORTRAIT`. `configChanges` and the rotation-safe code stay (see ADR-14).
  The rotation items of the 0.3, Phase 1, 2.4 and Phase 2 checklists no longer apply on phones. The landscape
  re-run offered last session is moot.

- 2026-10-11 — Two drawer requests from the user, plus landscape findings.
  **Drawer labels were invisible in dark mode** (the phone is in night mode). `DrawerContent`'s Surface used
  `surface.copy(alpha = 0.9f)`; `contentColorFor` matches only exact theme colours, so content fell back to
  `LocalContentColor`'s default, black. Now `contentColor = onSurface`. `DrawerContentTest.appNamesUseTheThemesOnSurfaceColour`
  failed on the phone before the fix (black vs. #E2E1EC) and passes after. Any other see-through Surface needs an
  explicit `contentColor` too.
  **Pull down to close:** the drawer's lazy grid takes every vertical drag, so the 1.4 swipe never saw one starting on
  it. `DrawerPullConnection` (`NestedScrollConnection` on the drawer box) takes what the list leaves over at its top:
  the drawer follows the finger and pre-scroll re-raises it before the list scrolls. A release (pre-fling) closes on a
  downward fling over 300 dp/s or once pulled 20 % down, and consumes the fling so the list does not fling too.
  Reaching 0 settles closed at once, because the list leaves composition and may never report the release.
  +2 `HomeScreenTest` (at the top: closes; scrolled: only scrolls).
  **Landscape:** one suite run happened in landscape (the phone was turned). It showed (a) `HomePageTest` again assumed
  a large screen (now 240×300 dp, fits both orientations); (b) a **real bug**: in landscape the pages are too short to
  shrink clear of the Remove strip (clamped at 0.7), so a top-row drop removed the item. Now Remove only counts above
  the top of the visible pages. Empty clipped boxes of off-screen pages must be skipped, or that top is 0 and Remove
  never fires (caught by the portrait run). (c) The drag-out folder test aimed at a point that lies on the card in
  landscape; it now aims at the margin under the tagged card. Portrait: 40/40 pass. **Landscape not re-run yet.**

- 2026-10-10 — 2.5 Folders, **code done; device checklist pending.** Tapping a folder tile opens `FolderSheet`: a
  dark-glass card (dark so the white wallpaper-style labels stay readable in any theme) over a 35 % scrim, inside the
  home drag layer. The scrim and card swallow pointers so the home and the drawer swipe never react; tapping the scrim
  closes. The title is a `BasicTextField`, committed via `onDispose` whatever closed the sheet, and only if changed
  (`HomeRepository.renameFolder`). Grid columns follow Launcher3 (pure `folderColumns`: 2 up to 4 items, 3 up to 9,
  then 4).
  Drag inside: `DropTarget.FolderRank` → `DropAction.MoveInFolder` (resolver, +2 tests). While dragging, the other
  items slide to where they will be (`moveToRank`, animated offsets), and `droppedAction` holds that order through the
  settle. Drag out: with the finger off the card for 300 ms the folder closes, and `setOpenFolder` (called from a
  `SideEffect`) re-resolves against the home grid. Edge flips are off while a folder is open.
  After every drop the repository runs pure `folderCleanup` (empty folder → gone; one item left → it takes the tile's
  place) through `applyFolderCleanup`, then closes page gaps. `move()` now clears `folderId` when an item leaves a
  folder. The open folder is remembered by tile id, so it survives rotation and closes by itself if the folder
  collapses; Back, Home and launching an item also close it.
  Tests: `FolderRulesTest` (8), `HomeScreenFolderTest` (4: open/close, reorder, drag out onto a home cell, rename),
  `HomeDaoFolderTest` +1 (collapse through Room). The full instrumented suite passed on the phone (36, then the DAO
  class again with the new case).

- 2026-10-10 — **2.4 done.** The user confirmed the spring-loaded shrink (the Remove strip stays clear of row 0) and a
  folder made by dropping onto a top-row app. That completes the device checklist.

- 2026-10-10 — **First instrumented run** (POCO F3): 32 tests. On the first pass 3 failed:
  - `HomePageTest` assumed a screen at least 400 dp wide; this phone is 392.7 dp, so the box was squeezed. Now 320×400 dp,
    same assertions scaled to the new size.
  - `HomeScreenDragTest.dragToAFreeCellMovesTheItemThere` failed once with "Cannot move pointers, no gesture is in
    progress" right after the long-press wait. It has not come back in three runs since; watch for it.
  - `dropOnAnotherAppCreatesAFolder` found a **real bug**: the Remove strip (status bar + 64 dp) covered most of grid
    row 0, so any drop there removed the item. **Fix:** Launcher3's spring-loaded state. While an item is dragged or
    settling, the pager shrinks around its bottom centre by `1 - (64 dp + 8 dp) / pagerHeight` (not below 0.7; about
    0.88 here), so row 0 starts below the strip. Geometry already went through `LayoutCoordinates`, which include
    layer transforms. `cellBounds` now maps both corners, so highlights and glides land on the scaled cells. The
    overlay settles to `destination width / item width`, so it ends exactly cell- or slot-sized. Test targets on pages
    are read after the drag starts.
  Second pass: 32/32 pass (see Known issues for Gradle's exit code). The user tried checkpoint 4 on the phone (drawer
  drag, Remove, new page, rotation mid-drag): OK. The shrink came after that and is not seen on the phone yet.

- 2026-10-10 — 2.4 checkpoint 4 of 5, **code done, not device-verified** (phone locked again). The user confirmed
  checkpoint 2/3 on the phone: home drag works and edge flips are smooth. They expected long-press in the drawer to
  drag, which is this checkpoint.
  **Drawer drag:** a drawer long press still opens the popup and also calls `DrawerScope.onAppLongPress` (new receiver of
  `HomeScreen`'s `drawer` slot) → `HomeDragState.arm(newAppItem(entry))`. The root layer then owns the finger. Lifting
  without moving leaves the popup. Moving past touch slop begins the drag: the overlay is home-cell sized and centred on
  the finger, and the drawer snaps shut (`onDrawerClosed`, which now also dismisses the popup and cancels a pending
  popup job). **Unverified risk:** the popup is its own window; if the platform cancels our gesture when it appears, the
  arm never fires, and the fallback is to open the popup on release instead. A new item has id `NEW_ITEM_ID` (0, Room's
  "generate" id). `DraggedItem.id` becomes null, `applyDrop` inserts it, and folder drops go through
  `insertIntoNewFolder`/`insertIntoFolder`, which keep nothing if the target is gone. There is no separate cancel strip:
  dropping below the dock (where the pill is) is `Outside`, which discards a new item.
  **Remove zone:** a strip across the top (status bar + 64 dp) while a home item is dragged. It is hit-tested by the
  finger, not the overlay centre, and turns red when hovered. A folder dropped there opens a confirm dialog; the
  item stays until confirmed.
  **Pages:** while dragging, the pager offers one extra empty page after the last, unless the dragged item is alone on
  the last page. Dropping there creates it; otherwise it vanishes again. The pager key is now the index. After every
  drop `closePageGaps()` renumbers pages with pure `pageRenumbering` (+4 unit tests), so empty pages before or between
  others go away.
  Tests: `HomeScreenDragTest` +4 (Remove zone, folder confirm, right-edge new page, drawer long-press-and-move);
  `HomeDaoFolderTest` +3. Still none run.

- 2026-10-10 — 2.4 checkpoints 2 + 3 of 5, **code done, not device-verified** (the phone was locked when they were
  ready). `feature/home/drag/HomeDragState.kt`: items use `combinedClickable` (long-press haptic is built in since
  foundation 1.7); `onLongClick` hands the item's `LayoutCoordinates` to `HomeDragState.start`. **Deviation:** the
  task says `detectDragGesturesAfterLongPress` on the item in the Initial pass. That detector has no pass parameter,
  and it dies with the item once an edge flip disposes the source page. Instead, `Modifier.homeDragLayer` on the
  `HomeScreen` root watches every event in the Initial pass. It remembers the pressed finger, and once a drag owns that
  pointer it consumes everything until the finger lifts (even after `cancel()`), so the pager and the drawer swipe never
  see it. The target is taken from the overlay's centre, not the finger. Below the dock's top edge it is a dock slot, or
  `Outside` if the centre is not over the dock. Above it, it is the nearest page by its visible (clipped) bounds, with
  the point clamped into the grid, so the gaps around a page map to its edge cells. Pages and the dock register their
  `LayoutCoordinates` in plain fields (no recomposition).
  Every move runs `DragResolver` as a preview, so the highlight shows the real landing cell: a soft tile, or an
  outlined tile when the drop would merge into a folder. `DropHighlight` is drawn under the grid. Edge flip: 40 dp zone
  above the dock, 400 ms dwell, repeats. Each flip runs in its own job, so leaving the edge never strands the pager
  between pages. A release hands the action to `onDrop` first, then the overlay glides (180 ms, scale 1.1 → 1) to the
  destination cell (or back to its source on `Cancel`). The source view stays hidden (alpha 0) until the glide ends,
  which covers the Room round trip. A size change (rotation) or the Home key cancels a drag.
  Apply: `HomeRepository.applyDrop`, with DAO transactions `createFolder` (tile in the target's place, target rank 0,
  dragged rank 1, untitled), `addToFolder` (rank = max + 1) and `deleteFolderWithItems`. `DropAction.AddToFolder.folderId`
  was renamed `folderItemId`, because it always held the tile's home-item id, not `folders.id`.
  Tests: `HomeScreenDragTest` (4 Compose tests: free cell, onto an app → folder, onto a dock slot, a tap still clicks)
  and `HomeDaoFolderTest` (4, in-memory Room). Both compile; neither has run.
  Known gaps: Compose reports a pointer cancel as "all fingers up", so a system cancel drops the item where it is
  instead of putting it back.
  Dragging past the last page does nothing yet (checkpoint 4).
  Next: run `connectedDebugAndroidTest` on the POCO F3 (with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
  so the launcher stays installed) and the 2.4 device checklist, then checkpoint 4 (Remove zone, drag from the drawer,
  page creation / empty-page cleanup).

- 2026-10-10 — **First device session** (POCO F3, Android 13). The app runs; no crash at start, so the 0.4 runtime
  Hilt/WorkManager check passes. The user confirmed: home shows the pill, the drawer opens by swipe, uninstalling an app
  removes it from the drawer, and **2.3 "Done when" is now met** (debug test items land in the right cells with labels,
  paging and page dots work, wallpaper offsets move, Home on page 1 animates to page 0, tapping opens with the reveal).
  Not yet checked: rotation (0.3, phase 1 item 4), Home from another app, install-from-Play, search, the drawer popup.
  No instrumented test has run yet.
  **1.4 swipe fix:** a full-length swipe was needed to open the drawer. Measured with `input swipe` + a temporary log:
  fast swipes gave velocity 0 (the tracker took only `change.position` per event and dropped the batched historical
  samples) and the release read `progress.value` before the queued `snapTo` calls had run (a 500 px swipe counted as
  58 px). Now `addPointerInputChange` and a synchronous `dragProgress`; thresholds 20 % (was 30 %) and 300 dp/s (was
  1200 px/s). User confirmed it feels right. +2 `HomeScreenTest` cases (short fast flick opens, short slow drag does
  not). Lesson for 3.2/3.3: the classifier must take historical samples, or it will miss flicks on this phone.
  Quirk: with synthetic `input swipe` under ~400 ms, the movement in the up event is lost (`detectVerticalDragGestures`
  ignores it), so the distance is undercounted; real finger swipes are not affected.

- 2026-09-22 — 2.4 checkpoint 1 of 5: `feature/home/drag/DragResolver.kt` (pure) + 18 tests. Types: `DragKind`
  (APP/SHORTCUT/FOLDER/WIDGET), `DraggedItem(id?, kind, span)` (id null = new from the drawer), `PlacedItem`,
  `DropTarget` (Cell/DockSlot/RemoveZone/Outside) → `DropAction` (MoveToPage/MoveToDock/CreateFolder/AddToFolder/
  Remove/Cancel). Rules: the dragged item's own cell counts as free. App/shortcut onto a 1×1 app/shortcut creates a
  folder; onto a folder it joins. Folders never nest. Anything else on an occupied cell goes to
  `nearestFreeTo`, and a block that doesn't fit shifts to the nearest block that does. The dock is 1×1 only (no
  widgets); a taken slot sends the item to the nearest free slot, preferring the left. A new item dropped on Remove is
  discarded. **Stopped here on purpose:** the rest of 2.4 is pointer handling, overlay and page-flip timing, which is
  guesswork without a device. Next session should start with the second sub-bullet, ideally with a phone attached.

- 2026-09-22 — 2.3 code done, **"Done when" not met: device needed** (items rendering in the right cell, parallax,
  paging). `HomeScreen` is now a Column inside `safeDrawing` insets: `HorizontalPager` (`beyondViewportPageCount = 1`,
  keyed by page index), `PageIndicator` (hidden with one page), `Dock` (equal slots, `col` = slot), then the 1.4 search
  pill, which stays until 2.7 puts a drawer pill in the dock. The 1.4 swipe-up detector wraps it all. `HomePage` is a
  custom `Layout`: cell = available size ÷ `GridSpec`, placement from `GridLayoutEngine.pxOf`, items keyed by id.
  `HomePageTest` (instrumented, not run) checks positions and spans. `HomeItemView`: app icon + white shadowed label.
  A missing app shows a dim grey disc until 2.8. Shortcuts use their app's icon until 2.6. Folders get a 2×2 preview
  on a translucent tile, and tapping one does nothing until 2.5. Widgets are placeholder tiles. Taps launch with clip
  reveal. Shortcuts go through the new `AppRepository.startShortcut`, which logs failures.
  `WallpaperOffsetsEffect`: `setWallpaperOffsetSteps(1/(n-1), 1)` and `setWallpaperOffsets(token, x, 0f)` from a
  conflated `snapshotFlow`. A single page reports x = 0.5 (centred), which the notes do not specify. The Home key now
  also animates to page 0. New settings: `homeGrid` (5×6, 5 dock slots) and `showHomeLabels`; no UI for them yet.
  **Temporary debug action:** drawer ⋮ → "Add test items (debug)", shown only when `BuildConfig.DEBUG`
  (`HomeViewModel.addTestItems`, TODO(2.7)). It fills the dock, two rows of page 0 and three cells of page 1.
  `HomeRepository` is now injected, so orphan pruning is live.

- 2026-09-22 — 2.2 done. `feature/home/grid/`: `GridSpec` (validated; `DEFAULT` = 5×6, 5 dock slots), `CellRect`
  (+ `overlaps`), `PxRect`, `GridLayoutEngine` with every function the task lists plus `fits()`. Occupancy is
  `[row][col]` and clips items that hang off the grid. `nearestFreeTo` searches square rings outward. Within a ring
  the smallest Euclidean distance wins, then the upper cell, then the left, so results are deterministic. 18 tests.

- 2026-09-22 — 2.1 done. Schema v2 (`app/schemas/.../2.json`): `home_items` (CLAUDE.md shape + `createdAt`, index
  `(container, page)` and `folderId`, enums stored as TEXT by Room) and `folders`. `AutoMigration(1→2)` with a
  `@DeleteTable("key_value")` spec: the Phase 0 placeholder table had no users, so it is gone along with its DAO.
  **There is no migration test yet:** it needs `room-testing` (a new dependency) and a device. Add both when the first
  real-data migration (v2→v3) happens. `HomeDao` is an abstract class so `moveItems` can be `@Transaction open`.
  `deleteByPackage` matches the prefix with `substr(...) = pkg || '/'`, not LIKE, because `_` is a LIKE wildcard.
  Folder children have `container = FOLDER` and the folder's `folderId`, with rank in `col`. Shortcut rows store
  `component = "<pkg>/"`, so `deleteByPackage` catches them too. Model: `core/model/HomeItem.kt` (sealed `HomeItem`,
  `HomePosition`, `HomePage`, `HomeLayout` with at least one page). Pure `HomeLayoutMapper` + `orphanedAppItemIds`
  (11 tests); an empty catalog never prunes. `HomeRepository` (layout flow, addApp/move/remove, orphan pruning once
  `isLoaded`) exists but nothing injects it until 2.3.

- 2026-09-22 — 1.6 done, **hide/unhide not verified on device**. Pure `applyHidden(list, hidden, query)` in
  `core/apps/HiddenApps.kt` (7 tests). Hiding is per profile (`AppEntry.key`). A hidden app is revealed only when the
  trimmed query equals its label, ignoring case and accents. `FoxSettings.hiddenApps` is a string-set key with
  `setHidden(key, hidden)`. The drawer popup gains "Hide". A ⋮ trailing icon on the search field opens a menu with
  "Hidden apps", which shows `feature/settings/HiddenAppsScreen` (checkbox list, `HiddenAppsContentTest`). **No
  NavHost yet:** the screen is a `rememberSaveable` overlay flag in `HomeActivity` with its own `BackHandler`; make it a
  route when settings grow (3.5). Icons are two local vector drawables (`ic_more_vert`, `ic_arrow_back`), because
  material3 1.4 does not pull in material-icons. They tint with `?android:attr/colorControlNormal`: the app attr form
  needs AppCompat and fails resource linking.

  **Phase 1 code complete.** 44 unit tests; 13 instrumented tests compile and none has ever run. Lint 6 warnings, 0
  errors. Still owed on a device: the Phase 0 and Phase 1 checklists and the first `connectedDebugAndroidTest`.

- 2026-09-22 — 1.5 done, **not verified on device** (no device, no TestDPC work profile). `AppRepository.workProfile`
  (`WorkProfile(serial, isPaused)`, first non-own profile, from quiet mode) and `setWorkPaused()`
  (`requestQuietModeEnabled` on IO; a `SecurityException` is logged, not thrown). A runtime receiver
  (`ContextCompat.registerReceiver`, NOT_EXPORTED, on the callback thread) listens for
  `MANAGED_PROFILE_ADDED/REMOVED/AVAILABLE/UNAVAILABLE/UNLOCKED` and requests a refresh. Drawer: Personal/Work
  `SecondaryTabRow` only when a work profile exists and the query is blank. **Search covers every profile**
  (pure `appsForTab`, 3 new tests), which goes beyond the task text. The Work tab has a "Pause work apps" switch row plus
  an explainer when paused. Paused and suspended apps are dimmed by `AppIcon`; badges came in 1.2. The "only when default
  launcher" condition holds because the drawer is only reachable once `HomeRoot` has the role. Known gaps: every
  non-own profile is treated as "work", so an OEM clone/dual-app profile would land in the Work tab. Android 15 private
  space is not listed, since `userProfiles` hides it from launchers without `ACCESS_HIDDEN_PROFILES`; this is from
  memory and needs checking on a device. `DrawerContentTest` +2 tests (no tabs without work; Work tab lists work apps
  and the toggle reports `true`).

- 2026-09-22 — 1.4 code done, **device checklist not run** (finger-follow, Home closes, Back closes). `HomeScreen`
  has a translucent "Search apps" pill and the drawer as an overlay. Its `progress` is an `Animatable`
  (0..1, `graphicsLayer` translation + alpha). The drag detector is `detectVerticalDragGestures` with a `VelocityTracker`
  and a `TODO(phase3)`. Release snaps open past 30 % or on an upward fling over 1200 px/s, and closes on a fast
  downward fling. The pill opens the drawer with search auto-focused; a swipe opens it without focus. Closing clears the
  query. The Home key increments `homeKeyPresses` in `onNewIntent(ACTION_MAIN)`, and `HomeScreen` closes on each change.
  `BackHandler(enabled = isOpen)`. `HomeActivity` injects `IconCache` and provides `LocalIconCache`. **`HomeRoot` now
  takes a `home` slot** instead of drawing the placeholder "Fox" label. `HomeRootTest`'s third test now asserts the slot
  content instead of the removed label; the invariant is unchanged (no prompt when default). A fourth test covers the
  inverse. New `HomeScreenTest` (4 Compose tests: closed at start, pill → focus=true, swipe → focus=false, Home key
  closes). No instrumented test has run yet. Temporary app-count log removed from `HomeViewModel`.

- 2026-09-22 — 1.3 code done, **device checklist not run**. `DrawerContentTest` (3 Compose tests, including the
  required "ma" filter test) compiles but has **never run** (no device). Sectioning is a pure `buildSections(apps,
  query)` with 6 unit tests. A blank query gives letter sections (accents stripped, non-letters go under `#`). A query
  gives one headerless section ordered by `AppSearch.Rank`. Deviations: `DrawerViewModel` owns a `TextFieldState`
  (`searchState`) instead of feeding the text field from `uiState.query`, because an async flow drops keystrokes;
  `uiState.query` is derived from it. The popup is a `DropdownMenu` anchored on the item: App info, Uninstall (only
  when not `FLAG_SYSTEM`), and a disabled "Add to home screen" placeholder. The rail is hidden during search. Launch
  animation is `core/ui/LaunchAnimation.kt` (clip reveal + screen-space `sourceBounds`). Icons come from `AppIcon` via
  `LocalIconCache`, which 1.4 provides; the drawer is not reachable in the app until 1.4. New setting `drawerColumns`
  (default 5).

- 2026-09-22 — 1.2 done, **desk-verified only**: the "< 5 ms per disk hit" timing and the scroll-jank check both
  need a device (the `FoxLog.d` timing line is in `IconCache.load`). `IconShapes.kt` is pure data (`PathCommand` list per
  `IconShape`: circle = 4 cubics, squircle = 96-segment superellipse n=4, rounded square r = 20 %), with 8 tests.
  `IconRenderer` turns commands into an `android.graphics.Path` and clips the adaptive layers with it, which replaces the
  system mask. Legacy icons get 8 % padding and no mask. Disk layout differs slightly from the task text: files go in
  `filesDir/icons/<sizePx>-<shape>/<pkg>@<serial>@<sha1(key)[:16]>.png`. The prefix lets `invalidate(pkg, serial)`
  delete by package. The per-config directory lets a settings change wipe every stale icon. The key still includes
  size and shape. `AppRepository.packageChanges` (new SharedFlow, not debounced) drives invalidation. `IconCache.generation`
  (StateFlow) bumps on every invalidation so the UI can re-fetch. New settings keys: `iconShapeId`, `iconSizeDp`
  (default 56); no UI sets them until 7.1. In-flight loads are de-duplicated per key and run in the app scope.

- 2026-09-22 — 1.1 done, **desk-verified only, no device** (device checklist not run). `AppEntry` (+ `key` =
  `component|serial`), `AppRepository` (LauncherApps across `userProfiles`, callback on a `HandlerThread`, 250 ms
  debounced refresh via a `SharedFlow`, `Mutex`-guarded refresh on IO, `UserHandle` map by serial), `sortEntries`
  (label via `Collation`, then component, then serial) and `AppSearch` (NFD + strip marks + lowercase; `rank()` returns
  LABEL_PREFIX / WORD_PREFIX / SUBSTRING, `match()` is `rank != null`). Additions beyond the task text: `isLoaded`
  StateFlow (drawer needs loading vs. empty), `isSystemApp()` for the 1.3 popup, `userFor(serial)`. `versionCode` comes
  from `getPackageInfo(MATCH_UNINSTALLED_PACKAGES)`, 0 when unresolvable — it only feeds icon-cache keys. Manifest gains
  `QUERY_ALL_PACKAGES` (lint-ignored, reason in comment) and `REQUEST_DELETE_PACKAGES`. Temporary app-count log in
  `HomeViewModel` (TODO(1.4)). 20 unit tests pass. Lint 6 warnings, 0 errors: the one new warning is
  `AndroidGradlePluginVersion` — a newer AGP was published since 0.2, unrelated to this change.

- 2026-09-17 — 0.6 reworked: **CI is local, not GitHub Actions** (ADR-13). The workflow from the first pass was
  verified and then removed at the user's request. `scripts/ci.sh` runs `check assembleDebug
  :app:compileDebugAndroidTestKotlin` — unit tests, lint, APK and the instrumented sources that nothing else compiles
  without a device — re-entering `nix develop` unless the caller is already in the dev shell. `.githooks/pre-commit`
  execs it; the hooks dir is tracked and the flake's `shellHook` sets `core.hooksPath=.githooks` on entering the shell,
  so it installs itself. The hook checks the working tree, not the index; `git commit --no-verify` skips it.
  Verified both ways: green on the current tree, and a deliberately failing unit test made `scripts/ci.sh` exit 1.
  The old workflow is recoverable from commit 75192eb. CLAUDE.md's "Pre-commit gate" paragraph still lists the older
  three-task command — worth pointing at `scripts/ci.sh` next time that file is edited.

  Phase 0 code is complete. Still owed on a device: the 0.3 checklist (wallpaper through the window, no flash on
  rotation, Home returns to Fox), the 0.4 runtime Hilt/WorkManager start, and the first `connectedDebugAndroidTest`
  (`HomeRootTest` has only ever been compiled).

- 2026-09-17 — 0.5 done. Test deps were already in the catalog from 0.2, so this was source only (plus
  `androidTestImplementation(kotlin-test)`). Added `Collation` in `core/common` (pure JVM, `java.text.Collator`,
  SECONDARY strength + raw-string tie-break) as the label ordering Phase 1's drawer will sort with, and
  `MainDispatcherRule` in the test source set (`UnconfinedTestDispatcher` by default, swappable for a
  `StandardTestDispatcher`). **8 unit tests pass** (`CollationTest` ×7 — case, accents, case-only ties, digits, empty
  label, locale-dependent ordering for sv vs en; `MainDispatcherRuleTest` ×1 smoke). `compileDebugAndroidTestKotlin`
  compiles `HomeRootTest` (3 Compose UI tests over the stateless `HomeRoot`, role state injected, no `RoleManager`).
  Instrumentation runner stays the default `AndroidJUnitRunner` — no instrumented test needs Hilt yet.
  Two deprecations dealt with: `MainDispatcherRule` needs `@OptIn(ExperimentalCoroutinesApi::class)` for
  `setMain`/`resetMain`, and `createComposeRule` is deprecated in favour of `androidx.compose.ui.test.junit4.v2`, which
  schedules with `StandardTestDispatcher` instead of Unconfined — the v2 rule is in use and compiles, but **it has never
  been run** (no device), so if `HomeRootTest` needs explicit synchronization that will only show up on the first
  `connectedDebugAndroidTest`. Gate green; lint unchanged at 5 warnings, 0 errors.

- 2026-09-17 — 0.4 done, **desk-verified only, no device**. Hilt graph up: `@HiltAndroidApp FoxApp` is now also
  `Configuration.Provider` (injected `HiltWorkerFactory`) and Coil 3's `SingletonImageLoader.Factory` (memory cache 15 % of
  heap, 64 MB disk cache in `cacheDir/image_cache`, crossfade on). Hilt-WorkManager needs the default startup initializer
  gone, so the manifest now merges `androidx.startup.InitializationProvider` with `tools:node="remove"` on
  `WorkManagerInitializer` — WorkManager initializes on demand from `FoxApp` instead. `core/common/Dispatchers.kt` has
  `@IoDispatcher`/`@DefaultDispatcher`/`@MainDispatcher` plus an `@ApplicationScope` `CoroutineScope`.
  `core/data/db`: `FoxDatabase` v1 with the placeholder `KeyValueEntity`/`KeyValueDao` and `DatabaseModule`; schema exported
  and committed at `app/schemas/dev.foxlauncher.core.data.db.FoxDatabase/1.json`. `core/data/settings`: `FoxSettings` over
  `DataStore<Preferences>` (`isFirstRun` Flow + `suspend setFirstRun`, writes on IO) with `SettingsModule`.
  `HomeActivity` is `@AndroidEntryPoint` and gets a `HomeViewModel` (injected `FoxSettings`, `StateFlow<HomeUiState>`); on
  resume, once the home role is held, it calls `onLauncherReady()` which clears first-run — that is the proof the graph
  resolves at compile time. `hiltViewModel` moved package in androidx.hilt 1.4.0 (now
  `androidx.hilt.lifecycle.viewmodel.compose`); the old import compiles with a deprecation warning, so the new one is used.
  Gate green; lint unchanged at 5 warnings, 0 errors. `testDebugUnitTest` is still NO-SOURCE — 0.5 brings the first tests.
  **Still owed: runtime Hilt check on a device** (app starts without a `HiltAndroidApp`/WorkManager init error), plus the
  0.3 device checklist.

- 2026-09-17 — 0.3 done, **desk-verified only, no device**. `HomeActivity` (singleTask, full `configChanges`, `enableEdgeToEdge`, `onNewIntent` stores the intent), `Theme.Fox.Home` with `windowShowWallpaper` + transparent window + `shortEdges` cutout, `FoxTheme` (dynamic color on API 31+, static fox-orange scheme below), stateless `HomeRoot(isDefaultLauncher, onRequestDefaultLauncher)`, `RoleManager.ROLE_HOME` request with a `Settings.ACTION_HOME_SETTINGS` fallback, adaptive launcher icon with a monochrome layer. The activity manifest block is byte-for-byte the attribute set in PLATFORM-NOTES H (checked by diffing the two). Pulled `core/common/FoxLog.kt` forward from 0.4 because the role fallback needed logging. Gate green; lint 5 warnings, 0 errors — all deliberate: `UnusedAttribute`/`DataExtractionRules` (minSdk 29 + allowBackup=false), `DiscouragedApi` on `screenOrientation="unspecified"` (kept for PLATFORM-NOTES fidelity; the value is the platform default anyway), 2× `NewerVersionAvailable` on Kotlin (ADR-11). Note: renaming a res folder leaves stale incremental merge state — `./gradlew :app:clean` was needed once. **Still owed: the 0.3 device checklist** (wallpaper visible through the window, no flash on rotation, Home key returns to Fox).
- 2026-09-17 — 0.2 done. Wrapper 9.7.1 (dist SHA-256 pinned), settings/root/app build files, full `libs.versions.toml`, `gradle.properties`, minimal manifest + placeholder `FoxApp`/`strings`/`themes`. Two deviations from the phase doc, both recorded as ADRs: AGP 9 has built-in Kotlin and hard-errors on `org.jetbrains.kotlin.android` (ADR-11), and current AndroidX requires compileSdk 37, so the flake moved to platform 37 / build-tools 37.0.0 (ADR-12). Kotlin held at 2.3.21 because KSP has no 2.4 line yet. Gate green: `:app:testDebugUnitTest :app:lintDebug assembleDebug` (0 tests exist yet; lint reports 5 warnings, 0 errors — `MissingApplicationIcon` goes away in 0.3). Debug APK 36 MB. Next: 0.3.
- 2026-09-17 — 0.1 done. flake.nix + .envrc + flake.lock committed; `nix develop` gives JDK 17, SDK platform 36, build-tools 36.0.0, adb and Gradle. aapt2 override set via GRADLE_OPTS. All three verify commands from the phase doc pass. Next: 0.2.
- 2026-09-17 — Plan, CLAUDE.md and docs written. No code yet.

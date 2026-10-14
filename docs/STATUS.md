# STATUS

Update this file at the end of every session. Keep it terse. Newest session notes on top.

**Current phase:** 6 — App widgets (Phase 5 done 2026-10-13, its boost checklist item blocked by MIUI until Phase 9; Phase 4 owes three checklist items, deferred; Phase 2 still owes 2.8, deferred by the user, and its device checklist; phases 0 and 1 owe their device checklists)
**Next task:** the POCO checks of 6.1–6.5 and phase 6's device checklist, then phase 7 (see docs/phases/07-polish.md) (3.8's POCO check still owed, the user went on to phase 6 first; 2.8 still deferred)

## Environment facts (verified 2026-09-17)

- Host: NixOS x86_64, 12 cores, 31 GB RAM, git 2.54. No JDK, Gradle, adb or Android SDK on PATH → Phase 0.1 must create the flake.
- Devices: POCO F3 (M2012K11AG, `alioth`), MIUI 14 V816.0.5.0.TKHMIXM, Android 13 (API 33), 1080×2400 @ 440 dpi,
  wireless adb. First used 2026-10-10. MIUI asks "Install via USB" on the phone for a *new* package (the test APK's
  first install fails with `INSTALL_FAILED_USER_RESTRICTED` if nobody taps Install). 120 Hz display with a faster touch-sampling rate, so one MotionEvent can carry
  several batched samples (see the 2026-10-10 note). Three-button navigation (`force_fsg_nav_bar` = 0).
- Dev shell (0.1): nixpkgs pinned to b1b8759 (nixos-unstable, 2026-09-16); JDK 17.0.20.1, Android platform 37, build-tools 37.0.0 (bumped from 36 in 0.2, see ADR-12), platform-tools/adb 37.0.1, Gradle 8.14.4 from the shell. The build itself uses the wrapper, Gradle 9.7.1.
- Emulators (2026-10-12, ADR-15): `nix develop .#emulator` adds emulator 37.1.11 and x86_64 Google APIs images.
  `scripts/emulator.sh` AVDs: fox-api33 / fox-api37 = emulator-5556 / -5558, Pixel 7 profile
  (1080×2400 @ 420 dpi), 4 GB RAM, `adb root` works. Cold boot about 30 s. Both use gesture navigation
  (the phone uses three buttons). Rendering is software (the emulator rejects the host GPU driver and falls back to
  swangle/lavapipe), so no performance numbers from emulators. `--window` opens through XWayland.
  **Test on API 33 (the phone's level) and above only** (user, 2026-10-12); the API 29 emulator was removed.
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
- [x] 2.7 First-run default layout (user confirmed on the POCO F3, 2026-10-12; middle dock slot stays free)
- [ ] 2.8 Missing / suspended app handling (deferred until after Phase 3, user 2026-10-12)
- [ ] Device checklist passed on: —

## Phase 3 — Gesture engine
- [x] 3.1 Gesture and action model + defaults
- [x] 3.2 GestureClassifier + tests
- [x] 3.3 GestureArena composable (device checklist passed 2026-10-12, POCO F3; emulators API 33/37)
- [x] 3.4 FoxAccessibilityService + ActionExecutor (POCO F3 2026-10-12: all six system actions, disclosure, stopped state, automatic restart)
- [x] 3.5 Gesture settings UI (POCO F3 2026-10-12: remapping to recents, an app, notifications; reset; Home closes settings)
- [x] 3.6 System gesture coexistence, predictive back, home intent (gesture navigation: fox-api33/37; three-button: POCO F3, 2026-10-12)
- [x] 3.7 Drag-follow drawer opening (POCO F3 2026-10-12)
- [ ] 3.8 Navigation gestures in other apps (user request 2026-10-13, ADR-20): fox-api33/37 pass; **owed:** the POCO F3 checklist
- [x] Device checklist passed on: POCO F3 (three-button) + fox-api33/37 (gesture navigation), 2026-10-12 — items covered across 3.3–3.7; item 3 done as pinch out → Open app: Camera

## Phase 4 — Wallpaper rotation
- [x] 4.1 Sources and entries data layer (built-in images are generated placeholders until the user supplies some)
- [x] 4.2 WallpaperPlaylist + tests
- [x] 4.3 FoxWallpaperService crossfade engine (POCO F3 2026-10-12)
- [x] 4.4 RotationScheduler and triggers (POCO F3 2026-10-12: interval, unlock, page change, gesture; time-of-day schedule left as a TODO, stretch)
- [x] 4.5 Static fallback (POCO F3 2026-10-12: page change, Next now, unlock with a MIUI static wallpaper; gesture prompt)
- [x] 4.6 Lock screen handling (POCO F3 2026-10-12: switch sets the lock image with MIUI Wallpaper Carousel off; Fox live on home and lock shows on the MIUI lock screen)
- [x] 4.7 Wallpaper settings UI (POCO F3 2026-10-13: folder, picked pictures, thumbnail tap, shuffle, crossfade, parallax, remove, preview "Settings…"; engine renderer fix)
- [x] 4.8 Memory and performance budget (POCO F3 2026-10-13: no memory growth over 20 changes; frame work avg 1.1–1.8 ms, worst 8.7 ms accepted by the user, 0 late)
- [x] 4.9 URI permission resilience (POCO F3 2026-10-13: test folder renamed away → no crash, folder flagged, banner, Pick again restored it; revoked grant on fox-api33)
- [ ] Device checklist: items 1, 3, 6 passed on POCO F3 (covered in 4.4–4.8); **owed:** 2 (15-min interval across a 16-min lock), 4b (the change seen from another app), 5 (reboot keeps the wallpaper and the rotation) — deferred by the user 2026-10-13

## Phase 5 — Memory widgets
- [x] 5.1 Native widget framework (POCO F3 2026-10-13: Clock added from the picker, resized 4×1→5×2→2×1, moved, removed)
- [x] 5.2 MemoryStats + MemInfoParser tests (no UI: JVM tests on a POCO F3 and an emulator sample, instrumented on fox-api33/37)
- [x] 5.3 RAM gauge, boost and detail widgets (POCO F3 2026-10-13: gauge and details read right, Boost placed, moved and tapped; recent apps verified on emulators only, usage access not granted on the phone)
- [x] 5.4 BoostStrategy per API level (POCO F3 2026-10-13: "Limited on MIUI", self trim measured 4 MB; MIUI blocks closing other apps, see PLATFORM-NOTES M)
- [x] 5.5 (stretch) AppWidgetProvider export (fox-api37 in Pixel Launcher, 2026-10-13; the phone checks it when Fox hosts app widgets, phase 6)
- [ ] Device checklist: POCO F3 2026-10-13 — widgets added from the home menu, resized, moved, figures right; **owed:** 5.4 "boost reduces used memory on API ≤ 33", blocked by MIUI until Shizuku (Phase 9); the 34+ caption passed on fox-api37

## Phase 6 — App widgets
- [ ] 6.1 AppWidgetHost lifecycle: fox-api33/37 pass (one host, listening only while home is started, kept across config changes); **owed:** the POCO F3 (`dumpsys appwidget`)
- [ ] 6.2 Widget picker: fox-api33/37 pass (App widgets tab, 27 / 34 providers grouped by app, preview layouts and images); **owed:** the POCO F3
- [ ] 6.3 Bind + configure flow: fox-api33/37 pass (bind dialog, Cancel, Create, Always allow, configure, Back out of configure); **owed:** the POCO F3
- [ ] 6.4 Render, size, resize, touch coexistence: fox-api37 pass, fox-api33 renders (render, tap, size, resize to min, drag, swipe over a widget opens the drawer); **owed:** the POCO F3, incl. a scrolling widget (none on the emulators)
- [ ] 6.5 Persistence and provider removal: fox-api37 pass (Remove frees the id, uninstalling Calendar removes its widget, a process restart keeps them), fox-api33 start-up keeps its widget; **owed:** the POCO F3, incl. a reboot. Work-profile groups read "App · Work" (no work profile on the devices)
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

- **Compose UI tests cannot run on API 37** (fox-api37): Espresso 3.5.0, pulled in by `ui-test-junit4`, reflects on
  `InputManager.getInstance`, which Android 17 removed (`NoSuchMethodException`); 43 of 54 tests fail that way, the Room
  tests pass. Run the instrumented suite on fox-api33. Possible fix to try: pin
  `androidx.test.espresso:espresso-core` 3.7.0 (newest on Google Maven, 2026-10-12); untested, needs a dependency-log line.
- The BUILD FAILED-with-every-test-passing issue below does not happen on the emulators: fox-api33 ends BUILD SUCCESSFUL.
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

- 2026-10-14 — 6.5. Pure `AppWidgetCleanup.plan` (6 tests): an item leaves when its id is no longer the host's or its
  provider is gone; an id is freed when no remaining item uses it and it is not pending. `AppWidgetReconciler`
  (started from `HomeActivity.onCreate`) runs it on IO at start and, debounced 500 ms, whenever the home's set of app
  widget ids changes, the host hears `onProvidersChanged` / `onAppWidgetRemoved`, or a package changes. `AppWidgetBinder`
  keeps the ids of widgets still being added (allocated, not yet saved; restored ones re-marked after process death),
  so a run mid-flow leaves them; `HomeViewModel.addAppWidget` settles the id once the row is saved. The host's view
  map is now synchronized (ids are freed off the main thread). fox-api37: start-up found 3 ids and 3 items, nothing to
  do; popup Remove on the schedule widget freed id 12 (`dumpsys`: 3 → 2); `pm uninstall --user 0` Calendar: Android
  deleted id 11 and the month view left the home, Calendar restored with `install-existing` (its app icon also left
  the demo folder, the 2.x orphan pruning, and the folder collapsed to Gmail; rebuilt by dragging Calendar from the
  drawer onto Gmail, and a stray scripted drag of the RAM gauge undone); force-stop and relaunch: the clock still renders.
  Reboot not tried on the emulators (the POCO checklist has it).

- 2026-10-14 — 6.4. `FoxAppWidgetHost.onCreateView` makes `FoxAppWidgetHostView`s (kept by id): each reports its size
  in dp from `onSizeChanged` (posted; `updateAppWidgetSize` with `SizeF` from API 31, the deprecated min/max form
  before), answers `scrollsVerticallyAt` (window point, any view under it with `canScrollVertically`) and makes a
  `snapshot`. `AppWidgetView` loads the provider off the main thread (`providerInfo`, cached for resizing), then
  `AndroidView` of `host.createView`; a provider gone shows "no longer available"; the drag overlay draws the snapshot,
  since a second `createView` would steal the id's updates (PLATFORM-NOTES AW). `HomeItemView` wraps it in the native
  widgets' Initial-pass long-press detector. The arena gets `startsOnScrollingContent`: a gesture whose down lands on
  a widget view that can scroll vertically is left to it. Popup offers Resize unless `resizeMode` is none; the resize
  frame takes min/max spans from `AppWidgetSizing` with the provider and the item's cell size. **Found and fixed (also
  in native widgets since 5.1):** the long-press handler, keyed by item id, captured the item of its first
  composition, so a drag after a resize or move restored the old cells. Instrumented `FoxAppWidgetHostViewTest` 4
  (list that scrolls, title and outside do not, a list that fits does not, snapshot), fox-api33. fox-api37: Numeral
  clock (3 × 2) and Calendar month view (4 × 4) render live; next-month tap works, also after a drag; swipe up over
  the calendar opens the drawer; resize stopped at 3 rows and the calendar re-laid out; long-press drag moved it and
  kept 4 × 3. fox-api33: the clock renders after a reinstall; full instrumented suite 178/178. No scrolling widget on the emulators (Calendar schedule and Gmail want an account): the POCO checks it.

- 2026-10-13 — 6.3. Pure `AppWidgetBindFlow` (allocate → bind if allowed, else `AskToBind` → `Configure` when needed →
  `Place`; a refusal, a failed bind or a cancelled configuration frees the id: 7 tests) on `AppWidgetBindPort`, which
  `AppWidgetBinder` implements on the host (`bindAppWidgetIdIfAllowed`, the `ACTION_APPWIDGET_BIND` intent with profile,
  configuration skipped only when reconfigurable and optional, Launcher3's rule). `HomeActivity` carries the steps out:
  bind dialog through an `ActivityResultLauncher`, configuration through
  `startAppWidgetConfigureActivityForResult` + `onActivityResult` (no contract exists for it), the pending widget saved
  in the instance state. `HomeItem.AppWidget` now keeps `provider` and `userSerial` (the existing `component` and
  `userSerial` columns, no schema change); `HomeRepository.addAppWidget` places it as native widgets are placed. No size
  options at bind: 6.4 sends sizes once the view has its cell. fox-api37: Analog clock → bind dialog → Cancel left no
  id; Create → its "Select a Clock Face" configuration → placed 3 × 2 at 0,4; Always allow + Create → Back from the
  configuration left no id; Calendar month view then bound without a dialog and went to page 1 (4 × 4 did not fit
  page 0). fox-api33: dialog → Create → configuration → placed. Placed widgets draw as the placeholder until 6.4.
  6.1 and 6.2 unticked again: tasks are ticked only after the POCO check.

- 2026-10-13 — 6.2. Picker gets tabs, Fox (native, as before) and App widgets. `AppWidgetProviders` (`@Singleton`, IO)
  reads `getInstalledProvidersForProfile` for every profile, skipping keyguard-only and `WIDGET_FEATURE_HIDE_FROM_PICKER`
  providers; app label through `LauncherApps.getApplicationInfo` (the provider's `providerInfo` is hidden API); keeps
  each `AppWidgetProviderInfo` and its preview `RemoteViews` (made on IO: the constructor looks the package up) by key
  for previews and 6.3. Pure `AppWidgetSizing` (Launcher3's span rules with no cell gap: ceil of px over the cell, target
  cells when in range, min never above default, `resizeMode` fixes a direction, null when the minimum exceeds the grid;
  12 tests) and `AppWidgetCatalog.group` (by package + profile, collator order, personal first; 4 tests).
  `WidgetPickerViewModel` reloads on each opening. `HomeScreen.onCellSizeChanged` reports one cell (pager less side
  padding over the grid) so spans and previews use the real cell; previews draw at that size, scaled down to the sheet
  width: preview layout in an `AppWidgetHostView` inflating on IO (`setExecutor`), else the preview image or icon via
  Coil `android.resource://` URIs. Work-profile groups read "App · Work". UI tests +4 (grouping order, cells and pick,
  too-large left out, empty text), 6/6 on fox-api33. Found while testing: a test swipe on the sheet hit the scrim (the
  sheet node's bounds ignore its offset) and dismissed it. fox-api37: Calendar month view 4 × 4, Gmail 3 × 1, Clock
  Analog 3 × 2, At a Glance, previews right; a tap closes the sheet and logs the key and span.

- 2026-10-13 — 6.1. `FoxAppWidgetHost` (`feature/widgets/app`), `HOST_ID` 0x0F0C (3852, never change). A Hilt `@Singleton`
  (one host per process, PLATFORM-NOTES AW) injected into `HomeActivity`, which drives it: `startListening` from
  `onStart`, `stopListening` from `onStop` (counted per started instance, like `HomeVisibility`, for the ADR-19
  hand-over), `clearViews` from the home instance's `onDestroy` (the views hold the activity). A binder-size error from
  `startListening` is logged, not thrown (as Launcher3). `LocalAppWidgetHost` (null by default, for tests) provided
  next to `LocalNativeWidgets`; nothing reads it until 6.4. fox-api33/37, `dumpsys appwidget`: one Fox host (hostId
  3852) with callbacks at home; gone while Settings is in front (no widgets yet, so the service drops it), back on
  return; dark mode, density and font-scale changes keep the same `ActivityRecord`, pid and callbacks proxy.

- 2026-10-13 — 3.8 checkpoint (user request: full-screen navigation in other apps). Swipe in from the left or right
  edge = Back, up from the bottom = Home, up and hold = Recents, through three invisible `TYPE_ACCESSIBILITY_OVERLAY`
  strips that `FoxAccessibilityService` adds (ADR-20, PLATFORM-NOTES G): 12 dp sides from 96 dp down, 14 dp bottom
  over the navigation bar. Pure `NavGestureClassifier` (37 tests; Back 40 dp or a fast flick, Home 64 dp, Recents
  after a 250 ms hold past it, haptics on Back armed and on Recents). `NavigationGestures` keeps the strips up only
  while the switch is on (Gestures settings, default off), Fox's home is not resumed (`HomeVisibility`, counted
  from `HomeActivity.onResume/onPause`) and the screen is on and unlocked. Service description and disclosure now
  name the strips. Emulators switched to three-button for the check: Back from both edges, Home, swipe-and-hold
  Recents (fox-api33/37); strips gone on Fox's home, on screen off and on the lock screen, back after unlock
  (fox-api33); with gesture navigation the system takes the swipes and Fox fires nothing (fox-api33). **Found and
  fixed:** the home pauses and resumes around `onNewIntent`, and the hide that followed the show by 5 ms skipped
  strips not yet "attached", leaving them over the home (fox-api37). fox-api33 then hung (SurfaceFlinger stuck in the
  emulator's GL pipe after a nav-mode switch, PLATFORM-NOTES G); after the user restarted it, `GesturesContentTest`
  passed 16/16 there (+3 for the switch). Both emulators are back on gesture navigation; the test run uninstalled Fox
  from fox-api33, so it was reinstalled and set as home (its data and service setting are fresh). Not handled:
  immersive apps and games keep the strips.
  Visual feedback, asked for after the first build reached the POCO: `NavFeedbackView` in a fourth overlay window
  (full-screen, takes no touches, hidden between gestures). A bubble slides in from the edge at the finger with an
  arrow (Back) or circle (Home), turns accent once lifting would act, a ring fills during the Recents hold and the
  glyph becomes a square when Recents opens; fired, it swells and fades, let go short, it slides out. Dark mode and
  the system accent (API 31+, Fox orange before); a soft shadow for light backgrounds. Screenshots on fox-api37
  (three-button, then back to gesture navigation): partial and armed Back, rising Home, hold ring.

- 2026-10-13 — README (not a phase task). `README.md` for users: features, defaults, Boost limits per Android
  version, build and setup, MIUI notes, permissions, development pointers. Media in `docs/media/` (≈4.4 MB),
  recorded on fox-api37 with `adb shell screenrecord` and converted with ffmpeg. fox-api37 now has a demo layout
  (clock and the three memory widgets, a Gmail + Calendar folder), the Fox live wallpaper on home and lock, "Fox
  gestures" on, and swipe right on the first page mapped to Next wallpaper. Seen while recording: the Boost widget's
  1 × 1 preview in the picker cuts its caption to "Limited on" (on the home grid it reads in full).

- 2026-10-13 — 5.5 checkpoint. `MemoryGaugeAppWidget` (`AppWidgetProvider`, Hilt entry point, reads memory off
  the main thread with `goAsync`): RemoteViews layout with a ring `ProgressBar` (layer-list ring drawable), the %
  and "x.x GB free"; system accent colour and widget corner radius from API 31 (`values-v31`); every 30 minutes
  (`updatePeriodMillis` 1 800 000, the shortest honoured) and on tap (explicit refresh broadcast). Manifest receiver
  exported for `APPWIDGET_UPDATE`. Debug-only `PIN_MEMORY_GAUGE` (adb, DUMP) calls `requestPinAppWidget`, which
  Android takes only from an app in front (start a Fox activity first). Instrumented `MemoryGaugeViewsTest` 1
  (fox-api33/37: the RemoteViews applied read "65%", "2.1 GB free", ring at 65). fox-api37, Pixel Launcher: pin
  dialog "RAM gauge 2 × 1", added, shows 48 % / 2.0 GB free in the accent colour; a tap refreshed it ("1 updated").
  Fox itself hosts app widgets only from phase 6.

- 2026-10-13 — 5.4 POCO round 1: Boost asked 65 apps to close and nothing closed ("Nothing to free", apps still
  running). **MIUI silently ignores `killBackgroundProcesses` from apps**: proven with `run-as` (Fox's uid: nothing)
  against the shell (`am kill`: killed at once); Fox does hold the permission (PLATFORM-NOTES M). With the user's
  choice: `BoostCapability.limit(sdk, manufacturer)` → `BoostLimit.ANDROID_14` / `MIUI` / none; with a limit only the
  self trim runs and the widget shows "Limited on Android 14+" or "Limited on MIUI", each with its own explanation.
  A real boost on MIUI waits for Shizuku (Phase 9). Tests: format +1 (limit), targets (forLimit), content +1 (MIUI
  caption), 14 (fox-api33).

- 2026-10-13 — 5.4 checkpoint. `BoostStrategy` (name, `boost()` → apps asked to close). `SelfTrimStrategy` (always:
  `IconCache.clearMemory`, Coil memory cache, GC). `LegacyKillStrategy` (API ≤ 33: `killBackgroundProcesses` for
  launchable non-system apps minus `ProtectedApps`: Fox, the app in front from usage events when allowed, the keyboard
  from `DEFAULT_INPUT_METHOD`, default dialer and SMS, `ENABLED_ACCESSIBILITY_SERVICES`, the live wallpaper; music
  with a foreground service is out of this API's reach anyway). `MemoryBooster` is the composite: strategies from
  `BoostStrategies.forSdk`, available memory before / after (+500 ms), clamped at 0. `KILL_BACKGROUND_PROCESSES`
  (normal) in the manifest. Pure `BoostTargets` (choose, component and accessibility-setting parsers) and
  `BoostStrategies` with 5 tests. fox-api33 (no third-party apps): both strategies ran, 0 apps, −3 MB measured →
  "Nothing to free"; fox-api37: self trim only.

- 2026-10-13 — 5.3 checkpoint. Three native widgets (`feature/memory/widgets`): **RAM gauge** (1×1..3×2, default
  2×1: ring of memory in use, % and "x.x GB free"; warning colour from 90 %), **Boost** (1×1: rocket, lifts while
  boosting for at least 1.2 s, then "Freed N MB" measured or "Nothing to free", 4 s; on Android 14+ a "Limited on
  Android 14+" caption whose tap explains ADR-5), **Memory details** (2×2..4×4: used, available, cached, swap
  used/total, sparkline of the last 60 readings; from 3 rows the 3 most recently used apps, tap → app info for Force
  stop, or "Allow usage access" → Settings). Stateless `*Content` composables; `MemoryWidgetViewModel` (one reading
  every 2 s shared by all widgets, `WhileSubscribed(5 s)`, history by `scan`); `MemoryBooster` measures available
  memory before / after (+500 ms) around the work, which is only a GC until 5.4's strategies; `RecentAppsSource`
  (`UsageStatsManager`, AppOps check; `PACKAGE_USAGE_STATS` in the manifest); pure `MemoryFormat` and
  `BoostCapability` (5 tests). `MemoryWidgetContentTest` 13 (fox-api33). Emulators: picker previews read live memory;
  all three placed; boost showed "Freeing…" then "Nothing to free" (true: a GC freed nothing); fox-api37 shows the
  caption and its explanation. Fixed on the way: the gauge's "2.3 GB fr…" (now wraps), the caption cut to "Fox only
  on" (shorter text, icon under it). **POCO round 1:** gauge and details fine; Boost could not be added: in the picker
  its preview is a button and took the tap. Previews now sit under a tap layer that adds the widget (+1 picker test).
  **Round 2:** Boost placed but could not be moved: its content is a button, which took the press, so the widget's
  long press never fired (same for the detail widget's app rows). Widgets now watch for a long press in the Initial
  pass, before their content, and consume the rest of that gesture so the button does not click (+2 home widget
  tests). Boost stays 1×1 (no Resize entry), as specified.

- 2026-10-13 — **5.2 done.** `feature/memory`: pure `MemInfoParser` (`Name: value kB` lines; unknown, foreign-unit
  and cut-off lines skipped; null without `MemTotal`), pure `MemorySnapshot.of` (ActivityManager's total/available
  lead, as Settings shows them; `/proc/meminfo` gives cached, buffers, swap and stands in when ActivityManager reports
  nothing, estimating available as free + cached + buffers on kernels without `MemAvailable`; never negative, never
  above total), `MemoryStats` (`snapshot()`, `observe(intervalMs)` reading only while collected, `flowOn(IO)`,
  `distinctUntilChanged`). Tests: parser 5 (real samples from the POCO F3 and fox-api37 in
  `app/src/test/resources/meminfo`, truncated, garbage), snapshot 6, instrumented `MemoryStatsTest` 3
  (fox-api33/37: sane, Android's total = the kernel's, keeps reading). Not run on the phone: an instrumented run
  uninstalls Fox afterwards, which would wipe its data there.

- 2026-10-13 — 5.1 checkpoint. `feature/widgets`: `NativeWidgetSpec` (kind, name, description, min/default/max
  `Span`, content with a `NativeWidgetScope`), `NativeWidgetRegistry` (Hilt `Set` multibinding, `NativeWidgetModule`),
  `LocalNativeWidgets`, `WidgetPickerSheet` (live previews at default size), `clock/ClockWidget` (2×1..5×2, default
  4×1, ticks each minute, large type at two rows). Pure `WidgetResize` (an edge moves a cell at a time, stopping at
  min/max, the grid edge or the first taken cell) and `WidgetPlacement` (page in view, else another, else a new
  page), 10 tests. Grid: a native widget draws its content across its cells on a translucent rounded surface; long
  press → popup (Resize, Remove) + drag (multi-cell drag already worked); **Resize** shows `WidgetResizeFrame`
  (edge handles, live preview, commit on release, tap outside / Back / Home ends it). Home settings → **Widgets** →
  picker sheet → `HomeViewModel.addNativeWidget` (page in view, tracked from `onPageSettled(page)`).
  **Deviations:** package `feature/widgets`, not `feature/widgets/native` (`native` is a Java keyword; Hilt's
  generated Java would not compile); resize comes from the popup's Resize entry (handles under the popup's
  tap-outside layer could not be touched); no `configJson` yet (no widget needs one; it will come with a migration
  when one does). **Found on fox-api33 (fixed):** the frame's `swallowPointers` consumed a handle's first small
  moves in the Main pass, so its drag detector gave up before touch slop: handles never moved under a real finger,
  yet the test passed with 22 px steps; the test now drags in small steps. Tests: `HomeScreenWidgetTest` 6,
  `WidgetPickerSheetTest` 1 (fox-api33). fox-api33: Widgets → Clock placed at 0,0 (4×1); Resize → right +1 (5×1),
  bottom +1 (5×2); long-press drag → rows 2–3; Remove → gone; no crash.

- 2026-10-13 — **4.9 done.** POCO with a throwaway `Pictures/FoxTest` (3 test images over adb, deleted afterwards):
  renamed away → tapping its thumbnail logged the provider's `IllegalArgumentException`, flagged "FoxTest is gone",
  crossfaded to the first remaining picture, banner shown; renamed back → Pick again → its pictures showed again;
  removing the source gave the grant back (`persisted=0x0`). No crash. That session's crossfades ran at 120 Hz
  (8.3 ms gaps, 0.6–0.9 ms work), unlike the 60 Hz of the 4.8 run.

- 2026-10-13 — 4.9 checkpoint. Already there: loader catches, three failures skip an entry, scanner flags
  `needsReauth`, grants taken on add. New: `persistedUriPermissions` checked at scan time and at start
  (`WallpaperSources.checkAccess`: folders without a grant need picking again, picked pictures without one are skipped
  via `markUnreadable`); a failed load reports through `WallpaperSources.reportUnreadable` (folder grant checked at
  once, else rescanned); `observeRotationEntries` (renamed from `observeEnabledEntries`) leaves out sources needing
  re-authorization and falls back to the built-ins when nothing else is left; banner "Fox can no longer read “X”…
  Pick again" at the top of the wallpaper settings. **Found on fox-api33 (fixed):** a moved folder made the provider
  throw `IllegalArgumentException`, uncaught in the engine → **the launcher crashed**; and its listing came back
  empty → entries silently dropped. Now caught, the folder is looked up before listing, and the engine scope logs
  instead of crashing (PLATFORM-NOTES W). fox-api33: grant revoked by clearing External Storage → next load denied,
  folder flagged, built-ins stepped in (turned off), banner, Pick again restored it; folder renamed → flagged as
  gone with its entries kept, renamed back + Pick again → its entries rotate again. Tests: DAO +3 (12),
  `WallpaperSourcesAccessTest` 4, content +2 (16), applier 4 (fox-api33/37).

- 2026-10-13 — 4.8 measured. **Bitmaps:** the loader decodes straight to the surface size (`ImageDecoder`
  `setTargetSize` + `setCrop` from pure `WallpaperGeometry.coverDecode`), so a photo never exists at full size (a
  4000×3000 one used to be decoded whole, 48 MB, then copied); the engine ends a running fade before decoding the next
  image, so it holds at most two surface-sized bitmaps; `onTrimMemory` (≥ RUNNING_LOW) ends a fade at once (there is no
  pre-decoded "next" to drop). **Tools:** pure `FrameStats` (per crossfade: frame gaps from Choreographer, work per
  frame; logged in debug), a debug-only `NEXT_WALLPAPER` receiver (adb only: `android.permission.DUMP`), and
  `scripts/perf/wallpaper.sh` (warm-up, forced GC via `run-as kill -10` before each meminfo, readings every 10
  changes, crossfade lines, engine trouble). Debug build (the receiver and the frame log need it). Tests: geometry
  12 (cover decode replaces sample size + crop), `FrameStats` 5, instrumented `WallpaperLoaderTest` 4 (exact size,
  centre crop, EXIF rotation, upscale; fox-api33/37).

  POCO F3, Fox live wallpaper, 1100 ms crossfade, parallax off (1080×2400 images), 20 changes 3 s apart:

  | after | PSS | RSS | Graphics | Java heap |
  |---|---|---|---|---|
  | 3 warm-up changes | 208.7 MB | 285.6 MB | 109.6 MB | 15.0 MB |
  | +10 changes | 209.0 MB | 285.9 MB | 110.0 MB | 15.0 MB |
  | +20 changes | 198.0 MB | 273.2 MB | 110.1 MB | 16.1 MB |

  No growth. Crossfades: 67 frames each, work per frame avg 1.1–1.8 ms, worst single frame 8.7 ms (two of 20 fades
  had a frame over 8 ms), 0 late frames in all 20; the very first fade after a fresh start had a 34 ms frame and 2
  late ones (caches filling), hence the warm-up. MIUI runs the wallpaper at 60 Hz (16.5 ms gaps), not 120. A first
  run without warm-up or GC read +22 MB: uncollected garbage and the first textures, gone in the run above.

- 2026-10-13 — **4.7 done.** POCO round 3 on the renderer build: every swipe, the unlock change and the thumbnail
  taps crossfaded on screen, zero dead-surface lines; parallax switched the decode width 1296 ↔ 1080; the preview ran
  its own engine (`created (preview)`) and its "Settings…" opened `.WallpaperSettings` → Fox's wallpaper settings.
  Not done: restarting Fox repeatedly to stress the startup race (the user did not ask for it); the renderer's
  rebind path ("lost its surface") has not fired on the phone yet, so watch for it.

- 2026-10-13 — 4.7 POCO round 2: **the freeze came back** with the same build, on a fresh start before any touch
  (first frame at 00:49:31, dead-surface lines right after): a race on MIUI's surface swap at bind time, not an
  action. `lockHardwareCanvas` keeps a hidden renderer Fox cannot see or fix, so the engine now draws through its own
  `WallpaperRenderer` (`HardwareRenderer` + `RenderNode`): rebinds the surface on every surface callback and, when a
  frame comes back `SYNC_LOST_SURFACE_REWARD_IF_FOUND`, rebinds and redraws it (logged once: "lost its surface:
  binding it again"; "frames are not reaching the screen" if even that fails). Instrumented `WallpaperRendererTest` 3
  (a red frame read back through an `ImageReader`; no surface; released surface) on fox-api33/37. Also fixed: after
  the source of the picture on screen went away, the next change showed the fallback picture again
  (`WallpaperPlaylist.onScreen`, +3 tests). Round-2 findings: removing sources and the crossfade setting work;
  "Change when switching pages" was turned off where "Move with the pages" (parallax) was meant.

- 2026-10-13 — 4.7 POCO round 1: folder (+24), picked picture, thumbnail taps and shuffle all reached the engine, but
  **the wallpaper never changed on screen**: every crossfade frame logged MIUI HWUI's `The surface had destroyed before
  draw` (≈99 per 800 ms fade) while SurfaceFlinger kept the engine's first frame. Not reproduced after reinstalling: a
  fresh engine survived lock/unlock and the folder picker round trip, with no dead-surface line. Suspected: a race with
  MIUI swapping the wallpaper surface right after the package update rebinds the service. The engine now handles
  `onSurfaceDestroyed` (stops the fade, forgets the size so a same-size new surface reloads and redraws; before, it was
  skipped as unchanged), skips frames on an invalid surface, and logs its lifecycle (debug only: created/preview,
  surface created/size/destroyed, visible/hidden, showing entry …, destroyed). If it freezes again, those lines say why.

- 2026-10-13 — 4.7 checkpoint. The wallpaper settings now hold everything: live wallpaper banner, Next now; *When it
  changes* (interval, unlock, page change); *How it looks* (shuffle, crossfade 300–2000 ms snapped to 100 ms, parallax
  on/off, lock screen); *Sources* (built-in / picked pictures / folders: switch, remove with confirmation and grants
  given back, "access lost: tap to pick the folder again", Add a folder via `OpenDocumentTree`, Add pictures via
  `PickMultipleVisualMedia`; the last enabled source cannot be turned off); *Wallpapers* (Coil thumbnail grid in chunked
  rows, the one showing outlined, unreadable ones dimmed, tap = `RotationScheduler.showNow` → `WallpaperPlaylist.jumpTo`).
  The screen takes a `WallpaperSettingsActions` interface (16 actions). New: DAO `setSourceEnabled`,
  `reauthorizeSource`, `getSource`, `observeEntryCounts` (no schema change); `WallpaperSources` add/remove/reauthorize
  with persisted grants; `FoxSettings` `setCrossfadeMs`, `wallpaperParallax` (engine decodes at screen width without
  it); `WallpaperEntryEntity.MAX_FAILURES`. Deep link (ADR-19): `settingsActivity` = alias `.WallpaperSettings`.
  **Found:** Coil 3 cannot load `android.resource://…/raw/<name>` (thumbnails blank) → resource ids for built-ins;
  on API 37 with no home instance the alias opened a second HomeActivity in an ordinary task → hand-over to the home
  instance. Tests: playlist +2 (21), DAO +3 (9, fox-api33/37), `WallpaperSettingsContentTest` 14 (fox-api33; API 37
  Espresso blocker). fox-api33: preview "Settings…" → Fox wallpaper settings; photo picker added 2 (grants persisted),
  folder `Pictures/FoxTest` scanned +3, tap on a thumbnail showed it live, remove gave the grants back, parallax off
  drew at screen width. fox-api37: deep link (one HomeActivity, home task), photo picker. **Follow-up:** while Fox is
  not the default launcher, HomeRoot's gate covers everything, settings included, so the preview's button shows the
  gate.

- 2026-10-12 — **4.6 done.** POCO: Fox live applied with *Home screen and lock screen*, switch off → the MIUI lock screen
  shows the live wallpaper and follows each change (keyguard layer at alpha 0, no separate lock image). With the switch
  on, each change sets a still lock image once Wallpaper Carousel is off. Both in PLATFORM-NOTES W.

- 2026-10-12 — 4.6 checkpoint. "Also change the lock screen" (default off, `wallpaper_change_lock_screen`): pure
  `StaticTargets` (home while Fox live is not the system's, lock when asked; 4 tests) decides at run time what
  `StaticApplyWorker` writes, in one `setStream` with `FLAG_SYSTEM`/`FLAG_LOCK`. The worker now applies the entry on
  screen (`WallpaperPlaylist.showing`, shared with the engine; +1 test) rather than an id passed in. Turning the switch on
  catches the lock screen up at once (`enqueueLockOnly`, KEEP so a pending change is not lost). Instrumented
  `StaticWallpaperApplierTest` 4 (fox-api33/37), `WallpaperSettingsContentTest` 7 (fox-api33; API 37 Espresso blocker).
  Emulators, lock screen enabled for the test (then disabled again): switch on → lock gets entry 1, Next now → home and
  lock entry 2 in one write; Fox live via *Home and lock* → keyguard shows the live wallpaper and follows Next now with
  the switch off; switch on → a still copy. fox-api33 delivers `USER_PRESENT` twice per unlock (the 5 s debounce
  absorbs it). POCO: lock image set on each unlock once MIUI Wallpaper Carousel was off (it overwrote it before).
  Findings in PLATFORM-NOTES W.

- 2026-10-12 — **4.5 done.** POCO with a MIUI static wallpaper: page change, Next now and unlock each set a Fox image
  (`StaticApplyWorker` SUCCESS, 180–300 ms from the change to `setStream`); the two-finger swipe offered the live
  wallpaper and Apply brought it back.

- 2026-10-12 — 4.5 checkpoint. `StaticWallpaperApplier` (entry decoded at the full display size, `setStream(FLAG_SYSTEM)`
  with a JPEG; `markFailed` on an unreadable entry) behind a unique `StaticApplyWorker` (latest change wins; skips if
  the live wallpaper was applied meanwhile). `RotationScheduler.advanceNow` enqueues it whenever Fox's live wallpaper
  is not the system's. **Deviation, for safety:** the default two-finger swipe up (Next wallpaper) must not replace a
  user's own wallpaper by accident, so `WallpaperAdvancer.advance` refuses without the live wallpaper and the executor
  returns `NEEDS_LIVE_WALLPAPER` → "Use the Fox live wallpaper?" (Apply opens the preview). Explicit opt-ins (interval,
  unlock, page change, "Next now") do use the fallback. **Found:** without the live wallpaper its service is not
  running, so the unlock receiver moved to `FoxApp` (process-wide; still exported); intervals under 15 minutes still
  need the live wallpaper (the settings say so). `LiveWallpaper` helper (active? preview intent). Banner: "Changes show
  with a flash: smooth crossfades need the Fox live wallpaper." Tests: executor +1; instrumented
  `StaticWallpaperApplierTest` 2 (emulator-only via `assumeTrue`: it replaces the wallpaper). fox-api33: 110/110; on
  both emulators with a static wallpaper: Next now and unlock set static images (screenshots), the gesture offered the
  live wallpaper and changed nothing, Apply brought the live wallpaper back.

- 2026-10-12 — **4.4 done.** POCO round 2: every page settle changed the wallpaper (7 in a row, `Page settled` 1/0
  alternating), the interval fired; no configuration change, no error.

- 2026-10-12 — 4.4 POCO round 1: interval, unlock (×2), two-finger swipe changed it; unlock with its switch off was
  skipped. Two follow-ups: (1) after a skipped interval check (a manual change 44 s earlier) the timer waited a whole
  extra interval; now it recomputes from the last change (fox-api33: manual 23:40:47 → interval 23:41:47). (2) no
  page-change log on the phone: on fox-api33 with a second page (made by dragging Messages to the right edge), each
  settle changed it (`Page settled: 1` → next, `Page settled: 0` → next); a bounce seen first came from the script's
  Home presses. New debug-only logs: `Page settled: N`, `Configuration changed: …` (to catch restarts).

- 2026-10-12 — 4.4 checkpoint. Pure `TriggerPolicy` (gesture/manual always; interval when ≥ 90 % of it has passed, as
  WorkManager runs periodic work early within its window; unlock debounced 5 s; page change debounced 1 s; a clock set
  back counts as long ago; `untilNextInterval` for the engine timer; offered intervals 1, 5, 10, 15, 30, 60 min, 3 h,
  1 day; 11 tests). `FoxSettings`: `rotationTriggers`, `wallpaperChangedAt` (`setActiveWallpaper(id, changedAt)` replaces
  `setActiveWallpaperEntryId`). `RotationScheduler.advanceIfDue(trigger)`, `syncSchedule()` (unique periodic
  `RotationWorker` for ≥ 15 min, cancelled below; called at start and on change). Engine: intervals under 15 min on a
  timer while visible (not in the preview); a runtime `ACTION_USER_PRESENT` receiver in the service. **Bug found on the
  emulator:** registered `RECEIVER_NOT_EXPORTED`, it never fired — SystemUI sends `USER_PRESENT` under its own uid. Now
  `RECEIVER_EXPORTED` (a protected broadcast, so no app can fake it). Page change: `HomeScreen.onPageSettled` →
  `HomeViewModel` → `advanceIfDue(PAGE_CHANGE)`. **Pulled from 4.7:** a minimal Wallpaper settings screen (live
  wallpaper status + Apply via the system preview, Next now, Change every, on unlock, on page change), reached from Home
  settings; `WallpaperSettingsContentTest` 6. On fox-api33: 1-minute interval fired at once (last change long ago) and
  a minute later; unlock changed it each time; the 15-minute `RotationWorker` ran and advanced with the emulator clock
  moved 16 min ahead (WorkManager refuses forced periodic work "before schedule"; clock restored). fox-api37: unlock
  and the 1-minute interval. 108/108 instrumented on fox-api33. Time-of-day schedule: not done (stretch).

- 2026-10-12 — **4.3 done.** On the POCO (live wallpaper applied through the system preview, replacing the user's
  Wallpaper Changer app): 11 two-finger swipes up cycled the three built-ins with smooth crossfades, the image stayed
  across apps; no errors.

- 2026-10-12 — 4.3 checkpoint. `feature/wallpaper/engine/`: pure `WallpaperGeometry` (sample size that never drops
  under the target, cover scale, centre crop, eased crossfade alpha, parallax shift; 13 tests), `WallpaperLoader`
  (`ImageDecoder`, software allocator so `WallpaperColors` can read it, EXIF applied, sampled then cropped into a bitmap
  of exactly 1.2 × surface width × surface height), `FoxWallpaperService` (`@AndroidEntryPoint`; engine on its own
  main-thread scope; shows `activeWallpaperEntryId` or the first enabled entry; `Choreographer` crossfade, 800 ms
  (`FoxSettings.crossfadeMs`); at most two bitmaps, the old one recycled; hidden → fade ends at once; first image after
  (re)creation without a fade; offsets → parallax; `onComputeColors` + `notifyColorsChanged` after each change; a
  failed decode → `markFailed`). Manifest service with BIND_WALLPAPER, `xml/wallpaper.xml`, thumbnail. **Pulled forward
  from 4.4:** `RotationScheduler.advanceNow(forward)` (playlist → `savePlaylist` + `activeWallpaperEntryId` +
  `markShown`, skips entries failed 3×) now backs the Next/Previous wallpaper actions instead of the plan's temporary
  button; 4.4 adds only the triggers. Bug found on the emulator and fixed: the first "next" re-picked the image already
  showing (empty history); an empty history now starts from what is on screen. On fox-api33 and fox-api37: applied via
  the system preview (`CHANGE_LIVE_WALLPAPER`), double tap → Next wallpaper crossfaded Dawn→Dusk→Forest→Dawn (mid-fade
  frames captured, no black frame); the image stayed after an app visit and after killing Fox's process (stand-in for
  a reboot). fox-api33: 102/102 instrumented.

- 2026-10-12 — **4.2 done.** Pure `feature/wallpaper/WallpaperPlaylist` (immutable; `next()`/`previous()` return the id
  and the playlist to keep). Shuffle walks a permutation drawn from the seed; at the end of a pass a new seed (fixed
  LCG step, so a stored playlist replays the same) whose permutation does not start with the entry showing. When
  entries change, the walk continues after the most recent history entry still present, so no old entry list needs
  storing: removing the current wallpaper continues with its successor. `previous()` pops the history, skipping
  vanished entries; history capped at 50. `core/model/PlaylistMode` + `PlaylistState`; `FoxSettings.playlistState`,
  `setPlaylistMode`, `savePlaylist(seed, history)` (history as `IdListCodec` text; seed null until first chosen).
  Tests: 18 playlist, 3 codec. Nothing visible until 4.4 wires it.

- 2026-10-12 — **4.1 done.** Room v3 (auto-migration 2→3, `3.json` committed): `WallpaperSourceEntity` (FOLDER_TREE /
  PICKED_IMAGES / BUILT_IN, `needsReauth`) and `WallpaperEntryEntity` (cascade on source delete, unique `sourceId+uri`,
  `lastShownAt`, `failedCount`); `WallpaperDao` (enabled entries flow, `applyScan` transaction, `markShown` resets
  failures, `markFailed`). Pure `diffScan` (by URI; a changed file keeps id and history, failures reset). `SourceScanner`
  (`@HiltWorker`) lists a folder's own images with one `DocumentsContract` child query; `SecurityException` /
  `FileNotFoundException` → `needsReauth`; `scanNow(sourceId)` and a daily unique periodic scan (scheduled from `FoxApp`).
  `WallpaperSources.ensureBuiltIn()` (from `FoxApp`) keeps a BUILT_IN source listing the shipped `res/raw` images by
  name-form `android.resource://…/raw/<name>` URIs (stable across builds). The three images are generated gradient
  placeholders (~40 KB each); the user offered real ones, to be swapped in (only images fine to publish: public repo).
  Tests: `ScanDiffTest` 8, `WallpaperDaoTest` 6. On fox-api33/37: DB v3, built-ins present, home items kept (pull with
  the `-wal` file, or fresh rows look missing). On the POCO, before/after the update: dock 5, pages 9, folder items 2,
  folders 1 — unchanged — plus 3 wallpapers. Lint caught a 3.7 leftover (`DrawerLayer`'s Modifier param not named
  `modifier`); fixed.

- 2026-10-12 — 3.7 checkpoint. The classifier follows a single-finger vertical drag when
  `GestureConfig.followsVerticalDrag(fromDock, upward)` says so (asked at slop): `DragStarted(dy)`, `DragUpdate(dy)`,
  `DragEnd(velocityY)` (`DragEnd(0)` on CANCEL), never a swipe; other fingers are then ignored (+9 tests, 49). The arena
  passes them to `onDragStart/onDrag/onDragEnd`, now consumes every single-finger vertical swipe, and reads `enabled`
  only when a gesture starts (the drawer it opens would otherwise cancel its own drag). `HomeScreen` follows a swipe up
  only while it maps to Open drawer (pages or dock); downward and remapped swipes fire on lift. **The 1.4 detector
  and its 3.5/3.6 workarounds are gone** (down tracker, `following`). The open drawer's surface outside its list
  (search, tabs) closes with a `draggable` on `DrawerLayer`, by the list's rule (closes once pulled 20 % down).
  Tests: `HomeScreenGestureTest` +3 (follows before lift, surface drag closes, short pull stays); fox-api33 96/96. By
  hand on fox-api33 and fox-api37: held 30 % up the drawer is half-way (screenshot), then opens; 10 % flick opens, 10 %
  slow drag does not; swipe down → shade; dock swipe up opens; header drag 30 % closes, 10 % stays; list scroll and
  pull-at-top unchanged; system strip ignored. Found: an instrumented run can leave the service in "Binding services"
  (PLATFORM-NOTES G); `adb install -r` clears it.

- 2026-10-12 — **3.6 done.** On the POCO (three-button): Home while home → `HOME_KEY` → first page; Home from an app →
  no gesture, page kept; Home with settings open only closed them; Home remapped to Open drawer opened it, and the next
  Home only closed it; Back with nothing open stayed home. After the install the service came back by itself (the
  package update rebinds it; no repair needed).

- 2026-10-12 — 3.6 checkpoint. **Home key:** `onNewIntent` tells "while home" (window focus and no
  `FLAG_ACTIVITY_BROUGHT_TO_FRONT`, Launcher3's test) from "back from an app". Every press closes what is open
  (drag, popup, folder, drawer, settings, the accessibility dialog); a press while home with nothing open is the
  `HOME_KEY` gesture through the executor (default: first page). Behaviour change: back from an app, Home now keeps
  the page (it used to jump to page 0), as Launcher3. The Home button row is in the Gestures screen now.
  **System strip:** `WindowInsets.mandatorySystemGestures` bottom; the arena ignores gestures starting at or below
  `systemGestureTop`, and the drawer detector (now told the real down position, not the post-slop one) does too.
  **Bug found and fixed:** Back with nothing open moved Fox's home task behind the last app (Android 12+ treats
  unhandled Back on a root launcher activity as move-to-back); PLATFORM-NOTES G said no handler was needed. An empty
  lowest-priority `onBackPressedDispatcher` callback in `HomeActivity.onCreate` now swallows it. Tests: arena +2
  (strip), `HomeScreenGestureTest` +3 (Home while home / from an app / with the drawer open); fox-api33 93/93. By hand
  on fox-api33 and fox-api37 (gesture navigation): Home while home → HOME_KEY → first page; from the Phone app → no
  gesture; Home remapped to the drawer opens it, and Home then closes it; a system swipe from the bottom edge opens no
  drawer; Back with nothing open stays home; Back closes the drawer and settings. Known gap: the gesture-navigation
  home swipe made while already home arrives without window focus, so it closes things but is not `HOME_KEY`.

- 2026-10-12 — **Surviving "clear recents" on MIUI** (user question). The phone's log: OneKeyClean killed processes at
  adj 0–200 regardless of foreground services, so nothing inside Fox can exempt it. The user locked Fox in Security →
  Boost speed → Lock apps; the next clear (13:52, three tasks removed) left Fox's process (same pid) and its service
  running. Before the lock, the 13:48 clears showed the gesture path of the automatic restart on the phone (double tap
  → stopped → restarted → screenshot `DONE` in 0.6 s). The Gestures screen now shows the lock tip on Xiaomi phones
  (`GesturesUiState.showMiuiLockHint`, +2 `GesturesContentTest`). PLATFORM-NOTES G updated.

- 2026-10-12 — **3.4 done; automatic restart works on the POCO.** I granted WRITE_SECURE_SETTINGS over the existing
  wireless adb at the user's request (the MIUI "USB debugging (Security settings)" toggle was already on). The user
  cleared recents twice: each time OneKeyClean killed Fox, Fox restarted, and 2 s later `AccessibilityRepair` brought
  the service back in about 0.5 s; swipe down then worked. Power menu and screenshot (two files saved) completed 3.4.
  One glitch: a swipe 2 s after the restart started landed in the half second while Fox's entry is removed, read as
  `OFF` and showed the disclosure. Now `AccessibilityRepair.isRepairing` makes `HomeViewModel` wait for the restart
  and then run the action; checked on fox-api33 by swiping the moment the restart began (log: `NEEDS_ACCESSIBILITY`,
  restarted, `after restarting the service: DONE`, shade open, no dialog).

- 2026-10-12 — **Automatic restart of the stopped service (ADR-18)**, which the user asked for sooner. The POCO's log
  showed the stuck state again minutes later (13:24, new process). Fox declares WRITE_SECURE_SETTINGS; after the
  user's one-time `adb shell pm grant dev.foxlauncher android.permission.WRITE_SECURE_SETTINGS`, `AccessibilityRepair`
  turns Fox's own entry off and on when the service is `STOPPED`: 2 s after every process start (`FoxApp`), when a
  gesture meets a stopped service (`HomeViewModel` restarts it and then runs the action), and from a Restart button on
  the Gestures banner. A "Restart it by itself" row shows whether it is on, or the adb command. Never turns the service
  on (`OFF` keeps the disclosure). ADR-9's rule in CLAUDE.md now names both privileged paths. On **fox-api33**: 86/86
  instrumented (then 11/11 `GesturesContentTest` after a wording fix: next to Restart the banner no longer says to
  use the settings); after a test run left the service crashed, starting Fox restarted it by itself; the Restart
  button restarted it; a swipe down on a stopped service restarted it and opened the shade in one go. On
  **fox-api37** a test run's force-stop *removes* Fox from the enabled list instead (status `OFF`, the disclosure),
  so the crashed state is an Android 13 / MIUI behaviour; the repair rightly leaves `OFF` alone.

- 2026-10-12 — **Accessibility "stopped" state** (user report on the POCO: "turned it on but it doesn't get up").
  The phone's log: after double tap → recents, the user cleared recent apps; MIUI's OneKeyClean killed Fox (home or
  not) and `dumpsys accessibility` showed the service Enabled + Crashed, nothing Bound, `accessibility_enabled` 0. The
  system settings still show the switch on and the system never restarts it; turning it off and on fixes it. Fox said
  "turn it on", which misled. Now `SystemActions.status` is `AccessibilityStatus` ON / STOPPED / OFF (connected;
  listed in `ENABLED_ACCESSIBILITY_SERVICES` but not connected; neither), the executor returns
  `ACCESSIBILITY_STOPPED`, and both the dialog and the Gestures banner say to turn it off and on. On fox-api33 an
  instrumented run produces the same stuck state (a plain kill or a reinstall recovers by itself): the stopped dialog
  and banner showed, and off+on brought the service back without restarting Fox. fox-api37: on and off paths
  unchanged. Tests: executor +1 (11), `GesturesContentTest` +1 (8), dialog +1 (3); fox-api33 83/83. PLATFORM-NOTES G
  updated; CLAUDE.md notes the re-toggle after instrumented runs.

- 2026-10-12 — 3.5 checkpoint. `feature/settings/`: `HomeSettingsScreen` (root: Gestures, Hidden apps; opened by the
  "Home settings" action, i.e. long press on empty space, and by a new drawer-menu entry), `GesturesScreen` /
  `GesturesContent` (every gesture but `HOME_KEY`, which joins in 3.6, with its action; action picker; app chooser
  with search; shortcut list of the chosen app; shield on accessibility actions and a status banner with Turn on →
  disclosure; Reset to defaults → `FoxSettings.resetGestures`), `GesturesViewModel`, `GestureLabels`. Navigation is a
  saved `SettingsRoute` stack over the home (ADR-17). `OpenShortcut` gained an optional `label` (old JSON reads with
  ""). **Search focus is back** for the "Search apps" action: `DrawerScope.isSearchRequested` →
  `DrawerScreen/DrawerContent(autoFocusSearch)` (ADR-16's promised path). **Until 3.7** the drawer detector follows a
  swipe up only while that swipe (pages or dock, by where it starts) still maps to Open drawer; otherwise the arena
  runs the remapped action (`HomeUiState.gestureMap`). **Bug found and fixed:** the Home key left a settings screen
  open (Hidden apps had the same flaw); Home now clears the settings stack. Tests: `GesturesContentTest` 7 (incl. the
  plan's LockScreen-for-DOUBLE_TAP), `HomeScreenGestureTest` +2 (remapped swipe up, open search), JSON +1; fox-api33
  81/81 after fixing my own remap test (Compose's `swipeUp()` starts on the dock, i.e. DOCK_SWIPE_UP). By hand on
  fox-api33 and fox-api37 through the UI: double tap → recents, power menu, screenshot each worked (finishing 3.4 on
  emulators), swipe up → notifications, reset restores the drawer. **Slip:** one `./gradlew :app:installDebug` ran
  without `ANDROID_SERIAL` and installed on the POCO too (data kept) before the emulator run; memory now forbids it.

- 2026-10-12 — 3.4 on the **POCO F3** (user): swipe down with the service off → `NEEDS_ACCESSIBILITY` and the
  disclosure; after enabling "Fox gestures" in MIUI's settings the service connected in the same process (no restart)
  and swipe down → notifications, two-finger swipe down → quick settings, double tap → lock all returned `DONE`;
  gestures kept working after unlocking. No crash. Recents, power menu and screenshot have no default gesture, so they
  wait for 3.5's remapping.

- 2026-10-12 — 3.4 checkpoint. Pure `feature/gestures/ActionExecutor` behind three ports: `SystemActions`
  (`AccessibilitySystemActions` → `FoxAccessibilityService.perform`), `AppLauncher` (`RepositoryAppLauncher`, via
  `AppRepository`), `WallpaperAdvancer` (`feature/wallpaper`, `NoOpWallpaperAdvancer` until phase 4); bound in
  `GesturesModule`, not activity-scoped (nothing needs the Activity). `execute` returns an `ActionResult` instead of
  emitting an event; `NEEDS_ACCESSIBILITY` only when the service is **off in settings** (`AccessibilityManager` list),
  so an enabled service still reconnecting after a restart does not prompt. `FoxAccessibilityService` asks for no
  events (`eventTypes = 0` on connect), companion `connected` + `perform`; manifest entry, `accessibility_service_config`,
  label "Fox gestures". `HomeScreen.onGesture` now passes `HomeControls` (open drawer/search, default page, page
  overview); `HomeActivity` adds home settings and builds `ExecContext`; `HomeViewModel.onGesture` runs the mapped
  action and raises `accessibilityPrompt` → `AccessibilityDisclosureDialog` → `ACTION_ACCESSIBILITY_SETTINGS`.
  **Not built yet:** page overview and home settings screens (no phase plans them; both log), `OpenSearch` focus
  (opens the drawer; TODO(3.5), ADR-16). Tests: `ActionExecutorTest` 10, `AccessibilityDisclosureDialogTest` 2;
  fox-api33 72/72. By hand on fox-api33 and fox-api37: service off → swipe down shows the disclosure, Open settings
  opens the accessibility settings; service on (adb) → swipe down opens the shade, double tap locks.

- 2026-10-12 — **3.3 done.** User on the POCO F3: paging, drawer follow and close, icon popup and drag, folders all
  normal; a two-finger swipe left does not page. The phone's log showed every gesture tried: SWIPE_LEFT/RIGHT only on
  the edge pages, SWIPE_LEFT_2F, SWIPE_RIGHT_2F, DOUBLE_TAP, LONG_PRESS_EMPTY, SWIPE_UP_2F, SWIPE_DOWN, PINCH_OUT and
  PINCH_IN, each with its mapped action; no crash.

- 2026-10-12 — 3.3 on **fox-api33** (the user launched it; fox-api37 was not running): 69 instrumented tests, one
  failure in a test written this session, `aSwipeUpStillOpensTheDrawerAndIsReported`. Its expectation was wrong for
  the 3.3 design: the drawer detector follows a swipe up, the drawer shows, and the arena (off while the drawer shows)
  drops the gesture, so `SWIPE_UP` is not reported. That is wanted: in 3.4 a reported `SWIPE_UP` would open the
  drawer twice. Renamed `aSwipeUpStillOpensTheDrawerWhichTakesTheGesture`, asserting nothing is reported; 3.7 revisits
  it. Added `aSwipeDownIsReportedAndLeavesTheDrawerClosed`. Feature tests then 59/59. By hand over adb: swipe down,
  long press on empty space, swipe left/right on a one-page home and double tap log their gesture and action; swipe
  up opens the drawer without a log line; a long press on an icon opens its popup; tapping Phone opens the dialer.
  Installed on the POCO for the user's check. Later the user launched **fox-api37**: the same hand check gives the same
  results there (Compose tests cannot run on 37, see Known issues), no crash.

- 2026-10-12 — 3.3 checkpoint, **code done; not yet run on an emulator** (none attached; the user launches them).
  `feature/gestures/GestureArena.kt`: `Modifier.gestureArena(enabled, isOnItem, onGesture, pagerTakesSwipeLeft/Right,
  dockTop)` (a modifier rather than the plan's wrapper composable) feeds every change, batched historical samples
  included, to the classifier in the Initial pass and runs the long-press timer. It consumes the rest of a gesture
  once it is the home's: two fingers (on `Claimed`), pinch, double tap, long press on empty space, three fingers, edge
  swipes. **Deviation:** the 1.4 drawer detector stays until 3.7 and the arena never consumes single-finger vertical
  swipes, so the drawer keeps following the finger and flinging (replacing it now would make the drawer jump open on
  release until 3.7). Thresholds: touch slop, long-press and double-tap timeouts from `ViewConfiguration`; fling
  300 dp/s (the drawer's; the system minimum, 50 dp/s, turns slow lifts into swipes); double-tap slop 100 dp (Android's,
  absent from Compose); pinch 48 dp. In `HomeScreen` it sits after the drag layer (a drag that owns the finger has
  consumed its events) and is off while the drawer, a folder, a popup or a drag is shown. `HomeDragState` gained
  `dockTop()` and `itemAt(position)`; `GestureConfig.dockTop` became a lambda read at the first DOWN.
  `HomeViewModel.onGesture` logs `Gesture <type> -> <action>` (debug builds) until 3.4 runs actions. New instrumented
  `GestureArenaTest` (11) and `HomeScreenGestureTest` (5), not run yet.

- 2026-10-12 — **3.2 done.** `feature/gestures/classifier/` (no Android imports): `GestureClassifier`, `GestureConfig`,
  `PointerSample`, `GestureResult`, and a least-squares `VelocityTracker` over the last 100 ms. 40 classifier + 6
  velocity tests. Differences from the plan, on purpose: (1) a fifth result, **`Claimed`**, when a single finger
  passes slop vertically or a second finger lands, so 3.3 can consume before the pager pages with two fingers or an
  item reacts; the type still comes on the first lift. (2) `Recognized(type)` has no `consumePointer` flag: the arena
  consumes on `Claimed`/`Recognized` and decides about item long presses itself. (3) The pager callbacks are
  `pagerTakesSwipeLeft/Right` and are read **at the first DOWN**, since a swipe can page onto the last page before it
  lifts. (4) The arena size is in `GestureConfig` for the 25 % rule. Two-finger = exactly two fingers down together
  (three → `Cancelled`); pinch = spacing change past the threshold and larger than the fingers' common movement
  (covers one finger held still); double tap is recognized when the second tap lifts; a long press ends the gesture
  (moving on is an item drag). Nothing visible on a device yet.

- 2026-10-12 — **3.1 done.** `core/model/GestureType` (15 gestures, stored by name) with `DEFAULT_GESTURE_MAP`, and
  `GestureAction` (sealed, `@Serializable`, stable `@SerialName`s, `requiresAccessibility` true for the six system
  actions). `FoxSettings.gestureMap` / `setGesture` keep one JSON key, `gesture_map`, holding **only the gestures the
  user changed** (`GestureMapJson`), so untouched gestures follow the defaults as they change and 3.5's reset can just
  drop entries. Reads forgive a newer Fox's data: unknown action or missing field → `None`, unknown gesture dropped,
  unreadable JSON → defaults (`classDiscriminator = "type"`, `ignoreUnknownKeys`). 12 unit tests. Nothing visible on a
  device yet, so no emulator or phone check.

- 2026-10-12 — **No search pill on the home (ADR-16, user request).** `HomeScreen` shows pages and the dock only;
  swipe up opens the drawer, whose search field is the one search UI. The pill was the only thing that asked the drawer
  to focus its search, so `openedBySearch`, the `drawer` slot's focus argument and `DrawerContent(autoFocusSearch)`
  went too (Phase 3's `OpenSearch` re-adds a focus path; see ADR-16). The dock now sits right above the navigation bar.
  Tests: the two pill tests are gone with the feature (`tappingThePillOpensTheDrawerWithSearchFocused`,
  `reopeningWithThePillFocusesSearchAgain`); tests that only used the pill to open the drawer now swipe up; new
  `theHomeHasNoSearchBar`. fox-api33: 53/53 pass. On fox-api33 and fox-api37 after `pm clear`: dock of four, no pill,
  swipe up shows the drawer with its search field. The 2.7 free middle slot stays.

- 2026-10-12 — **API 29 emulator removed** (user): `.#emulator` now carries only the API 33 and 37 images and
  platforms; `fox-api29` deleted with `avdmanager delete avd`; ADR-15 amended. The old image stays in the Nix store until
  the next `nix-collect-garbage`.

- 2026-10-12 — 2.7 First-run default layout, **code done; POCO F3 check pending.** Pure `DefaultLayoutBuilder`
  (`DefaultLayout.kt`, 11 tests) takes a `DefaultAppResolver` (role → launcher component), the catalog and the dock
  width: dialer, SMS, browser, camera in that order, personal profile only, deduplicated, no gaps for roles that
  resolve to nothing; an odd dock wider than 4 keeps its middle slot free (5 slots: 0, 1, 3, 4), which is this
  session's reading of "drawer pill in the middle dock slot": the drawer pill already exists as "Search apps" under
  the dock. `DeviceDefaultApps` resolves DIAL, a BROWSABLE http link and STILL_IMAGE_CAMERA (chooser → the single
  system handler, as Launcher3) plus `Telephony.Sms.getDefaultSmsPackage`, then maps each package through
  `getLaunchIntentForPackage` (PLATFORM-NOTES A). `HomeRepository.placeDefaultLayout` waits for the catalog and inserts
  through `HomeDao.insertIntoEmptyHome` (one transaction, only into an empty home; +3 instrumented tests).
  `HomeViewModel.onLauncherReady` reads `isFirstRun` from the store (the UI state's initial value says true), guards
  against overlapping resumes, places the layout, then clears the flag; a crash in between re-runs harmlessly. The
  "Add test items (debug)" action and `HomeRepository.addApp` are gone. Emulators: after `pm clear`, API 29/33/37 all
  showed Phone, Messages, (free), Chrome, Camera; a relaunch did not double it; emptying the dock and restarting kept
  it empty; Phone opens the dialer; the drawer menu has only Hidden apps. Instrumented: fox-api33 54/54, BUILD
  SUCCESSFUL. fox-api37: Compose tests blocked by Espresso (known issues). fox-api29 showed 19/54 teardown timeouts
  (`Activity never becomes requested state DESTROYED`) only while Fox is home (7/7 with the stock launcher); left
  there, because the user scoped testing to API 33+.

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

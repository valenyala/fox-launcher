# STATUS

Update this file at the end of every session. Keep it terse. Newest session notes on top.

**Current phase:** 2 — Home grid (phases 0 and 1 code complete; both device checklists still owed)
**Next task:** 2.4 Drag & drop

## Environment facts (verified 2026-09-17)

- Host: NixOS x86_64, 12 cores, 31 GB RAM, git 2.54. No JDK, Gradle, adb or Android SDK on PATH → Phase 0.1 must create the flake.
- Devices: none attached as of 2026-09-17 (`adb devices` empty). Record model + Android version here once one is used. Every device checklist in Phase 0 is still unrun.
- Dev shell (0.1): nixpkgs pinned to b1b8759 (nixos-unstable, 2026-09-16); JDK 17.0.20.1, Android platform 37, build-tools 37.0.0 (bumped from 36 in 0.2, see ADR-12), platform-tools/adb 37.0.1, Gradle 8.14.4 from the shell. The build itself uses the wrapper, Gradle 9.7.1.
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
- [x] 2.3 Page + dock rendering, wallpaper offsets (desk only)
- [ ] 2.4 Drag & drop
  - [x] `DragResolver` + tests (pure drop rules)
  - [ ] `HomeDragState`, long-press pick-up, `DragOverlay`, target highlight, edge page-flip
  - [ ] Apply `DropAction`s in `HomeRepository` (move, dock, create/add folder, remove)
  - [ ] Remove zone, drag from drawer, page creation / empty-page cleanup
  - [ ] Device checklist
- [ ] 2.5 Folders
- [ ] 2.6 Item popup: shortcuts, app info, remove, uninstall
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

## Session notes

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

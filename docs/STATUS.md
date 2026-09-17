# STATUS

Update this file at the end of every session. Keep it terse. Newest session notes on top.

**Current phase:** 0 — Toolchain & scaffold
**Next task:** 0.5 Test infrastructure

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
- [ ] 0.5 Test infrastructure
- [ ] 0.6 CI workflow
- [ ] Device checklist passed on: —

## Phase 1 — App catalog & drawer
- [ ] 1.1 AppEntry + AppRepository
- [ ] 1.2 IconCache
- [ ] 1.3 App drawer screen
- [ ] 1.4 Minimal home + swipe-up drawer
- [ ] 1.5 Work profiles
- [ ] 1.6 Hidden apps
- [ ] Device checklist passed on: —

## Phase 2 — Home grid
- [ ] 2.1 Room schema + repository
- [ ] 2.2 GridLayoutEngine
- [ ] 2.3 Page + dock rendering, wallpaper offsets
- [ ] 2.4 Drag & drop
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

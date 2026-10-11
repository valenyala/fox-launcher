# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

Fox Launcher is an Android home-screen launcher written in Kotlin with Jetpack Compose. Three headline features define it:

1. **Full gestures** — every swipe (1- and 2-finger, four directions), double-tap, long-press and pinch on the home screen is remappable to an action. System actions (notification shade, quick settings, lock screen, recents) go through an AccessibilityService, never hidden APIs.
2. **Memory widgets** — launcher-native widgets on the home grid that show real RAM state and offer a "boost". What a boost can actually do depends on the Android version (see Hard rules).
3. **Seamless wallpaper rotation** — a live-wallpaper service owned by the launcher crossfades between images from user-chosen sources on interval / unlock / gesture triggers. The launcher window shows the wallpaper through (`windowShowWallpaper`) and never draws it itself.

## How to work in this repo

The repository started empty; the Gradle project is created in Phase 0. All work is driven by the plan in `docs/`:

| File | Purpose |
|---|---|
| `docs/PLAN.md` | Roadmap, phase list, and the session protocol you must follow |
| `docs/STATUS.md` | Which tasks are done, current phase, blockers. **Update it every session.** |
| `docs/phases/NN-*.md` | Task-level detail for one phase. Open only the current one. |
| `docs/DECISIONS.md` | Settled architecture decisions. Do not re-litigate; supersede with a new ADR if truly needed. |
| `docs/PLATFORM-NOTES.md` | Android launcher platform facts and traps. Read the section for the feature you touch. |

Session protocol in one line: read `docs/STATUS.md` → open the current phase file → do the next unchecked task → run the verification gate → tick it in `STATUS.md` → commit.

## Toolchain

The host is NixOS. Outside the dev shell there is no `java`, `adb` or Android SDK on PATH. Always run Gradle from the shell:

```
nix develop            # JDK 17, Android SDK (platform + build-tools), platform-tools/adb, gradle
```

`flake.nix` and `.envrc` are created in Phase 0 task 0.1. Android SDK on NixOS needs the aapt2 override in `GRADLE_OPTS`; the flake sets it. If Gradle says `aapt2` failed to execute, the override is missing.

Device testing needs a physical phone over adb or an emulator (the flake can include one). Device checklists in phase files are only runnable with a device attached.

## Commands (valid once Phase 0 is complete)

```
./gradlew assembleDebug                                    # build debug APK
./gradlew :app:testDebugUnitTest                           # all JVM unit tests
./gradlew :app:testDebugUnitTest --tests "dev.foxlauncher.feature.gestures.GestureClassifierTest"
./gradlew :app:testDebugUnitTest --tests "*GestureClassifierTest.flingUp*"   # single test method
./gradlew :app:lintDebug                                   # Android lint → app/build/reports/lint-results-debug.html
./gradlew :app:connectedDebugAndroidTest                   # instrumented + Compose UI tests (device required)
./gradlew check                                            # everything that runs without a device

# device workflow
./gradlew installDebug
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package set-home-activity dev.foxlauncher/.HomeActivity          # make default launcher without the dialog
adb shell settings put secure enabled_accessibility_services dev.foxlauncher/dev.foxlauncher.feature.gestures.system.FoxAccessibilityService
adb shell settings put secure accessibility_enabled 1                          # enable our service for gesture system actions
adb shell appops set dev.foxlauncher GET_USAGE_STATS allow                     # usage-stats special permission
adb logcat -s FoxLauncher:V AndroidRuntime:E

# performance (release-like build; results in build/perf)
./gradlew :app:assembleProfiling && adb install -r app/build/outputs/apk/profiling/app-profiling.apk
./scripts/perf/measure.sh <label>                          # cold start, page swipes, drawer, memory
./scripts/perf/ab.sh <open|scroll|pages|return> A.apk B.apk   # A/B per-frame work of one scenario
```

**Pre-commit gate** (must be green before every commit):

```
./scripts/ci.sh
```

That is the whole CI for this repo (ADR-13): it runs
`./gradlew check assembleDebug :app:compileDebugAndroidTestKotlin` — unit tests, lint, the APK, and the
instrumented sources that nothing else compiles while no device is attached. It re-enters `nix develop` on
its own, so it works from outside the shell. `.githooks/pre-commit` execs it and the dev shell points
`core.hooksPath` there, so the gate runs on every `git commit`; `git commit --no-verify` is the escape
hatch. There is no GitHub Actions workflow — do not add one without a new ADR.

## Architecture

Single Gradle module `:app`, applicationId `dev.foxlauncher`, minSdk 29, package-by-feature. Stack: Kotlin, Compose + Material 3, Hilt (KSP), Room, Preferences DataStore, WorkManager, Coil, coroutines/Flow. No LiveData, no RxJava, no multi-module until a phase explicitly introduces it.

```
dev.foxlauncher
├── FoxApp.kt              @HiltAndroidApp
├── HomeActivity.kt        the ONE activity: HOME intent, singleTask, hosts Compose nav, owns AppWidgetHost
├── core/
│   ├── apps/              LauncherApps wrapper → AppRepository (StateFlow<List<AppEntry>>), IconCache, profile helpers
│   ├── data/              Room db (home items, folders, wallpaper sources/entries), DataStore settings + JSON-serialized maps
│   ├── model/             plain Kotlin models shared across features (AppEntry, HomeItem, GestureAction, ...)
│   ├── ui/                theme, shared composables, adaptive-icon rendering
│   └── common/            dispatchers, Log wrapper (tag "FoxLauncher"), Result helpers
└── feature/
    ├── home/              pages + dock, GridLayoutEngine (pure Kotlin), drag & drop, folders, item popups
    ├── drawer/            app drawer, search, hidden apps, work-profile section
    ├── gestures/          GestureClassifier (pure Kotlin) → GestureArena composable → ActionExecutor; system/FoxAccessibilityService
    ├── wallpaper/         FoxWallpaperService (live wallpaper engine), RotationScheduler, sources, playlist, settings
    ├── memory/            MemoryStats, BoostStrategy per API level, memory widgets
    ├── widgets/           native widget registry + AppWidgetHost integration
    └── settings/          settings screens
```

Unidirectional data flow everywhere: repositories expose `Flow`, ViewModels expose `StateFlow<UiState>` and take intent-style functions, composables are stateless. Writes are `suspend` on `Dispatchers.IO`.

### The four data flows that cross packages

- **Apps:** `LauncherApps.Callback` (package added/removed/changed, profiles) → `AppRepository` rebuilds its list → drawer and home observe it. Home items reference apps by `ComponentName.flattenToString()` + user serial number (`UserManager.getSerialNumberForUser`), never by `UserHandle` directly.
- **Gestures:** raw Compose pointer events (Initial pass) → `GestureClassifier` (pure Kotlin state machine, all thresholds injected) → `GestureEvent` → `GestureActionMapper` (mapping from DataStore) → `ActionExecutor` (needs Activity; system actions need the accessibility service to be enabled, otherwise it prompts). The classifier only consumes events once a non-pager gesture is recognized, so the `HorizontalPager` keeps single-finger horizontal swipes.
- **Wallpaper:** anything that wants to change the wallpaper (WorkManager interval, unlock receiver, gesture action, settings button) calls `RotationScheduler.advance()`, which only writes `activeEntryId` to DataStore. `FoxWallpaperService.Engine` observes that key, decodes the next image off-thread at surface size, and crossfades on a `Choreographer` loop. Nothing else draws wallpaper. Static fallback (`WallpaperManager.setStream`) exists only when our live wallpaper is not the active one.
- **Memory:** `MemoryStats` (`ActivityManager.getMemoryInfo` + `/proc/meminfo`) polled only while a memory widget is visible → widget UI. Boost selects a `BoostStrategy` by `Build.VERSION.SDK_INT` and reports the *measured* before/after delta.

### Persistence shape

- Room `HomeItem(id, container[PAGE|DOCK|FOLDER], page, col, row, spanX, spanY, type[APP|SHORTCUT|FOLDER|APP_WIDGET|NATIVE_WIDGET], component, userSerial, shortcutId, appWidgetId, nativeWidgetKind, folderId, title)`. Schema JSON is exported to `app/schemas/` and every schema change ships a migration.
- Settings in Preferences DataStore; structured settings (gesture map, widget config) are kotlinx.serialization JSON strings under one key each.

## Hard rules

- **No hidden-API reflection** (`StatusBarManager.expandNotificationsPanel`, `IActivityManager`, etc.). It is blocklisted at runtime and rejected by Play. System actions go through `FoxAccessibilityService.performGlobalAction` only. Shizuku is the one sanctioned privileged path and lives in Phase 9 behind explicit opt-in.
- **Memory boost must be honest.** `ActivityManager.killBackgroundProcesses` affects other apps only on API ≤ 33; on API 34+ it only kills our own processes. Never show a fabricated "freed X MB"; show the measured delta and, on 34+, say what the widget can and cannot do.
- **`HomeActivity` must never visibly restart.** Keep `configChanges` covering orientation/screen size/uiMode/locale/density and handle them in Compose. A recreating launcher looks like a crash.
- **Nothing from `PackageManager`, `LauncherApps`, bitmap decoding or Room touches the main thread.**
- **Pure logic has no Android imports.** `GestureClassifier`, `GridLayoutEngine`, `WallpaperPlaylist`, `MemInfoParser`, sorting/search live in classes testable on the JVM and each has unit tests. Do not add Robolectric to test them.
- **New dependency** → add to `gradle/libs.versions.toml` and append a line to `docs/DECISIONS.md`. Do not guess version numbers; check Maven Central.
- **minSdk is 29.** Do not add `@RequiresApi`/`SDK_INT` gates for anything below it.
- **Never delete or weaken a test to make the gate pass.** Fix the code or record a blocker in `docs/STATUS.md`.
- One task per commit, message `phaseN: <task title>`.

## Agent skills

### Issue tracker

Issues live as GitHub issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, using the default label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

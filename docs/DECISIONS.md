# Architecture Decision Records

Short, numbered, append-only. To change a decision, add a new ADR that says "Supersedes ADR-N".
Smaller models: do not re-open these in a session; they were made with the trade-offs already weighed.

## ADR-1 Kotlin + Jetpack Compose, single `:app` module
Compose is the only UI toolkit; XML views appear only where the platform forces them
(`AppWidgetHostView` inside `AndroidView`, accessibility/wallpaper service config XML).
A single module with strict package-by-feature layout keeps Gradle simple for automated sessions.
Modularization is allowed only if a phase task introduces it explicitly (none currently does).

## ADR-2 minSdk 29, targetSdk/compileSdk = latest stable
API 29 gives `RoleManager` (default launcher prompt), system gesture navigation insets and
exclusion APIs, `ImageDecoder`, and lock-screen global action, so no compatibility branches exist
below it. `compileSdk`/`targetSdk` track the newest stable platform (36 at time of writing; bump
when Google requires it for Play).

## ADR-3 Wallpaper rotation is a live wallpaper, not `WallpaperManager.setBitmap`
`setBitmap` re-sets the system wallpaper and produces a visible flash/jump; it cannot animate.
A `WallpaperService` owned by the launcher draws the current image and crossfades to the next one
in-process, which is seamless on home, on app switch and across launcher restarts. The launcher window
uses `windowShowWallpaper` so there is one drawing path. `setBitmap/setStream` remains as a fallback
when the user has not applied our live wallpaper, and for the lock screen where live wallpaper support
varies by OEM.

## ADR-4 System actions use an AccessibilityService; no hidden APIs, no root
Expanding the notification shade, quick settings, locking, recents, screenshot and the power menu are
only reachable by third-party apps via `AccessibilityService.performGlobalAction`. Reflection on
`StatusBarManager`/`IActivityManager` is blocklisted and rejected by Play. The service declares no
event types, so it costs nothing while idle. The user enables it from our settings with a disclosure.

## ADR-5 Memory boost capability matrix (be honest in the UI)
- Stats: `ActivityManager.getMemoryInfo()` + `/proc/meminfo` — all API levels, no permission.
- API 29–33: `killBackgroundProcesses(pkg)` on launchable third-party packages not in the foreground
  and not on the allowlist (self, IME, dialer/SMS defaults, accessibility services, active media session,
  live wallpaper). It kills cached processes only; the OS may recreate them.
- API 34+: `killBackgroundProcesses` only affects our own package. The widget shows stats, trims our own
  caches, and explains the limitation. Real background killing on 34+ requires Shizuku (ADR-9, Phase 9).
- Never display a computed/fake "freed" number; display the measured `availMem` delta.

## ADR-6 Libraries
Hilt (KSP) for DI, Room for structured persistence, Preferences DataStore for settings (structured
settings serialized with kotlinx.serialization JSON under a single key), WorkManager for deferred/
periodic work, Coil for image loading in UI (not for the wallpaper engine, which decodes directly),
kotlinx-coroutines everywhere. No LiveData, RxJava, Koin, Moshi, Gson, Glide.

## ADR-7 Tests are JVM-first
All decision logic (gesture classification, grid math, playlist order, meminfo parsing, search ranking)
lives in pure-Kotlin classes with no Android imports and gets JUnit4 tests run by `testDebugUnitTest`.
Compose UI tests and instrumented tests exist for a few critical flows only, because they need a device
and automated sessions often have none. Robolectric is not used.

## ADR-8 Two widget kinds
"Native widgets" are Compose composables registered in the launcher (memory widgets live here); they
are cheap, themeable and gesture-friendly. "App widgets" are third-party `AppWidgetProvider`s hosted
through `AppWidgetHost`. Both are `HomeItem` rows with different `type`; the grid engine does not care.

## ADR-9 Shizuku is the only privileged path, and it is optional
Phase 9 may add a `BoostStrategy` that uses Shizuku to force-stop packages on API 34+. It must be a
separate opt-in with its own explanation, must degrade to ADR-5 behaviour when Shizuku is absent, and
must never be required for any other feature.

## ADR-10 Gesture classification is separate from Compose
`GestureClassifier` is a pure state machine fed with `(pointerId, x, y, timeMs, phase)` samples and a
`GestureConfig` of thresholds. The Compose `GestureArena` only adapts pointer events to samples and
consumes events when the classifier reports a recognized non-pager gesture. This keeps the pager's
native horizontal paging and makes the classifier exhaustively testable.

## Dependency log
(append `- <lib> <version> — reason` when adding a dependency)

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

## ADR-11 AGP 9 built-in Kotlin; Kotlin 2.3.21 pinned by KSP
AGP 9 ships Kotlin support itself and **refuses** `org.jetbrains.kotlin.android`
("no longer required for Kotlin support since AGP 9.0"), so the app module applies only
`com.android.application` plus the separate `plugin.compose`, `plugin.serialization`, `ksp` and `hilt`
plugins. AGP 9.4.0 bundles KGP 2.2.10; applying the Compose/serialization plugins at 2.3.21 upgrades
the buildscript classpath, which is the documented upgrade path. Kotlin is **2.3.21, not the newest
2.4.20**, because KSP's newest release (2.3.12) is built against Kotlin 2.3.20 and no 2.4.x KSP exists
yet. Revisit when KSP publishes a 2.4 line. `kotlinOptions` is gone; the `kotlin { }` extension that
AGP registers (`KotlinAndroidProjectExtension`) is still there and carries `jvmToolchain(17)`.

## ADR-12 compileSdk/targetSdk 37 (supersedes the "36" figure in ADR-2)
AndroidX as of 2026-09 (`core-ktx` 1.19.0 and friends) declares an AAR-metadata minimum
`compileSdk` of 37, so 36 fails `checkDebugAarMetadata`. The dev shell therefore pins Android
platform 37 and build-tools 37.0.0. minSdk stays 29 per ADR-2.

## ADR-13 CI runs locally as a pre-commit hook, not on GitHub Actions
Phase 0.6 originally called for `.github/workflows/ci.yml`. The workflow was written, verified and then
removed: the checks now run on this machine before every commit instead. `scripts/ci.sh` runs
`check assembleDebug :app:compileDebugAndroidTestKotlin` (unit tests, lint, APK, instrumented sources),
re-entering `nix develop` when the caller is not already in the dev shell, and `.githooks/pre-commit`
execs it. The hooks directory is tracked, and the dev shell's `shellHook` sets `core.hooksPath` on entry,
so the gate installs itself. It checks the working tree, not the staged snapshot; `git commit --no-verify`
is the escape hatch for docs-only or WIP commits. The hosted runner bought little here — one developer,
one machine, no attached device, and every run would re-provision SDK 37 — while the local gate is the
same commands the session protocol already requires. Restoring the workflow means reverting this file and
`git show 75192eb -- .github`.

## ADR-14 The home screen is portrait-only
The user asked for no landscape home (2026-10-11), as Pixel's launcher does on phones by default. `HomeActivity` sets
`android:screenOrientation="portrait"` (PLATFORM-NOTES H). It keeps the full `configChanges` set and the
rotation-safe code (a size change cancels a drag; Remove never reaches into the pages), because coming home from a
landscape app still changes the configuration, and Android ignores the lock on large screens for targetSdk 36+.
This retires the phone rotation checks in the device checklists: 0.3 "rotating does not flash", Phase 1 item 4,
2.4 "rotation mid-drag", and Phase 2 item 6 "rotate with folder open". Instrumented tests still run in whatever
orientation the test activity gets, so they must not assume one.

## ADR-15 Emulators live in a second dev shell, `.#emulator`
The user asked for virtual devices next to the POCO F3 (2026-10-12). `flake.nix` builds two SDKs from one
`composeSdk` helper: the default shell is unchanged (same derivation), and `.#emulator` adds the emulator,
platforms 29/33 and the x86_64 Google APIs images for API 29 (minSdk), 33 (the phone's level, and the last one
where boost can kill other apps, ADR-5) and 37 (compile/target). The images are about 5 GB, and `scripts/ci.sh`
enters the default shell on every commit, so they stay out of it. Google APIs images rather than Play Store or
bare AOSP: Play services without the Play-signed lock-down, so `adb root` works. `scripts/emulator.sh` owns the
AVDs (`fox-api<N>`, Pixel 7 profile, 4 GB RAM) and gives each a fixed console port, so serials are stable:
emulator-5554/5556/5558. Another API level means adding it to `androidEmu` and to the script's tables.
Amended the same day, at the user's request: testing covers only the phone's API 33 and above, so API 29 (image,
platform, `fox-api29`) is gone. The other two keep their ports, emulator-5556 and emulator-5558.

## ADR-16 No search bar on the home screen; search lives in the drawer
The user asked for it (2026-10-12). Phase 1.4 put a "Search apps" pill under the dock that opened the drawer with its
search field focused. It is gone: the home shows pages and the dock only, swiping up opens the drawer, and the
drawer's own search field is the one search UI. Nothing asks the drawer to focus its search any more, so that path
went too (`DrawerContent(autoFocusSearch)`, the home's `openedBySearch`). Phase 3's `GestureAction.OpenSearch` brings
it back with a real trigger; the removed code is in the commit that introduced this ADR. Supersedes the pill in
phase 1.4 and the "drawer pill in the middle dock slot" of 2.7, whose free middle slot stays.

## ADR-17 Settings screens stack over the home instead of a NavHost
Task 3.5 brought the second settings screen (gestures) and a root ("Home settings"). A `NavHost` composes only its
current destination, so the home, its kept drawer and their state would leave composition whenever settings open and
be rebuilt on return, the costliest work in the app (see the perf notes). `HomeActivity` instead keeps a saved list of
`SettingsRoute`s and draws the top one over the home, which stays composed and covered (Material surfaces block
touches). Each screen's back pops one route; the Home key clears them all. The navigation-compose dependency stays
unused until a destination needs real navigation (deep links, arguments).

## ADR-18 An adb-granted WRITE_SECURE_SETTINGS restarts Fox's own stopped accessibility service
Narrows ADR-9 ("Shizuku is the only privileged path"), at the user's request (2026-10-12). MIUI's "clear recent apps"
kills Fox and leaves its accessibility service enabled but crashed; the system never restarts it until it is turned
off and on (PLATFORM-NOTES G), and it happened several times a day on the POCO. Fox declares WRITE_SECURE_SETTINGS,
which no install receives: the user opts in by running `adb shell pm grant dev.foxlauncher
android.permission.WRITE_SECURE_SETTINGS` once (on MIUI, "USB debugging (Security settings)" must be on). With it,
`AccessibilityRepair` turns Fox's own entry in `ENABLED_ACCESSIBILITY_SERVICES` off and on, two seconds after any
process start and when a gesture finds the service stopped. Limits, all binding: it touches only Fox's own entry; it
acts only when the service is already listed as enabled (`STOPPED`), never turning it on for the user (the disclosure
of PLATFORM-NOTES G stays the only way in); nothing else may use the permission without a new ADR; without the grant
Fox behaves exactly as before, telling the user to turn the service off and on. It is public API
(`Settings.Secure.putString`), not hidden-API reflection.

## ADR-19 The live wallpaper's settings button opens an alias of HomeActivity
Task 4.7 makes the wallpaper settings the `settingsActivity` of `wallpaper.xml`, which the system preview starts by
component name. A second activity would break "HomeActivity is the one activity" and draw the settings away from the
home they belong to. Instead the manifest has an exported `activity-alias` `.WallpaperSettings` targeting
`HomeActivity`: the running home instance receives it in `onNewIntent` and opens `SettingsRoute.WALLPAPER` (ADR-17).
With no home instance running (Fox not the default launcher, or not started yet) the alias creates an instance in an
ordinary task (seen on API 37); that instance hands the request to the home instance with a HOME intent carrying
`EXTRA_OPEN_SETTINGS` and finishes before composing anything, so a launcher never runs twice. The hand-over is not
counted as a Home press. Back from these settings goes home, not back to the preview.

## ADR-20 Navigation gestures in other apps are accessibility-overlay strips
The user asked for full-screen navigation in other apps (2026-10-13): swipe in from the left or right edge = Back,
up from the bottom = Home, up and hold = Recents. The POCO F3 runs three-button navigation, so Android's own gesture
navigation is not there to do it. A third-party app
sees touches in other apps only through windows of its own, and the only such window that needs no further
permission is `TYPE_ACCESSIBILITY_OVERLAY`, added by an accessibility service. So Fox's existing service (ADR-4)
puts three invisible strips on the screen edges (12 dp sides from 96 dp down, 14 dp bottom, over the navigation bar),
classifies their touches with a pure `NavGestureClassifier`, and performs `GLOBAL_ACTION_BACK/HOME/RECENTS`.
Consequences, all accepted: a touch that starts on a strip never reaches the app below (edge taps, the outer few dp
of the keyboard's edge keys and of the navigation buttons); there is no Back arrow, only a haptic tick; the strips
are up only while the switch is on (default off), another app is in front and the screen is unlocked. With Android's
gesture navigation the system takes the same swipes first and Fox performs nothing (fox-api33: one Back, no Fox
action). Rejected: touch exploration and gesture detection (`FLAG_REQUEST_TOUCH_EXPLORATION_MODE`) change how
every touch works; `SYSTEM_ALERT_WINDOW` overlays are another permission for the same windows; reading the window
list to step aside for the keyboard needs `canRetrieveWindowContent`, which the disclosure rules out. The service
still asks for no events and reads no content; its description and the disclosure now name the strips.

## Dependency log
(append `- <lib> <version> — reason` when adding a dependency)
- AGP (com.android.application) 9.4.0 — build system; built-in Kotlin, see ADR-11.
- Kotlin (compose + serialization plugins) 2.3.21 — see ADR-11.
- KSP 2.3.12 — annotation processing for Hilt and Room; newest release, built against Kotlin 2.3.20.
- Gradle wrapper 9.7.1 — required by AGP 9; distribution SHA-256 pinned in gradle-wrapper.properties.
- androidx.compose:compose-bom 2026.09.00 — single source of truth for Compose artifact versions.
- androidx.activity:activity-compose 1.13.0 — ComponentActivity + setContent, role-request launcher.
- androidx.core:core-ktx 1.19.0 — platform ktx extensions.
- androidx.lifecycle 2.11.0 (runtime-ktx, runtime-compose, viewmodel-compose) — collectAsStateWithLifecycle, VMs.
- androidx.navigation:navigation-compose 2.10.1 — in-activity navigation (settings, drawer routes).
- com.google.dagger:hilt-android 2.60.1 (+ hilt-android-compiler) — DI, per ADR-6.
- androidx.hilt 1.4.0 (hilt-navigation-compose, hilt-work, hilt-compiler) — Hilt bridges for Compose nav and WorkManager.
- androidx.room 2.8.5 (runtime, ktx, compiler) — structured persistence, per ADR-6.
- androidx.datastore:datastore-preferences 1.2.1 — settings store, per ADR-6.
- androidx.work:work-runtime-ktx 2.11.2 — wallpaper rotation scheduling, per ADR-6.
- io.coil-kt.coil3:coil-compose 3.6.2 — UI image loading only (not the wallpaper engine), per ADR-6.
- org.jetbrains.kotlinx:kotlinx-coroutines 1.11.0 (android, test) — concurrency everywhere.
- org.jetbrains.kotlinx:kotlinx-serialization-json 1.11.0 — JSON for structured DataStore values.
- org.jetbrains.kotlinx:kotlinx-collections-immutable 0.5.2 — stable Compose parameters.
- junit 4.13.2, kotlin-test 2.3.21, turbine 1.2.1 — JVM unit tests, per ADR-7.
- androidx.test runner/rules 1.7.0, androidx.test.ext:junit 1.3.0, compose ui-test-junit4/manifest (BOM) — instrumented tests.

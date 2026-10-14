# Phase 9 — Power features (optional)

Each task is independent and opt-in for the user. Do not start this phase before Phase 8 unless the user asks.

## 9.1 Shizuku boost strategy (ADR-9)
- Add `dev.rikka.shizuku:api` + `provider`; declare the Shizuku provider in manifest; request permission at runtime;
  `ShizukuBoostStrategy` obtains `IActivityManager` via `ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))`
  using the `android.app.IActivityManager` AIDL stub compiled from a copied `.aidl` (hidden-API stubs are acceptable here because
  the call goes through Shizuku's privileged process, not our own; document this exception in ADR-9's spirit). Actions:
  `forceStopPackage(pkg, userId)` for the ADR-5 candidate list, or `killBackgroundProcesses`. Placed first in `CompositeStrategy`
  when Shizuku is running and permitted. UI: separate opt-in card "Deep boost (Shizuku)" with explanation and status.
- As built (ADR-21): no `IActivityManager` stub. A Shizuku user service of Fox's, running as the shell, runs
  `cmd activity kill` (background processes, as `killBackgroundProcesses`), so Fox's process uses no hidden API.

## 9.2 Online wallpaper sources
**Dropped by the user, 2026-10-14: not to be built.**
- `WallpaperSource` type `REMOTE(provider, query)`; providers Wallhaven and/or Unsplash (API keys via `local.properties` →
  `BuildConfig`, never committed); download to cache dir with size cap; attribution shown in settings; respect metered network
  constraint in WorkManager.

## 9.3 Per-app gestures and macros
**Removed by the user, 2026-10-14, after it was built (194040e): not to be rebuilt.**
- Gesture map keyed additionally by "context" (home page N, drawer, folder); actions that run a list of actions with delays.
- As built (2026-10-14): contexts are the home pages only; the drawer and folders have no gesture surface (their
  swipes scroll), so they keep none. Macro steps are the fixed actions and apps, not shortcuts.

## 9.4 External intent API
**Removed by the user, 2026-10-14, after it was built (d18cf18): not to be rebuilt.**
- Exported `BroadcastReceiver` guarded by a signature permission `dev.foxlauncher.permission.CONTROL`: actions
  `dev.foxlauncher.NEXT_WALLPAPER`, `PREV_WALLPAPER`, `BOOST`, so Tasker/automation apps can drive the launcher. Document in README.
- As built (ADR-22): no permission, which would shut Tasker out; the receiver acts only while the user has "Commands
  from other apps" on.

## 9.5 Extra transitions, lock-screen live wallpaper
**Dropped by the user, 2026-10-14: not to be built.**
- Ken Burns (slow zoom/pan between transitions), blur-through crossfade, per-source transition; investigate lock-screen live
  wallpaper on API 34+ (`WallpaperManager.setWallpaperComponentWithFlags` is system-only; the user must pick "Lock screen" in the
  system live-wallpaper preview) and document exact steps per OEM in PLATFORM-NOTES W.

## 9.6 Hide navigation buttons (user request, 2026-10-14, ADR-23)
- A switch, Settings → Shizuku → "Hide navigation buttons", that turns full-screen gestures on or off through Fox's
  Shizuku shell service: MIUI's `force_fsg_nav_bar`, else Android's navigation overlay. It shows the system's state,
  read on every resume; Fox stores nothing. The Deep boost screen becomes the "Shizuku" screen holding both uses.
- Open question from the user: hiding them per app (in front). See STATUS for the finding.

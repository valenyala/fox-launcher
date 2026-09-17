# Phase 4 — Wallpaper rotation

**Goal:** wallpapers from user-chosen sources rotate on interval / unlock / gesture with a seamless crossfade, via our own
live-wallpaper engine; a static fallback exists when the live wallpaper is not active.
**Prereqs:** Phase 3 complete (for the NextWallpaper action), Phase 2 (offsets).
**Definition of done:** pick a folder of images → apply Fox live wallpaper → wallpapers crossfade every N minutes and on
unlock and on two-finger swipe up, with no black flash, on home and while in other apps; survives reboot.
**Out of scope:** online sources, Ken Burns, blur (Phase 9).

Read `PLATFORM-NOTES.md` section W before starting. ADR-3 applies.

## 4.1 Sources and entries data layer

- **Do:** Room v3: `WallpaperSourceEntity(id, type[FOLDER_TREE|PICKED_IMAGES|BUILT_IN], uri?, displayName, enabled, needsReauth)`,
  `WallpaperEntryEntity(id, sourceId, uri, displayName, sizeBytes, lastModified, lastShownAt, failedCount)`; DAO with
  `observeEnabledEntries()`, `upsertAll`, `deleteBySource`, `markShown(id, now)`, `markFailed(id)`.
  `feature/wallpaper/source/SourceScanner` (`CoroutineWorker`, Hilt): for FOLDER_TREE, list children via `DocumentsContract`
  child query filtering `image/*` mime, upsert diff (add new, delete gone). Runs on source added and on a daily periodic work.
  Ship 3 built-in wallpapers in `res/raw` as `BUILT_IN` source so the feature works with zero setup.
- **Verify:** unit test for the diff function (pure). Build; schema 3 committed.

## 4.2 WallpaperPlaylist (pure Kotlin) + tests

- **Do:** `WallpaperPlaylist(entries: List<Long>, mode: SEQUENTIAL|SHUFFLE, seed, history: List<Long>)` with `next()`,
  `previous()`, `withEntries(newList)` (keeps position sensibly when entries change), shuffle = no repeats until all shown,
  then reshuffle avoiding immediate repeat. Persist `mode`, `seed`, `history` (last 50) in DataStore.
- **Verify:** tests: sequential wrap, shuffle covers all before repeating, previous after next returns same id, entries removed
  mid-cycle, single entry, empty.
- **Done when:** ≥ 10 tests pass.

## 4.3 FoxWallpaperService crossfade engine

- **Do:**
  - `feature/wallpaper/engine/FoxWallpaperService : WallpaperService`, `Engine` with its own `CoroutineScope` (cancel in `onDestroy`).
    Observes `FoxSettings.activeWallpaperEntryId` (DataStore flow) → on change, `WallpaperLoader.load(uri, surfaceW, surfaceH)`
    on IO (EXIF-aware, sample-size decode, center-crop scale to cover `surfaceW * (1 + parallaxExtra)` × `surfaceH`) → start
    a `Crossfade(from, to, durationMs)` driven by `Choreographer`. Draw path: `lockHardwareCanvas` if available else `lockCanvas`.
    First image after (re)creation draws without animation.
  - `onOffsetsChanged` stores `xOffset`; draw translates by `-xOffset * extraWidth`. `onVisibilityChanged(false)` cancels frame
    callbacks; `(true)` redraws once. `onSurfaceChanged` reloads at the new size. `onComputeColors` from current bitmap.
  - Manifest: service with `BIND_WALLPAPER`, `res/xml/wallpaper.xml` pointing `settingsActivity` to a settings deep link
    (Phase 4.7), thumbnail drawable.
  - `WallpaperLoader` pure parts (`sampleSizeFor(srcW, srcH, dstW, dstH)`, `coverScale(...)`, `cropRect(...)`) get unit tests.
- **Verify:** unit tests; device: apply via system preview (`ACTION_CHANGE_LIVE_WALLPAPER`), change `activeWallpaperEntryId`
  through a temporary settings button → crossfade with no flash; go to another app and back — image persists; reboot — image persists.
- **Done when:** checklist passes.

## 4.4 RotationScheduler and triggers

- **Do:** `feature/wallpaper/RotationScheduler` (`@Singleton`): `suspend fun advance(direction = NEXT)`: loads playlist state,
  picks id, writes `activeWallpaperEntryId` + `markShown`. Triggers:
  - Interval: `PeriodicWorkRequest` (`RotationWorker`, unique name, `ExistingPeriodicWorkPolicy.UPDATE`) for ≥ 15 min; for
    intervals < 15 min the engine runs a `delay` loop while visible calling `advance()` (settings expose 1, 5, 10, 15, 30, 60 min, 3 h, daily).
  - On unlock: runtime `ACTION_USER_PRESENT` receiver registered in `FoxWallpaperService.onCreate`, debounce 5 s.
  - Gesture: `ActionExecutor` `NextWallpaper/PreviousWallpaper` → `advance(NEXT/PREV)` (replace the Phase 3 no-op `WallpaperAdvancer`).
  - Optional "on page change" trigger (setting, default off).
  - Time-of-day schedule (list of HH:mm) via `OneTimeWorkRequest` re-enqueued after each run (stretch; leave a TODO if skipped).
- **Verify:** unit test for `TriggerPolicy` (pure: given settings + last change time + event → should advance?).
- **Done when:** interval + unlock + gesture verified on device.

## 4.5 Static fallback

- **Do:** when `wallpaperManager.wallpaperInfo?.component != ours`, `advance()` additionally enqueues `StaticApplyWorker`
  that decodes to screen size and calls `setStream(FLAG_SYSTEM)`. Settings show a banner "Seamless transitions need the Fox
  live wallpaper — Apply" when not active.
- **Verify:** device: remove live wallpaper → rotation still changes the wallpaper (with a flash, expected).

## 4.6 Lock screen handling

- **Do:** setting "Also change lock screen" (default off): on advance, `setStream(FLAG_LOCK)` with the same image. Investigate
  on the test device whether the live wallpaper can be applied to lock too; document the result in PLATFORM-NOTES W.
- **Done when:** setting works; note written.

## 4.7 Wallpaper settings UI

- **Do:** `feature/settings/WallpaperScreen`: source list (add folder via `OpenDocumentTree`, add pictures via
  `PickMultipleVisualMedia`, toggle, remove, "needs re-authorization" state), thumbnail grid of entries (Coil, tap = set now),
  order mode, interval, triggers toggles, transition duration (300–2000 ms), parallax on/off, lock-screen toggle, "Apply live
  wallpaper" button, "Next now". This screen is also the `settingsActivity` target of `wallpaper.xml` (deep link into the app).
- **Done when:** the whole phase is operable from the UI without adb.

## 4.8 Memory and performance budget

- **Do:** ensure the engine never holds > 2 full-size bitmaps; recycle after crossfade; `onTrimMemory` → drop the next-cache;
  measure with `adb shell dumpsys meminfo dev.foxlauncher` before/after 20 transitions — RSS must not grow. Frame time during
  crossfade < 8 ms at 1080p on a mid-range device (log via `Choreographer` deltas in debug).
- **Done when:** numbers recorded in STATUS.md.

## 4.9 URI permission resilience

- **Do:** catch `SecurityException`/`FileNotFoundException` in loader → `markFailed`; after 3 failures skip the entry; on
  source-level failure set `needsReauth` and show it in settings; `takePersistableUriPermission` on add; check
  `contentResolver.persistedUriPermissions` at scan time.
- **Done when:** revoking the folder permission from system settings degrades gracefully (no crash, banner shown).

## Phase device checklist

1. Add a folder with ≥ 5 images → apply live wallpaper → tap "Next now" 5 times → smooth crossfades, correct crop, no flash.
2. Set interval 15 min, lock phone 16 min → new wallpaper on unlock (unlock trigger off) / interval respected.
3. Enable unlock trigger → each unlock changes wallpaper (debounced).
4. Two-finger swipe up → next wallpaper, also visible when switching to another app.
5. Reboot → same wallpaper as before reboot, rotation continues.
6. Swipe pages → parallax if enabled.

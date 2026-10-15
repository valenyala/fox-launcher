# Phase 5 — Memory widgets & native widget framework

**Goal:** launcher-native widgets on the grid, with three memory widgets that show accurate RAM data and an honest boost.
**Prereqs:** Phase 2 complete (grid). Phase 3 recommended (long-press home menu).
**Definition of done:** user adds a RAM gauge / boost / detail widget from the home long-press menu, resizes it, sees numbers
that match `dumpsys meminfo`, and boost behaves per ADR-5 on the device's API level.

Read `PLATFORM-NOTES.md` section M. ADR-5 and ADR-8 apply.

## 5.1 Native widget framework

- **Do:** `feature/widgets/native/NativeWidgetSpec(kind: String, nameRes, descriptionRes, minSpan: Span, defaultSpan, maxSpan,
  resizable: Boolean, content: @Composable (NativeWidgetScope) -> Unit)`; `NativeWidgetRegistry` (Hilt multibinding `Set<NativeWidgetSpec>`).
  `HomeItem.NativeWidget(kind, spanX, spanY, configJson)`. Grid: renders `content` inside a rounded surface; resize mode on
  long-press → handles on edges → `GridLayoutEngine.isFree` validates; persist span. "Widgets" entry in the home long-press menu
  opens a picker sheet listing native widgets (App widgets tab arrives in Phase 6) with previews; tapping places at `firstFree`.
- **Verify:** unit test for resize validation (pure). Device: place, resize, move, remove a sample "Clock" native widget
  (add a trivial one to prove the framework; keep it, it's useful).

## 5.2 MemoryStats + MemInfoParser tests

- **Do:** `feature/memory/MemInfoParser.parse(text): MemInfo(totalKb, freeKb, availableKb, cachedKb, buffersKb, swapTotalKb, swapFreeKb)`
  pure; `MemoryStats` (`@Singleton`) exposing `fun observe(intervalMs): Flow<MemorySnapshot>` combining `ActivityManager.getMemoryInfo`
  and `/proc/meminfo` on IO, `flowOn(IO)`, `distinctUntilChanged`, only collected while a widget is composed (structured concurrency
  through `collectAsStateWithLifecycle`).
- **Verify:** parser tests with a real `/proc/meminfo` sample (include one from a device in test resources) and a truncated sample.

## 5.3 RAM gauge, boost and detail widgets

- **Do:** three specs: `memory_gauge` (1×1 min, 2×1 default: circular used% + "x.x GB free"), `memory_boost` (1×1: button with
  rocket icon; tap runs boost with a 1.2 s animation and then shows "Freed 123 MB" measured, or "Nothing to free" / on 34+ a
  short "Stats only on Android 14+" caption with an info tap), `memory_detail` (2×2: used/available/cached/swap rows, sparkline
  of the last 60 samples, top recent apps if usage access granted with a "Force stop" deep link to app info). Colors from theme;
  respect label/blur settings.
- **Verify:** Compose UI tests with a fake `MemoryStats` flow rendering expected text.

## 5.4 BoostStrategy per API level

- **Do:** `interface BoostStrategy { val name; suspend fun boost(): BoostResult(freedBytes: Long, killedPackages: Int, note: String?) }`;
  `SelfTrimStrategy` (clear IconCache memory, Coil memory cache, `Runtime.gc()`; always), `LegacyKillStrategy` (API 29–33 per
  PLATFORM-NOTES M with the ADR-5 allowlist; foreground app from `UsageStatsManager` when permitted), `CompositeStrategy` choosing by
  `SDK_INT` = Self + (Legacy if ≤ 33). Measure `availMem` before, run, `delay(500)`, measure after; never report a negative
  number (clamp to 0 and say "Nothing to free"). Allowlist as a pure function with tests. Phase 9 adds a Shizuku strategy in front.
- **Verify:** unit tests for the allowlist and for `CompositeStrategy` selection with an injected `sdkInt`.
- **Device checklist:** on an API ≤ 33 device boost reduces used memory visibly; on 34+ the caption is shown and no false number appears.

## 5.5 (stretch) AppWidgetProvider export

- **Do:** expose `memory_gauge` as a real `AppWidgetProvider` (`RemoteViews`, updated every 30 min + on tap) so users of other
  launchers can use it. Skip if the phase is running long; leave a TODO in STATUS.md.

## 5.6 Widget background opacity (user request, 2026-10-15)

- **Do:** one setting, Home settings → Widget background: how opaque the rounded surface behind Fox's native widgets is,
  0–100% in 5% steps, default 25% (what it always was). `FoxSettings.widgetBackgroundOpacity` → `HomeUiState` →
  `HomeItemView`. A preview Clock over a wallpaper-like backdrop. App widgets draw their own background: untouched.
- **Verify:** UI test of the screen (percentage, 5% snapping). Device: a placed widget follows the slider.

## 5.7 Lock screen widget (user request, 2026-10-15)

- **Do:** `lock_screen`, 1×1, not resizable: a padlock; a tap performs `GLOBAL_ACTION_LOCK_SCREEN` through Fox's
  accessibility service (ADR-4), as the Lock screen gesture does, never device admin. Service off → the disclosure
  dialog; stopped → restarted first when Fox holds the adb grant (ADR-18), else "turn it off and on"
  (`AccessibilityRepair.recover`, shared with the home gestures). The picker preview takes no tap.
- **Verify:** UI test (a tap locks, the preview takes none). Device: tap locks; with the service off the disclosure
  shows and opens the accessibility settings; a long press opens the popup and does not lock.

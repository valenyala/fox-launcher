# Phase 8 — Performance & release

**Goal:** ship-quality build.
**Prereqs:** Phases 0–7 (7 partially acceptable).

## 8.1 Baseline profiles + macrobenchmark
- Add `:baselineprofile` and `:macrobenchmark` modules (the one sanctioned exception to ADR-1) using the AGP baseline-profile plugin;
  journeys: cold start to first frame, open drawer, scroll drawer, swipe pages. Generate on a physical device; commit
  `app/src/main/baseline-prof.txt`. Target: cold start < 500 ms TTID on a mid-range device; record numbers in STATUS.md.

## 8.2 Jank pass
- `adb shell dumpsys gfxinfo dev.foxlauncher framestats` during: drawer scroll, page swipe, folder open, wallpaper crossfade.
  Use Compose compiler reports (`-P plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination`) to find unstable
  parameters in hot composables (page, item, drawer cell) and fix them. Target: < 1% janky frames on drawer scroll.

## 8.3 R8, keep rules, signing
- `isMinifyEnabled = true`, `isShrinkResources = true`, keep rules for: kotlinx.serialization (`@Serializable` classes), Room
  entities are fine, Hilt generated code is fine, `FoxAccessibilityService`, `FoxWallpaperService`, `AppWidgetProvider` (if 5.5),
  `PinRequestActivity`. Verify a release build installs and every feature works. Signing via `keystore.properties` (gitignored)
  read in `app/build.gradle.kts`; document in README.

## 8.4 Battery pass
- Verify: wallpaper engine has zero wakeups while invisible (`adb shell dumpsys batterystats --checkin | grep dev.foxlauncher` after 1 h
  screen-off); WorkManager jobs have `setRequiresBatteryNotLow` where sensible; MemoryStats only polls while a widget is visible;
  accessibility service receives no events.

## 8.5 Play listing and disclosures
- README with features/screenshots; privacy policy (no data collection; accessibility service purpose; usage access purpose; notification
  access purpose); Play data-safety answers; `QUERY_ALL_PACKAGES` declaration text ("launcher"); screenshots; feature graphic; versioning
  scheme (`versionCode` = build number, `versionName` semver).

## 8.6 Crash reporting decision
- Default: none (privacy). If crash reports are wanted, add ACRA with an email/self-hosted sender and an opt-in; record as ADR-11.

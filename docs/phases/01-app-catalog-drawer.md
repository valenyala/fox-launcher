# Phase 1 — App catalog & drawer

**Goal:** Fox is usable as a daily launcher: it lists every launchable app across profiles, searches, launches, and stays
correct as packages come and go.
**Prereqs:** Phase 0 complete.
**Definition of done:** install → set default → swipe up → find any app by scrolling or typing → it opens; install/uninstall an
app while Fox is in the background and the drawer reflects it without restart; rotation never restarts the activity.
**Out of scope:** home grid items, gestures beyond a plain swipe-up, folders.

Read `PLATFORM-NOTES.md` section A before starting.

## 1.1 AppEntry + AppRepository

- **Why:** single source of truth for installed apps used by drawer, home, gestures and search.
- **Do:**
  - `core/model/AppEntry(component: String /*flattened*/, userSerial: Long, label: String, packageName: String,
    isWorkProfile: Boolean, isSuspended: Boolean, isAvailable: Boolean, versionCode: Long)` — `@Immutable data class`.
  - `core/apps/AppRepository` (`@Singleton`): `val apps: StateFlow<ImmutableList<AppEntry>>`, `suspend fun refresh()`,
    `fun launch(entry, sourceBounds: Rect?, opts: Bundle?)`, `fun openAppInfo(entry, sourceBounds)`, `fun uninstall(entry)`.
    Built from `LauncherApps.getActivityList(null, user)` for all `UserManager.userProfiles`, on `@IoDispatcher`.
    Register `LauncherApps.Callback` on a `HandlerThread`; each callback schedules a debounced (250 ms) `refresh()`.
    Sort by label with `Collator`. Keep `UserHandle` in a private map keyed by serial for launching.
  - Pure helper `core/apps/AppSort.kt` (`fun sortEntries(list, collator)`), and `core/apps/AppSearch.kt`
    (`fun match(query, label): Boolean` — case/diacritic-insensitive prefix on any word, then substring) with unit tests.
- **Files:** `core/model/AppEntry.kt`, `core/apps/AppRepository.kt`, `core/apps/AppSort.kt`, `core/apps/AppSearch.kt`, tests.
- **Verify:** unit tests for sort/search; build.
- **Done when:** a temporary log in `HomeViewModel` prints the app count on start (remove it in 1.3).

## 1.2 IconCache

- **Why:** icon loading is the #1 launcher jank source; do it once, off-thread, cached.
- **Do:**
  - `core/apps/IconCache` (`@Singleton`): `suspend fun icon(entry): ImageBitmap`, `fun cached(entry): ImageBitmap?` (memory),
    `suspend fun invalidate(packageName, userSerial)`, `fun clearMemory()`.
  - Memory layer: `LruCache<String, ImageBitmap>` sized to ~48 MB. Disk layer: PNG files under `filesDir/icons/<hash>.png`,
    key = `component|serial|versionCode|iconSizePx|shapeId`. Render `LauncherActivityInfo.getIcon(densityDpi)` into a bitmap
    of `iconSizePx` (from settings, default 56dp at device density); apply adaptive mask using a `Path` from a shape id
    (circle, squircle, rounded square; store the path formulas in `core/ui/IconShapes.kt`). Non-adaptive drawables are
    scaled to fit with 8% padding. Work profile badge via `PackageManager.getUserBadgedIcon`.
  - Invalidate on `onPackageChanged/Added/Removed` (hook from AppRepository) and when icon settings change.
  - Placeholder while loading: a `Color` tile derived from `label.hashCode()`.
- **Files:** `core/apps/IconCache.kt`, `core/ui/IconShapes.kt`, `core/apps/IconRenderer.kt` (pure-ish; the mask math has tests).
- **Verify:** unit test for `IconShapes` path bounds; build; on device, drawer scrolls without frame drops on a mid-range phone.
- **Done when:** second launch of the drawer shows icons instantly (disk hit), verified by log timing < 5 ms per icon.

## 1.3 App drawer screen

- **Why:** the primary browsing surface.
- **Do:**
  - `feature/drawer/DrawerViewModel`: `UiState(query, sections: ImmutableList<Section(letter, apps)>, isLoading)`, functions
    `onQueryChange`, `onAppClick(entry, bounds)`, `onAppLongClick`.
  - `feature/drawer/DrawerScreen`: full-screen translucent scrim over wallpaper (`surface` at 90% alpha with blur off by
    default), search field at top (auto-focus only when opened via search action), `LazyVerticalGrid` (columns from settings,
    default 5), letter headers, an alphabet fast-scroll rail on the right (drag to jump), keyboard dismiss on scroll.
  - Long-press bottom sheet/popup: "App info", "Uninstall" (hidden for system apps), placeholder rows for later phases.
  - Tap → `AppRepository.launch(entry, iconBoundsInWindow, ActivityOptions.makeClipRevealAnimation(...).toBundle())`.
- **Files:** `feature/drawer/*`.
- **Verify:** Compose UI test: given a fake list of 3 apps and query "ma", only matching items are shown.
- **Device checklist:** icons render; fast scroll jumps; typing filters; tap opens app; long-press shows popup; back closes drawer.
- **Done when:** checklist passes.

## 1.4 Minimal home + swipe-up drawer

- **Why:** need a home surface to open the drawer from; gestures proper come in Phase 3, so keep this simple and replaceable.
- **Do:**
  - `feature/home/HomeScreen`: wallpaper shows through; bottom pill "Search apps"; a plain `Modifier.pointerInput` vertical
    drag detector (`detectVerticalDragGestures`) that opens the drawer as a `AnimatedVisibility`/offset-driven overlay
    following the finger (progress 0..1), snapping open past 30% or on upward fling. This detector is explicitly temporary
    and will be replaced by `GestureArena` in 3.3; put a `// TODO(phase3)` on it.
  - `BackHandler(enabled = drawerOpen) { close }`. `onNewIntent` (Home key) closes the drawer.
  - Remove the temporary log from 1.1.
- **Files:** `feature/home/HomeScreen.kt`, `HomeViewModel.kt`, `HomeActivity.kt`.
- **Verify:** build; device: swipe-up opens with finger-follow, Home key closes, back closes.
- **Done when:** checklist passes.

## 1.5 Work profiles

- **Why:** common on real devices; broken work-profile support makes a launcher unusable for those users.
- **Do:** drawer shows a "Work" tab (or section toggle) when `userProfiles.size > 1`; badge icons; "Pause work apps" toggle
  calling `UserManager.requestQuietModeEnabled` (only when default launcher); reflect `onPackagesUnavailable` for quiet mode
  as dimmed items. Handle profile added/removed by listening to `Intent.ACTION_MANAGED_PROFILE_ADDED/REMOVED/AVAILABLE/UNAVAILABLE`
  at runtime and refreshing.
- **Verify:** build; if a work profile is available (TestDPC app can create one), the tab appears and toggle works; else note as
  "not verified on device" in STATUS.md.
- **Done when:** build green and either verified or noted.

## 1.6 Hidden apps

- **Why:** a small but expected feature; also the first structured setting.
- **Do:** `FoxSettings.hiddenApps: Flow<Set<String>>` (set of `component|serial`), popup action "Hide", settings screen
  (`feature/settings/HiddenAppsScreen`) listing all apps with checkboxes. Drawer filters hidden apps unless the query exactly
  matches a hidden app's label (lets the user still find it).
- **Files:** `feature/settings/*`, `core/data/settings/FoxSettings.kt`, drawer.
- **Verify:** unit test for the filter rule (pure function `applyHidden(list, hidden, query)`).
- **Done when:** tests pass; hide/unhide works on device.

## Phase device checklist

1. Fresh install → set default → Home shows wallpaper and pill.
2. Install any app from Play while Fox is in the background → open drawer → it is listed with icon.
3. Uninstall it → it disappears without restarting Fox.
4. Rotate device on home and in drawer → no flash, drawer state kept.
5. Search "yout" finds YouTube-like labels; clearing the search restores the list.
6. Tap an app → opens with a reveal animation from its icon.

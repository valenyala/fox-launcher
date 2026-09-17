# Android launcher platform notes

Facts and traps that are easy to get wrong. Each section is referenced from phase tasks. When something
here contradicts what you remember, trust this file unless a device proves otherwise; then update this file.

## H. HOME activity and manifest

```xml
<activity
    android:name=".HomeActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:excludeFromRecents="true"
    android:clearTaskOnLaunch="true"
    android:resumeWhilePausing="true"
    android:screenOrientation="unspecified"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|locale|layoutDirection|density|fontScale"
    android:theme="@style/Theme.Fox.Home"
    android:windowSoftInputMode="adjustPan">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- `Theme.Fox.Home` must set `android:windowShowWallpaper=true`, `android:windowBackground=@android:color/transparent`,
  `android:windowTranslucentStatus=false`, and use edge-to-edge (`enableEdgeToEdge()` in `onCreate`).
- Home button press while already home arrives in `onNewIntent` (because `singleTask`). Use it to close the drawer /
  scroll to the default page. Do not finish or recreate the activity.
- Becoming default launcher: `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)`, check with
  `roleManager.isRoleHeld(ROLE_HOME)`. For dev: `adb shell cmd package set-home-activity <component>`.
- Uninstalling the current default launcher is blocked by the system; nothing to do.

## A. App catalog

- Use `LauncherApps` (not `PackageManager.queryIntentActivities`): `getActivityList(null, user)` for each user in
  `UserManager.userProfiles`. Register one `LauncherApps.Callback` on a background `Handler`; events:
  `onPackageAdded/Removed/Changed`, `onPackagesAvailable/Unavailable/Suspended/Unsuspended`, `onShortcutsChanged`.
- Launch: `launcherApps.startMainActivity(component, user, sourceBounds, activityOptions.toBundle())`. Do not build
  your own intent; it breaks work-profile apps.
- Labels via `LauncherActivityInfo.label`; sort with `java.text.Collator.getInstance()` so non-Latin labels order correctly.
- Icons: `LauncherActivityInfo.getIcon(densityDpi)` returns an `AdaptiveIconDrawable` on most apps. Render to a bitmap
  once at a fixed px size (icon size × 1.0, no extra padding) with the chosen mask path, cache to disk keyed by
  `component|userSerial|versionCode|iconConfigHash`. Themed (monochrome) icons: `AdaptiveIconDrawable.monochrome` (API 33).
- `UserHandle` is not stable across reboots as an object; persist `UserManager.getSerialNumberForUser(user)` and resolve
  back with `getUserForSerialNumber`.
- App shortcuts (static/dynamic/pinned) need `launcherApps.hasShortcutHostPermission()`, true only while we are the
  default launcher. Query with `ShortcutQuery` flags `FLAG_MATCH_DYNAMIC|MANIFEST|PINNED`; start with
  `launcherApps.startShortcut(pkg, id, sourceBounds, opts, user)`. Icons via `getShortcutIconDrawable`.
- Work profiles: apps for a managed profile show with the briefcase badge (`PackageManager.getUserBadgedIcon`).
  Quiet mode: `UserManager.requestQuietModeEnabled(enabled, user)` — allowed for the default launcher.
- Suspended/unavailable apps: show dimmed, tapping a suspended app should call `launcherApps.startMainActivity` anyway;
  the system shows the suspension dialog.

## G. Gestures and system navigation

- Read thresholds from `ViewConfiguration.get(context)`: `scaledTouchSlop`, `scaledMinimumFlingVelocity`,
  `scaledMaximumFlingVelocity`, `ViewConfiguration.getLongPressTimeout()`, `getDoubleTapTimeout()`.
- In Compose, observe pointer events in `PointerEventPass.Initial` via `awaitPointerEventScope` to see multi-touch before
  children. Consume (`change.consume()`) only when a gesture is recognized that the pager must not also handle
  (vertical, multi-finger, pinch, double-tap). Single-finger horizontal swipes stay unconsumed so `HorizontalPager` pages.
- Do not call `setSystemGestureExclusionRects` for the whole screen; the system caps it at 200dp per edge and ignores
  the rest. We do not need it: the home screen does not use edge swipes.
- Respect `WindowInsets.systemGestures` and `mandatorySystemGestures` for placing the dock and drawer handle; a swipe
  starting inside the bottom mandatory inset belongs to the system (home gesture), never to us.
- Predictive back: `android:enableOnBackInvokedCallback="true"` in `<application>`. Use Compose `BackHandler` only when
  something is open (drawer, folder, popup). With nothing open, do not register a handler; on a launcher, back is a no-op.
- System actions available to `AccessibilityService.performGlobalAction`: `GLOBAL_ACTION_NOTIFICATIONS`,
  `GLOBAL_ACTION_QUICK_SETTINGS`, `GLOBAL_ACTION_LOCK_SCREEN` (28+), `GLOBAL_ACTION_RECENTS`, `GLOBAL_ACTION_BACK`,
  `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_POWER_DIALOG`, `GLOBAL_ACTION_TAKE_SCREENSHOT` (30+),
  `GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS`/`DPAD_*` (33+).
- Accessibility service config (`res/xml/accessibility_service_config.xml`): declare
  `android:accessibilityEventTypes="typeWindowStateChanged"` (the attribute cannot be empty), `android:accessibilityFeedbackType="feedbackGeneric"`,
  `android:canRetrieveWindowContent="false"`, `android:notificationTimeout="1000"`, `android:description="@string/accessibility_service_description"`,
  and no `packageNames`. In `onServiceConnected` set `serviceInfo = serviceInfo.apply { eventTypes = 0 }` so the service receives
  no events at all; `onAccessibilityEvent` and `onInterrupt` stay empty. Set `android:isAccessibilityTool="false"`. The settings
  screen that enables it must show a disclosure ("Fox Launcher uses the accessibility service only to perform system actions such
  as opening notifications when you gesture; it does not read screen content") before opening the system settings.
- The service instance is reachable from the app via a `companion object` holding a `WeakReference` set in
  `onServiceConnected`/`onDestroy`, plus a `MutableStateFlow<Boolean>` for "connected".
- Check enabled state with `AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` comparing
  `resolveInfo.serviceInfo` component to ours. Deep link: `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- Haptics: `View.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS / GESTURE_START / CONFIRM)`; in Compose use
  `LocalHapticFeedback`.

## W. Wallpaper

- Live wallpaper: subclass `WallpaperService`, return an `Engine` from `onCreateEngine()`. Draw with
  `surfaceHolder.lockHardwareCanvas()` (API 26+) / `unlockCanvasAndPost`. Register in manifest with
  `android.service.wallpaper.WallpaperService` action, `BIND_WALLPAPER` permission, and `res/xml/wallpaper.xml`
  (`<wallpaper android:settingsActivity=... android:thumbnail=... android:showMetadataInPreview="true"/>`).
- Apply from the app: `Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(...))`
  which opens the system preview; the user confirms. Detect if ours is active: `wallpaperManager.wallpaperInfo?.component == ours`
  (returns null when a static wallpaper is set).
- `Engine.onVisibilityChanged(false)` → stop all frame scheduling. `Engine.onOffsetsChanged(xOffset, yOffset, xStep, ...)`
  → parallax; the launcher calls `wallpaperManager.setWallpaperOffsets(windowToken, xOffset, 0f)` and
  `setWallpaperOffsetSteps(1f/(pageCount-1), 1f)` as the pager scrolls. Some OEMs never call `onOffsetsChanged`; treat
  the offset as optional.
- Decode at surface size: `ImageDecoder.decodeBitmap(source) { decoder, info, _ -> decoder.setTargetSampleSize(...) }`
  or `BitmapFactory.Options.inSampleSize` from `inJustDecodeBounds`. Use `Bitmap.Config.ARGB_8888` for software canvas
  drawing; hardware bitmaps cannot be drawn to a software canvas but can to `lockHardwareCanvas`. Apply EXIF rotation.
  Keep at most current + next bitmaps; recycle the old one after the crossfade.
- Crossfade: draw current with alpha 255, next with alpha `(t*255)` via `Paint.alpha`, `t` from an `Interpolator`
  over `durationMs`, one frame per `Choreographer.FrameCallback`. Stop callbacks when done.
- `onComputeColors()` (API 27+) should return `WallpaperColors.fromBitmap(current)` and call `notifyColorsChanged()`
  after each transition so the system and our theme pick up colors.
- `WallpaperManager.setStream/setBitmap(..., which = FLAG_SYSTEM | FLAG_LOCK)` needs `SET_WALLPAPER` permission and is
  the non-seamless fallback. It runs a full-size decode in system server; always pass a stream already scaled down.
- Lock-screen live wallpaper support varies by OEM and version (AOSP added a lock-screen live-wallpaper picker path in
  API 34). Treat "live on lock" as best-effort; the reliable option is a static `FLAG_LOCK` image set alongside each advance.
- User folders: `ACTION_OPEN_DOCUMENT_TREE` + `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
  List with `DocumentsContract.buildChildDocumentsUriUsingTree` + query (much faster than `DocumentFile.listFiles()`).
  Persisted permissions can be revoked; handle `SecurityException` by marking the source as needs-reauth.
  Individual images: `ActivityResultContracts.PickMultipleVisualMedia`. `READ_MEDIA_IMAGES` is only needed if we scan
  MediaStore directly; prefer SAF/photo picker and do not request the media permission.
- WorkManager `PeriodicWorkRequest` minimum interval is 15 minutes. For shorter intervals, the engine runs its own
  timer while visible (it is a long-lived process as long as the wallpaper is set). `ACTION_USER_PRESENT` is not
  exempt from implicit-broadcast limits, so register it at runtime inside the service (`registerReceiver` in
  `WallpaperService.onCreate`, unregister in `onDestroy`).
- `ACTION_BOOT_COMPLETED` is exempt and can be a manifest receiver (`RECEIVE_BOOT_COMPLETED`); use it to re-enqueue
  the periodic work only if the user chose interval rotation. WorkManager already persists periodic work across reboot,
  so usually nothing is needed.

## M. Memory

- `ActivityManager.getMemoryInfo(MemoryInfo)` → `totalMem`, `availMem`, `threshold`, `lowMemory`. No permission.
- `/proc/meminfo` is world-readable on all supported versions; fields of interest: `MemTotal`, `MemFree`, `MemAvailable`,
  `Buffers`, `Cached`, `SwapTotal`, `SwapFree`, `Shmem`, `SReclaimable`. Values are in kB. Parse with a pure-Kotlin parser.
- `ActivityManager.getRunningAppProcesses()` returns only our own process since API 22. Do not use it to list others.
- `ActivityManager.killBackgroundProcesses(pkg)`: permission `android.permission.KILL_BACKGROUND_PROCESSES` (normal).
  API ≤ 33: kills that package's background (cached) processes. API 34+: no effect on other packages. See ADR-5.
- Which apps to target on ≤33: iterate our own `AppRepository` packages (launchable, non-system-critical), skip the
  foreground app (from `UsageStatsManager.queryEvents` last `ACTIVITY_RESUMED` if `PACKAGE_USAGE_STATS` granted, otherwise
  skip only ourselves), skip the allowlist in ADR-5, call `killBackgroundProcesses` for each on IO, then re-measure.
- `PACKAGE_USAGE_STATS` is a special permission: check `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)`,
  request via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- `ComponentCallbacks2.onTrimMemory` on the app + clearing `IconCache` memory layer is the "self trim" strategy.
  `System.gc()` is allowed but reports nothing meaningful; do not count it as freed memory.

## AW. App widgets (third-party)

- One `AppWidgetHost(context, HOST_ID)` per process, created in `HomeActivity.onCreate`, `startListening()` in
  `onStart`, `stopListening()` in `onStop`. `HOST_ID` is an arbitrary constant that must never change (ids are keyed by it).
- Allocation: `host.allocateAppWidgetId()` → try `appWidgetManager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, options)`;
  if false, launch `ACTION_APPWIDGET_BIND` with `EXTRA_APPWIDGET_ID`, `EXTRA_APPWIDGET_PROVIDER`, `EXTRA_APPWIDGET_PROVIDER_PROFILE`
  through an `ActivityResultLauncher`. On cancel, `host.deleteAppWidgetId(id)`.
- Configure: if `info.configure != null`, `host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST, null)`;
  `AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL` (API 28) lets us skip it.
- View: `host.createView(context, id, info)` → `AppWidgetHostView`; wrap in `AndroidView`. Call `updateAppWidgetSize(null, minW, minH, maxW, maxH)`
  (dp) whenever the cell size changes; on API 31+ prefer `updateAppWidgetOptions` with `OPTION_APPWIDGET_SIZES`.
- Providers: `appWidgetManager.getInstalledProvidersForProfile(user)` per profile; previews via `loadPreviewImage` or
  `previewLayout` (API 31, inflate with `RemoteViews`). Widgets from the managed profile carry the badge.
- Widget views eat touches; the grid's long-press-to-drag must be detected in the parent (Initial pass) with a delay so the
  widget still gets taps and scrolls. Do not set `AppWidgetHostView.setOnLongClickListener` as the only path.
- Removing a widget: `host.deleteAppWidgetId(id)` then delete the `HomeItem`. Provider uninstalled: `onProvidersChanged`
  / `AppWidgetHost.onProviderChanged`; drop items whose provider no longer resolves.

## S. Broadcasts, background, permissions summary

- Manifest-declared receivers only for exempt broadcasts (`BOOT_COMPLETED`, `PACKAGE_*` with data scheme,
  `LOCALE_CHANGED`, `TIME_SET`). Everything else registers at runtime (`Context.RECEIVER_NOT_EXPORTED` flag on API 33+).
- Permissions used and why (keep this list current; it feeds the Play data-safety form):
  `SET_WALLPAPER` (fallback wallpaper), `KILL_BACKGROUND_PROCESSES` (boost ≤33), `BIND_APPWIDGET` (host, granted to
  default launcher automatically), `BIND_ACCESSIBILITY_SERVICE` (service declaration), `RECEIVE_BOOT_COMPLETED`,
  `PACKAGE_USAGE_STATS` (special, optional), `QUERY_ALL_PACKAGES` (required for launchers; Play accepts launcher use),
  `REQUEST_DELETE_PACKAGES` (uninstall from popup), `VIBRATE`, `POST_NOTIFICATIONS` (only if we ever notify),
  `BIND_NOTIFICATION_LISTENER_SERVICE` (Phase 7 dots).
- Uninstall: `Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).putExtra(Intent.EXTRA_USER, user)`.
- App info: `launcherApps.startAppDetailsActivity(component, user, sourceBounds, opts)`.
- App-open animation: `ActivityOptions.makeClipRevealAnimation(view, x, y, w, h)` or `makeScaleUpAnimation`; pass the
  icon's screen bounds as `sourceBounds` too.

## C. Compose specifics that bite launchers

- Do not put the grid in `LazyVerticalGrid`; items have absolute cells and spans. Use a custom `Layout` per page with
  `placeable.place(cellX*cellW, cellY*cellH)`.
- `HorizontalPager` with `beyondViewportPageCount = 1` so neighbours are composed for drag targets.
- Mark model classes `@Immutable`/`@Stable` or keep them as `data class` of primitives/strings; use `kotlinx.collections.immutable`
  or wrap lists in a `@Immutable` holder to avoid recomposing every icon on any change. Enable Compose strong skipping (default in 2.x).
- `AndroidView` for `AppWidgetHostView` must use `update = {}` sparingly; recreating the view on every recomposition
  breaks widget state. Key it by `appWidgetId`.
- Icons as `ImageBitmap` from the cache; never load a `Drawable` inside composition.

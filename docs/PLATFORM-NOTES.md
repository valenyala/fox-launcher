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
    android:screenOrientation="portrait"
    android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden|navigation|uiMode|locale|layoutDirection|density|fontScale"
    android:theme="@style/Theme.Fox.Home"
    android:windowSoftInputMode="adjustPan"
    tools:ignore="LockedOrientationActivity">
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
- Portrait only (ADR-14). `configChanges` still lists `orientation|screenSize`: coming home from a landscape app is a
  configuration change too, and from targetSdk 36 Android ignores orientation locks on large screens (smallest width
  ≥ 600 dp: tablets, unfolded foldables), where the home must still rotate without restarting.
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
- Default apps (first-run dock, 2.7): the activity that resolves an intent is usually not the app's launcher activity
  (POCO F3: DIAL → `com.google.android.dialer/…main.impl.MainActivity`, launcher icon `.extensions.GoogleDialtactsActivity`;
  STILL_IMAGE_CAMERA → `com.android.camera/.AssistantCamera`, icon `.Camera`). Keep only the package and map it through
  `getLaunchIntentForPackage`. With several handlers and no default, `resolveActivity` returns the system chooser, which is
  not among `queryIntentActivities`; Launcher3 then takes the single system handler. SMS: `Telephony.Sms.getDefaultSmsPackage`.

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
- Predictive back: `android:enableOnBackInvokedCallback="true"` in `<application>`. Use Compose `BackHandler` for what
  is open (drawer, folder, popup, settings). With nothing open, Back must still be **swallowed**: unhandled, Android
  12+ treats Back on a root launcher activity as "move the task to the back", and for the home task that brings the
  last app to the front (seen on fox-api33, 2026-10-12). `HomeActivity` registers an empty
  `onBackPressedDispatcher` callback in `onCreate`, before any Compose handler, so it is always the last resort.
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
- Enabled state: `getEnabledAccessibilityServiceList` lists only services that are running, so it cannot tell "off"
  from "stopped". Fox reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` instead: connected → on, listed but not
  connected → stopped, else off (`AccessibilitySystemActions.status`). Deep link: `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- **A stopped Fox can leave its service stuck** (measured 2026-10-12). When Fox's process is stopped in a way that
  does not restart services, `dumpsys accessibility` shows our service under Enabled *and* Crashed with no Bound
  services, `accessibility_enabled` drops to 0, and the system never starts it again until the user turns it off and
  on. Seen with MIUI's "clear recent apps" (OneKeyClean kills Fox although it is the home app) and after an
  instrumented test run on the emulator. A plain `kill -9` or `adb install -r` recovers by itself on stock API 33.
  The system settings still show the switch on, so Fox tells the user to turn it off and on (`STOPPED`). With the
  opt-in adb grant of WRITE_SECURE_SETTINGS, `AccessibilityRepair` does that itself (ADR-18): it writes the list
  without Fox, waits 500 ms (the system must see the removal; two quick writes coalesce), writes it back with
  `ACCESSIBILITY_ENABLED` = 1, and waits up to 3 s for the service to connect.
- A third stuck state, seen only around instrumented runs: the runner force-stops Fox ("stop due to start instr")
  while the system is still binding the service, which then sits in `dumpsys accessibility`'s "Binding services"
  forever. The system skips anything it is binding, so off and on (by the user or `AccessibilityRepair`) cannot help;
  a package update (`adb install -r`) clears it. On MIUI, `pm grant` of this permission
  also needs Developer options → "USB debugging (Security settings)".
- **MIUI's "clear recent apps" ignores importance** (measured 2026-10-12): OneKeyClean killed processes at oom adj 0,
  100 and 200 alike, including foreground services (Garmin Connect, Camera) and Fox as the home app. No foreground
  service, separate process or other app-side trick helps. Its own lock list does: with Fox locked in Security →
  Boost speed → Lock apps, a clear left Fox's process and its accessibility service running. Fox has no recents card
  to lock (a launcher excludes itself from recents), so that menu is the way; the Gestures screen says so on Xiaomi
  phones (`Build.MANUFACTURER`, which Redmi and POCO also report).
- **Navigation gestures in other apps** (task 3.8, ADR-20; measured on fox-api33/37, 2026-10-13). The strips are
  `TYPE_ACCESSIBILITY_OVERLAY` windows from the service's own `WindowManager`: no extra permission, above apps and the
  system bars. Flags `NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS`, and on API 30+
  `fitInsetsTypes = 0` so the bottom strip lies over the navigation bar, where a swipe up starts. Frames seen
  on a 1080×2400 three-button emulator: bottom `[0,2364][1080,2400]`, sides 31 px wide from y 252 down. A window
  keeps the whole gesture once it has the DOWN, so a 12 dp strip still sees a long swipe. With gesture navigation
  the system takes edge and bottom swipes before the strips' classifier finishes; nothing fires twice.
- `View.isAttachedToWindow` turns true only at the window's first traversal, a frame after `addView`. A hide right
  after a show (the home pauses and resumes around each `onNewIntent`) then skipped the removal and left the strips up
  over the home. Test `view.parent != null` (set inside `addView`), and only put the strips up once the home has stayed
  away for 150 ms.
- The feedback window is a full-screen `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_TOUCHABLE`, whose root view goes
  `INVISIBLE` between gestures; an invisible root hides the window, so it costs no composition. Raw `MotionEvent`
  coordinates are its own (it starts at the display corner, `fitInsetsTypes = 0`). A finger held still sends no
  events, so the Recents hold ring redraws itself (`postInvalidateOnAnimation`) until the hold fires.
- Emulator trap (fox-api33, 2026-10-13): switching the navigation mode with `cmd overlay enable-exclusive` froze the
  display for a rotation-style screenshot, and SurfaceFlinger's RenderEngine never came back from the QEMU GL pipe
  (`rcCreateSyncKHR`); system_server then looped on watchdog restarts and had not recovered after six minutes. The
  same switch had worked twice before on that emulator and once on fox-api37. Restart the emulator when it happens.
- Navigation mode from an app (task 9.6, ADR-23), fox-api37: `Settings.Secure.getInt("navigation_mode")` is readable
  to an app targeting 37 (0 three buttons, 2 gestures). Switching it takes the shell (`cmd overlay enable-exclusive
  --category com.android.internal.systemui.navbar.gestural|threebutton`, ~70 ms), and the overlay change relaunches
  every open activity, the home included, in about 0.5 s (`AppCompatResourceOverlayPolicy … result: true`, then
  "finishDrawing of relaunch"); `configChanges` cannot prevent it. MIUI keeps its own switch, Settings.Global
  `force_fsg_nav_bar` (0 buttons, 1 full-screen gestures), and ignores Android's navigation overlays, but its SystemUI
  answers the setting by toggling its own `com.android.systemui.gesture.line.overlay`: the same assets-path config
  change (`Config changes=80000000`), `CONFIGURATION_CHANGED` to every app, and every open activity relaunched (POCO
  F3, MIUI 14: Fox's home 2.1–2.5 s, 2026-10-14). No navigation mode can change under a running app without
  relaunching it, so hiding the buttons per app in front is out.
- Haptics: `View.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS / GESTURE_START / CONFIRM)`; in Compose use
  `LocalHapticFeedback`.

## W. Wallpaper

- Live wallpaper: subclass `WallpaperService`, return an `Engine` from `onCreateEngine()`. Do **not** draw with
  `surfaceHolder.lockHardwareCanvas()` on MIUI (POCO F3, 2026-10-13): right after the system (re)binds the service
  it swaps the wallpaper's surface (`WallpaperService: Wallpaper has updated the surface`), and on some starts the
  canvas lock's hidden renderer is left with no surface. Every later frame is dropped (`OpenGLRenderer: The surface
  had destroyed before draw`, one per frame) while `Surface.isValid()` stays true and SurfaceFlinger keeps the first
  frame: the wallpaper freezes. Fox draws through its own `HardwareRenderer` + `RenderNode` (public since API 29):
  it binds `holder.surface` again on every surface callback, and when `syncAndDraw()` reports
  `SYNC_LOST_SURFACE_REWARD_IF_FOUND` it binds again and redraws the frame (`WallpaperRenderer`).
  (`Surface.getGenerationId()` would detect the swap but is hidden API.) Register in manifest with
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
- Measured (task 4.6, 2026-10-12). AOSP preview ("Set wallpaper"): API 33 offers *Home screen* / *Home screen and lock
  screen*; API 37 adds *Lock screen*. *Home and lock* leaves no separate lock image, so the keyguard shows the live
  wallpaper itself, crossfades included (fox-api33/37). `setStream(FLAG_LOCK)` gives the lock screen its own still image
  (the home screen is untouched); `setStream(FLAG_SYSTEM)` alone keeps an existing lock image; both flags in one call
  make the two share one image again. Fox's "Also change the lock screen" (off by default) writes `FLAG_LOCK` (plus
  `FLAG_SYSTEM` when the static fallback is on) after each change, in the same worker.
- MIUI 14 (POCO F3): the keyguard draws its own wallpaper window (`MiuiKeyguardPictorialWallpaper`, from
  `com.miui.miwallpaper`). Wallpaper Carousel (`com.miui.android.fashiongallery`) sets the lock wallpaper itself
  (`MiuiWallpaperManagerService setWallpaper which=2 type=gallery`) on screen-on and unlock, overwriting Fox's; with
  Carousel turned off, Fox's `FLAG_LOCK` image shows. Nothing public detects Carousel; the user must turn it off.
  MIUI uses the AOSP live picker (`com.android.wallpaper.livepicker`): *Home screen and lock screen* clears the lock
  image, the keyguard window goes transparent (`no needShowLockWallpaper, change alpha 0`) and Fox's live wallpaper
  shows on the lock screen and follows each change — so live on lock works on MIUI too, with Carousel off.
- User folders: `ACTION_OPEN_DOCUMENT_TREE` + `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
  List with `DocumentsContract.buildChildDocumentsUriUsingTree` + query (much faster than `DocumentFile.listFiles()`).
  Persisted permissions can be revoked; handle `SecurityException` by marking the source as needs-reauth.
  Individual images: `ActivityResultContracts.PickMultipleVisualMedia`. `READ_MEDIA_IMAGES` is only needed if we scan
  MediaStore directly; prefer SAF/photo picker and do not request the media permission.
- Measured (task 4.7, 2026-10-13). Photo picker URIs (`content://media/picker/...`) accept
  `takePersistableUriPermission` on API 33 and 37 (`persisted=0x1` in `dumpsys activity permissions`);
  `releasePersistableUriPermission` drops it to `persisted=0x0` (the activity's temporary grant stays until it goes).
  API 33's picker confirms with "Add (n)", API 37's with "Done". `OpenDocumentTree` refuses the storage root and
  `Download` ("Can't use this folder"); `Pictures/<sub>` works, with an "Allow … to access files" confirmation.
  Persisted grants are capped per app (128 before API 30, 512 since), and every picked picture takes one: prefer
  folders for large sets.
- Lost access, measured (task 4.9, fox-api33, 2026-10-13). Revoking: there is no per-grant switch in Settings; clearing
  the providing app's storage (`pm clear com.android.externalstorage`, Settings → Apps → External Storage) drops every
  grant it ever issued, **to every app**, so never do it on a personal phone. A revoked grant: opening a document
  throws `SecurityException` ("Permission Denial: opening provider …"). A folder moved or deleted under a live grant:
  opening one of its documents throws **`IllegalArgumentException`** ("Failed to determine if … is child of …:
  FileNotFoundException"), not `FileNotFoundException`, and an uncaught one in the engine took the launcher down
  (shared process); listing its children returns an **empty cursor**, not an error, which silently emptied the source.
  Fox now catches the runtime exceptions a provider can send back (`IllegalArgumentException`,
  `IllegalStateException`, `UnsupportedOperationException`) when decoding, looks the folder itself up before
  listing it, guards the engine's coroutines with a logging handler, and leaves a folder that needs picking again
  out of the rotation (built-ins step in when nothing else is left) with a banner in the wallpaper settings.
- Coil 3 reads `android.resource` URIs by resource **id** only (`ResourceUriFetcher` requires an int last segment;
  `.../raw/<name>` throws). Fox stores the built-ins by name (stable across builds) for the engine's
  `ContentResolver`, and hands Coil the resource id for thumbnails (`WallpaperSources.thumbnailModel`).
- The live wallpaper preview shows a "Settings…" button once `wallpaper.xml` has `settingsActivity`; it starts that
  component by name, with no action (ADR-19 for how Fox routes it).
- WorkManager `PeriodicWorkRequest` minimum interval is 15 minutes. For shorter intervals, the engine runs its own
  timer while visible (it is a long-lived process as long as the wallpaper is set). `ACTION_USER_PRESENT` is not
  exempt from implicit-broadcast limits, so register it at runtime. Fox registers it in `FoxApp` (task 4.5): the
  process lives as long as the launcher or the live wallpaper does, so unlock rotation also works with the static
  fallback. Register it **exported**: SystemUI sends `USER_PRESENT` under its own uid, and a `RECEIVER_NOT_EXPORTED`
  receiver silently never gets it (seen on fox-api33, 2026-10-12).
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
- **MIUI ignores `killBackgroundProcesses` from apps** (POCO F3, MIUI 14 / Android 13, 2026-10-13): Fox holds
  `KILL_BACKGROUND_PROCESSES` (`granted=true`), the call returns without error, and nothing is killed, no log either;
  the same call from the shell (`am kill --user 0 <pkg>`) kills the cached process at once ("kill background from
  process"), and `run-as dev.foxlauncher am kill --user 0 <pkg>` (Fox's own uid) does nothing again. So the API ≤ 33
  boost is useless on Xiaomi phones: Fox treats them like Android 14+ (`BoostLimit.MIUI`, "Limited on MIUI").
  Shizuku (Phase 9, ADR-9) runs as the shell and can do it.
- `PACKAGE_USAGE_STATS` is a special permission: check `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)`,
  request via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- `ComponentCallbacks2.onTrimMemory` on the app + clearing `IconCache` memory layer is the "self trim" strategy.
  `System.gc()` is allowed but reports nothing meaningful; do not count it as freed memory.
- Shizuku (task 9.1, ADR-21), verified on fox-api33/37 with Shizuku 13.6.0 from its GitHub release (the Google APIs
  images have no Play Store): start the server with `adb shell <pm path dir>/lib/x86_64/libshizuku.so` after
  `adb unroot` (as root it would run as uid 0, not the shell). The server hands its binder to `ShizukuProvider` when
  an app process starts, so a Fox already running when Shizuku starts learns of it only at its next start (the
  server also restarted Fox for its provider after a force stop). Permission: Shizuku's own dialog, "Allow all the
  time". The user service runs as `dev.foxlauncher:shell` under the shell uid. fox-api37: `cmd activity kill` from it
  logged "Killing …moe.shizuku.privileged.api (adj 700): kill background", which Android 14+ refuses to an app.
  `ShizukuProvider` probes for Sui with a hidden `ServiceManager.getService` unless
  `disableAutomaticSuiInitialization()` runs first (Fox calls it in `attachBaseContext`).

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
- The host keeps one view per id: a second `createView` for the same id (a drag overlay, say) takes the widget's updates
  away from the first. Fox's drag overlay draws a bitmap of the live view instead (task 6.4).
- In Compose, `AndroidView` gets moves after the ancestors' Initial pass, so the home's gesture arena, which claims a
  vertical swipe at touch slop, would always beat a list inside a widget. Fox leaves a gesture alone when, at its
  down, a view under the finger can scroll vertically (`canScrollVertically`); a list that fits keeps the home's swipe.
- `getInstalledProvidersForProfile` and `getAppWidgetInfo` give sizes in px for this display. The provider's
  `providerInfo` (its `ActivityInfo`) is hidden API: read the app label through `LauncherApps.getApplicationInfo`.
- The bind dialog (`AllowBindAppWidgetActivity`) answers `RESULT_OK` on Create; "Always allow" makes
  `bindAppWidgetIdIfAllowed` succeed from then on. The configuration activity answers through `onActivityResult` only
  (`startAppWidgetConfigureActivityForResult` uses an `IntentSender` Fox never sees).
- Emulators: Google Calendar's schedule widget and Gmail's want a signed-in account, so no scrolling widget there.
- MIUI (POCO F3, 2026-10-14): the clock, weather and other "MIUI widgets" on MIUI's home are not app widgets; none of
  the 168 providers `dumpsys appwidget` lists is the Clock app's. No other launcher can host them; Fox's native Clock
  stands in.
- The home must not pad by `safeDrawing`: it includes the keyboard, so a search field over the home (widget picker)
  squeezed the grid and resized every app widget while typing. Pad by `systemBars ∪ displayCutout`.

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

## P. Performance facts (measured)

- Measure release-like builds only (`profiling` build type); debug Compose is several times slower.
- MIUI (POCO F3, MIUI 14) runs third-party apps at 60 Hz even with the panel set to 120 Hz. An app can ask with
  `WindowManager.LayoutParams.preferredDisplayModeId` (a same-resolution mode). The system's
  `PRIORITY_USER_SETTING_PEAK_REFRESH_RATE` vote still wins: MIUI lowers it on its own (heat, its "smart" policy).
  Check with `adb shell dumpsys display | grep PEAK_REFRESH` and the `FrameInterval` column of `gfxinfo framestats`.
- `dumpsys gfxinfo <pkg> framestats` keeps only the last 120 frames: one second at 120 Hz.
- A full-screen `graphicsLayer { alpha = … }` copies the layer off screen every frame by default;
  `CompositingStrategy.ModulateAlpha` avoids it when the content barely overlaps.
- When the window turns invisible (any app in front), HWUI drops the display lists of everything in its render tree,
  and Compose re-records each one before the first frame back. Anything kept composed but hidden (the drawer) must
  be out of that tree: give it its own layer and do not draw that layer while hidden. Compose still records a changed
  layer that nothing draws, so showing it again is cheap.
- MIUI swaps most of a backgrounded launcher out (after a few minutes in Settings: 28 MB PSS plus 21 MB in swap, against
  about 141 MB in front) and evicts its code pages. The first frame after a long absence pays page faults
  (`filemap_fault` in a trace) that no app-side change avoids.

- Wallpaper engine (POCO F3, 2026-10-13, task 4.8): a crossfade of two 1080×2400 bitmaps through Fox's own
  `HardwareRenderer` costs 1.1–1.8 ms of main-thread work per frame (record + `syncAndDraw`), worst 8.7 ms, no missed
  vsync; MIUI drives the wallpaper at 60 Hz even while the launcher window runs at 120. The first fade after a start
  is slower (texture uploads): measure after a warm-up. `run-as <pkg> kill -10 <pid>` makes a debuggable app collect
  garbage (ART logs "SIGUSR1 forcing GC"), so meminfo shows what stays.

## C. Compose specifics that bite launchers

- Do not put the grid in `LazyVerticalGrid`; items have absolute cells and spans. Use a custom `Layout` per page with
  `placeable.place(cellX*cellW, cellY*cellH)`.
- `HorizontalPager` with `beyondViewportPageCount = 1` so neighbours are composed for drag targets.
- Mark model classes `@Immutable`/`@Stable` or keep them as `data class` of primitives/strings; use `kotlinx.collections.immutable`
  or wrap lists in a `@Immutable` holder to avoid recomposing every icon on any change. Enable Compose strong skipping (default in 2.x).
- `AndroidView` for `AppWidgetHostView` must use `update = {}` sparingly; recreating the view on every recomposition
  breaks widget state. Key it by `appWidgetId`.
- Icons as `ImageBitmap` from the cache; never load a `Drawable` inside composition.

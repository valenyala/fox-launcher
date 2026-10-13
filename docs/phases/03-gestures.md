# Phase 3 — Gesture engine

**Goal:** every home-screen gesture is recognized by a tested classifier and remappable to an action, including system
actions through our accessibility service.
**Prereqs:** Phase 2 complete.
**Definition of done:** the gesture settings screen lists all gestures; each can be set to any action; defaults work out of
the box; system actions work once the service is enabled and prompt cleanly when it is not; classifier tests are exhaustive.
**Out of scope:** per-app gestures, macros (Phase 9). Navigation gestures in other apps came later, as 3.8.

Read `PLATFORM-NOTES.md` section G before starting. `DECISIONS.md` ADR-4 and ADR-10 apply.

## 3.1 Gesture and action model + defaults

- **Do:**
  - `core/model/GestureType` enum: `SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT` (single-finger; left/right are only
    reported when the pager cannot page, i.e. at first/last page — see 3.3), `SWIPE_UP_2F, SWIPE_DOWN_2F, SWIPE_LEFT_2F,
    SWIPE_RIGHT_2F, DOUBLE_TAP, LONG_PRESS_EMPTY, PINCH_IN, PINCH_OUT, DOCK_SWIPE_UP, DOCK_SWIPE_DOWN, HOME_KEY`.
  - `core/model/GestureAction` sealed + `@Serializable`: `None, OpenDrawer, OpenSearch, Notifications, QuickSettings, LockScreen,
    Recents, PowerMenu, Screenshot, NextWallpaper, PreviousWallpaper, HomeSettings, PageOverview, OpenApp(component, serial),
    OpenShortcut(pkg, id, serial), ScrollToDefaultPage`.
  - Defaults: `SWIPE_UP→OpenDrawer, SWIPE_DOWN→Notifications, SWIPE_DOWN_2F→QuickSettings, DOUBLE_TAP→LockScreen,
    LONG_PRESS_EMPTY→HomeSettings, PINCH_IN→PageOverview, SWIPE_UP_2F→NextWallpaper, DOCK_SWIPE_UP→OpenDrawer, HOME_KEY→ScrollToDefaultPage`,
    rest `None`.
  - `FoxSettings.gestureMap: Flow<Map<GestureType, GestureAction>>` stored as one JSON string; `setGesture(type, action)`.
  - `GestureAction.requiresAccessibility: Boolean` property.
- **Verify:** unit test: JSON round-trip of the full default map; unknown action in stored JSON falls back to `None` (forward compat:
  use `ignoreUnknownKeys` and a `classDiscriminator`).
- **Done when:** tests pass.

## 3.2 GestureClassifier + tests (pure Kotlin)

- **Do:**
  - Input: `PointerSample(pointerId: Int, x: Float, y: Float, timeMs: Long, phase: DOWN|MOVE|UP|CANCEL)`; config
    `GestureConfig(touchSlopPx, minFlingVelocityPxPerS, longPressMs, doubleTapMs, doubleTapSlopPx, pinchThresholdPx, dockTopPx, pageCanScrollLeft/Right: () -> Boolean)`.
  - Output via `fun feed(sample): GestureResult` where `GestureResult` is `None | Recognized(type, consumePointer=true) | LongPressPending | Cancelled`,
    plus `fun onTimer(nowMs)` for long-press/double-tap timeouts (the composable drives timers with `withFrameMillis`/`delay`).
  - Rules: swipe = displacement > slop and velocity > minFling in the dominant axis on UP, or displacement > 25% of the arena
    height/width; two-finger variants when two pointers were down simultaneously for the swipe; pinch when the inter-pointer
    distance changes by > pinchThreshold with mostly opposite motion; double-tap = two taps within doubleTapMs & doubleTapSlopPx;
    long-press = no movement past slop for longPressMs; dock variants when the DOWN y ≥ dockTopPx. A single-finger horizontal
    swipe is `None` (leave it to the pager) unless `pageCanScroll*` returns false for that direction.
  - Velocity: implement a small `VelocityTracker` (last ~100 ms window, least-squares or simple delta) in pure Kotlin.
- **Files:** `feature/gestures/classifier/*` (no Android imports), `test/.../GestureClassifierTest.kt`, `VelocityTrackerTest.kt`.
- **Verify:** tests for: each single swipe; each two-finger swipe (fingers down within 60 ms); slow drag under fling velocity but past
  25% → recognized; double-tap timing boundaries (just under/over); single tap → None; long-press with jitter < slop → recognized,
  with jitter > slop → cancelled; pinch in/out; three fingers → None; CANCEL resets; dock region variants; horizontal single-finger
  at middle page → None, at last page swiping left → SWIPE_LEFT.
- **Done when:** ≥ 25 tests pass.

## 3.3 GestureArena composable

- **Do:** `feature/gestures/GestureArena(modifier, config, onGesture, content)` wraps the home content. In `pointerInput` use
  `awaitPointerEventScope` with `PointerEventPass.Initial`, convert changes to `PointerSample`s, feed the classifier; when
  `Recognized(consume=true)`, consume all current changes so children (pager, items) do not also react; run the long-press timer
  with a coroutine started on DOWN and cancelled on movement/UP. Replace the temporary detector from 1.4. Item long-press-drag
  (2.4) must still work: the arena reports `LONG_PRESS_EMPTY` only when the DOWN was not on an item (the page reports hit-test
  results through a `LocalHitTester` or by the arena receiving `isOnItem(x,y)`).
- **Verify:** Compose UI test using `performTouchInput { swipeUp() }` on a fake content asserting `onGesture(SWIPE_UP)`; and
  `swipeLeft()` on a 2-page pager still pages.
- **Device checklist:** all default gestures fire; pager still pages; item drag still works; two-finger swipe does not page.
- **Done when:** checklist passes.

## 3.4 FoxAccessibilityService + ActionExecutor

- **Do:**
  - `feature/gestures/system/FoxAccessibilityService` per PLATFORM-NOTES G: no events, `companion` with `instance: WeakReference`
    and `connected: StateFlow<Boolean>`; `fun perform(action: Int): Boolean`.
  - `feature/gestures/ActionExecutor` (scoped to Activity via Hilt `@ActivityScoped`): `fun execute(action, context: ExecContext)`
    where `ExecContext` carries lambdas the home screen provides (`openDrawer`, `openSearch`, `scrollToDefault`, `openPageOverview`,
    `openHomeSettings`) so the executor stays UI-agnostic. System actions: if service not connected → emit
    `NeedsAccessibility` event → UI shows the disclosure dialog with "Open settings" (`ACTION_ACCESSIBILITY_SETTINGS`).
  - `NextWallpaper/PreviousWallpaper` call `RotationScheduler` (Phase 4). Until Phase 4 exists, inject an interface
    `WallpaperAdvancer` with a no-op implementation and a `TODO(phase4)`.
  - Service declaration in manifest + `res/xml/accessibility_service_config.xml` + description string used by the system settings.
- **Verify:** build; `adb shell settings put secure enabled_accessibility_services ...` then swipe down → shade opens; double-tap locks.
- **Done when:** notifications, quick settings, lock, recents, power menu, screenshot all verified on a device.

## 3.5 Gesture settings UI

- **Do:** `feature/settings/GesturesScreen`: list of `GestureType` with a human name and current action; tapping opens an
  action picker (list of actions; `OpenApp` opens an app chooser reusing the drawer list; `OpenShortcut` lists shortcuts of a
  chosen app). Actions that need accessibility show a small shield icon and the status. "Reset to defaults".
- **Verify:** Compose UI test: choosing `LockScreen` for `DOUBLE_TAP` writes to a fake settings store.
- **Done when:** remapping works on device.

## 3.6 System gesture coexistence, predictive back, home intent

- **Do:** apply `WindowInsets.systemGestures` padding to the arena's bottom so the system home swipe is not misread as
  `DOCK_SWIPE_UP` (starts inside mandatory inset → ignore); `BackHandler` only when drawer/folder/overview open; `HOME_KEY`
  routed from `onNewIntent` through the same `ActionExecutor`. Verify with 3-button nav and gesture nav.
- **Done when:** verified in both navigation modes on a device (or two emulators).

## 3.7 Drag-follow drawer opening

- **Do:** `SWIPE_UP`/`DOCK_SWIPE_UP` mapped to `OpenDrawer` should animate with the finger (as 1.4 did) — the classifier reports
  a provisional `VerticalDragStarted` after slop so the arena can drive the drawer offset, then commits/cancels on UP based on
  velocity/threshold. Extend `GestureResult` with `DragUpdate(dy)` / `DragEnd(velocity)`. Other vertical actions (Notifications)
  fire on UP only.
- **Verify:** classifier tests for the drag lifecycle.
- **Done when:** the drawer follows the finger and snaps correctly.

## 3.8 Navigation gestures in other apps (added at the user's request, 2026-10-13; ADR-20)

- **Why:** the user wants full-screen navigation in other apps: swipe in from the left or right edge = Back, swipe up
  from the bottom = Home, swipe up and hold = Recents.
- **Do:**
  - `feature/gestures/navigation/NavGestureClassifier` (pure): per edge, Back on a mostly horizontal swipe inwards past
    a distance or fast; Home on a swipe up within 45°, same rule; Recents when a bottom swipe past the Home distance
    holds within slop for `holdMs` (fires while the finger is down; the lift then does nothing). One finger only.
  - `NavigationOverlay`: three `TYPE_ACCESSIBILITY_OVERLAY` strips added by `FoxAccessibilityService` (PLATFORM-NOTES G).
  - `NavigationGestures`: strips up only while the setting is on, Fox's home is not in front (`HomeVisibility`, fed
    by `HomeActivity.onResume/onPause`) and the screen is on and unlocked.
  - Switch "Navigation gestures in apps" in the Gestures settings (default off); service description and disclosure
    mention the strips.
- **Verify:** `NavGestureClassifierTest`; `GesturesContentTest` (switch); three-button emulator: Back from both
  sides, Home, swipe-and-hold Recents, strips gone on home and on the lock screen; gesture navigation: no double Back.
- **Device checklist:** on the POCO F3 (three-button): Back from both edges, Home, Recents in several apps; edge taps
  and the keyboard still usable; nothing on the home screen or the lock screen.
- **Done when:** the device checklist passes.

## Phase device checklist

1. Every default gesture performs its action.
2. With the service disabled, swipe down shows the disclosure and deep-links to settings; after enabling, it works without restart.
3. Remap double-tap to "Open app: Camera" → works.
4. Gesture nav: system home/back swipes still work; 3-button nav: Home key scrolls to default page.
5. Item drag, folder open, pager paging are unaffected.

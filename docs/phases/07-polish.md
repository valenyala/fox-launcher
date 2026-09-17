# Phase 7 — Polish & personalization

**Goal:** the features users expect from a mature launcher. Tasks are independent; do them in any order.
**Prereqs:** Phases 0–6.

## 7.1 Icon packs and icon options
- Discover packs: query intents `org.adw.launcher.THEMES`, `com.novalauncher.THEME`, `org.adw.launcher.icons.ACTION_PICK_ICON`;
  parse the pack's `appfilter.xml` (`<item component="ComponentInfo{pkg/cls}" drawable="name"/>`) from its resources; map
  drawable names to resource ids; fall back to the pack's `iconback/iconmask/iconupon/scale` for unmapped apps (optional).
  Settings: pack picker, icon shape, icon size (48–64 dp), label on/off, themed icons toggle (API 33 monochrome via `AdaptiveIconDrawable.monochrome`).
  Pure parser with tests. IconCache key must include pack + options.

## 7.2 Dynamic color and theming
- Material You (`dynamicColorScheme`) with fallback palette from `WallpaperColors` (our engine's `onComputeColors` + `WallpaperManager.addOnColorsChangedListener`).
  Light/dark/system; drawer/folder/widget surface opacity and blur (`Modifier.blur` is expensive; make it a setting, default off).

## 7.3 Notification dots
- `NotificationListenerService` (`FoxNotificationListener`), permission flow via `ACTION_NOTIFICATION_LISTENER_SETTINGS` with
  disclosure. Map `StatusBarNotification.packageName + user` → count; expose `Flow<Map<AppKey, Int>>`; draw a dot on icons; long-press
  popup shows up to 3 notification titles (optional).

## 7.4 Animations
- App open/close reveal; drawer/folder open with spring; page indicator; icon press scale; wallpaper-aware scrim. Keep all durations
  in one `FoxMotion` object; respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (reduce motion).

## 7.5 Pin requests (shortcuts, widgets)
- Handle `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` / `ACTION_CONFIRM_PIN_APPWIDGET` in a small transparent `PinRequestActivity`
  using `launcherApps.getPinItemRequest(intent)` → confirmation sheet → `request.accept()` → insert item at first free cell.
  Requires manifest `<activity>` with the two intent filters.

## 7.6 Backup / restore
- Export `HomeLayout` + settings + gesture map + wallpaper sources (URIs may not survive; export display names and flag them) to a
  JSON file via `CreateDocument`; import with validation and a preview; version the format.

## 7.7 Unified search
- Drawer search extended with: settings shortcuts (our own screens), contacts (runtime `READ_CONTACTS`, optional), calculator
  (pure evaluator), web search fallback (`Intent.ACTION_WEB_SEARCH`). Pure ranking function with tests.

## 7.8 Accessibility
- Content descriptions on all icons/widgets; every drag-and-drop action available through the long-press popup ("Move to page…",
  "Move to dock"); TalkBack pass on home, drawer, settings; minimum touch targets 48 dp; `semantics { role }` set for items.

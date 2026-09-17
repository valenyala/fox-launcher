# Phase 0 — Toolchain & scaffold

**Goal:** a building, installable, empty launcher that can be set as default, with DI, tests and CI wired.
**Prereqs:** none.
**Definition of done:** `./gradlew :app:testDebugUnitTest :app:lintDebug assembleDebug` green inside `nix develop`;
on a device the app appears in the default-launcher chooser, becomes default, and pressing Home shows it.
**Out of scope:** any real launcher feature.

Read `PLATFORM-NOTES.md` section H before 0.3.

## 0.1 Nix dev shell

- **Why:** the host has no JDK/SDK. Everything downstream needs a reproducible shell.
- **Do:** create `flake.nix` and `.envrc` (`use flake`). Starting point (unverified; fix package names/versions against
  the nixpkgs you pin):

  ```nix
  {
    description = "Fox Launcher dev shell";
    inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    outputs = { self, nixpkgs }:
      let
        system = "x86_64-linux";
        pkgs = import nixpkgs {
          inherit system;
          config = { allowUnfree = true; android_sdk.accept_license = true; };
        };
        buildToolsVersion = "36.0.0";
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "36" ];
          buildToolsVersions = [ buildToolsVersion ];
          includeEmulator = false;      # set true + includeSystemImages/systemImageTypes/abiVersions for an emulator
          includeNDK = false;
          includeSources = false;
        };
        sdk = "${android.androidsdk}/libexec/android-sdk";
      in {
        devShells.${system}.default = pkgs.mkShell {
          packages = [ pkgs.jdk17 android.androidsdk pkgs.gradle pkgs.android-tools ];
          ANDROID_HOME = sdk;
          ANDROID_SDK_ROOT = sdk;
          JAVA_HOME = pkgs.jdk17;
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";
        };
      };
  }
  ```

  Commit `flake.lock`. Add `.envrc` with `use flake`. If `composeAndroidPackages` rejects the platform/build-tools
  version, list what the pinned nixpkgs offers (`nix eval nixpkgs#androidenv.androidPkgs.platforms` is not stable; simplest is
  to try `"35"`/`"35.0.0"` and step up) and align `compileSdk`/`buildToolsVersion` in 0.2 with it.
- **Files:** `flake.nix`, `flake.lock`, `.envrc`.
- **Verify:** `nix develop -c java -version` prints 17.x; `nix develop -c sh -c 'ls $ANDROID_HOME/platforms'` shows `android-36`;
  `nix develop -c adb version` works.
- **Done when:** the three commands succeed and `flake.lock` is committed.

## 0.2 Gradle project skeleton

- **Why:** everything else is code inside this build.
- **Do:**
  - Inside the shell run `gradle wrapper --gradle-version <latest 8.x or 9.x that the AGP you pick supports>`; commit
    `gradlew`, `gradlew.bat`, `gradle/wrapper/*` including the jar.
  - `settings.gradle.kts` with `pluginManagement` (google, mavenCentral, gradlePluginPortal), `dependencyResolutionManagement`
    with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, `rootProject.name = "fox-launcher"`, `include(":app")`.
  - `gradle/libs.versions.toml` with **looked-up current stable** versions of: AGP, Kotlin, KSP (must match Kotlin), Compose BOM,
    compose-compiler plugin (`org.jetbrains.kotlin.plugin.compose`), activity-compose, lifecycle (runtime-compose, viewmodel-compose),
    navigation-compose, Hilt + hilt-navigation-compose, Room (runtime, ktx, compiler), DataStore preferences, WorkManager ktx,
    hilt-work, Coil compose, kotlinx-serialization-json + plugin, kotlinx-coroutines (android, test), junit4, kotlin-test,
    androidx.test (runner, rules, junit), compose ui-test-junit4 + ui-test-manifest, kotlinx-collections-immutable.
    Do not guess versions; open the Maven Central / Google Maven pages and copy. Record each in `DECISIONS.md` dependency log.
  - Root `build.gradle.kts` declaring plugins with `apply false`.
  - `app/build.gradle.kts`: `namespace = "dev.foxlauncher"`, `applicationId = "dev.foxlauncher"`, `minSdk = 29`,
    `compileSdk`/`targetSdk` = platform from 0.1, `versionCode = 1`, `versionName = "0.1.0"`, Java 17 toolchain,
    `buildFeatures { compose = true }`, `testOptions.unitTests.isReturnDefaultValues = true`, lint `abortOnError = true`,
    `warningsAsErrors = false`, `checkDependencies = true`, Room `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`,
    `kotlinOptions`/`compilerOptions` `-Xcontext-receivers` not needed; keep defaults.
  - `gradle.properties`: `org.gradle.jvmargs=-Xmx4g -XX:+UseParallelGC`, `org.gradle.caching=true`,
    `org.gradle.configuration-cache=true`, `android.useAndroidX=true`, `kotlin.code.style=official`,
    `android.nonTransitiveRClass=true`.
  - Minimal `AndroidManifest.xml` with `<application android:name=".FoxApp" android:allowBackup="false"
    android:enableOnBackInvokedCallback="true" ...>` and no activity yet (added in 0.3).
  - `proguard-rules.pro` empty placeholder; `release` build type with `isMinifyEnabled = true` (rules filled in Phase 8),
    `debug` with `applicationIdSuffix = ".debug"`? **No** — the accessibility/wallpaper component names in docs assume the
    plain id. Keep a single applicationId.
- **Files:** as listed.
- **Verify:** `./gradlew assembleDebug` succeeds; `./gradlew :app:lintDebug` succeeds.
- **Done when:** APK at `app/build/outputs/apk/debug/app-debug.apk` exists.

## 0.3 HOME activity, theme, default-launcher prompt

- **Why:** the launcher must be selectable as Home before anything else is testable.
- **Do:**
  - `HomeActivity` as in PLATFORM-NOTES H, `enableEdgeToEdge()`, `setContent { FoxTheme { HomeRoot() } }`.
  - `res/values/themes.xml`: `Theme.Fox.Home` parent `android:Theme.Material.NoActionBar` (or `Theme.Material3.DayNight.NoActionBar`
    from the material library if added) with `windowShowWallpaper=true`, transparent `windowBackground`,
    `windowLayoutInDisplayCutoutMode=shortEdges`, `windowLightStatusBar=false`.
  - `core/ui/theme`: `FoxTheme` using Material 3 `dynamicDarkColorScheme`/`dynamicLightColorScheme` (API 31+) else static.
  - `HomeRoot()` shows, for now, a centered button "Set as default launcher" when `!roleManager.isRoleHeld(ROLE_HOME)`, launching
    the role request via `rememberLauncherForActivityResult(StartActivityForResult())`. When held, show nothing (wallpaper only)
    and a small "Fox" label at the bottom.
  - `onNewIntent` override storing the intent; nothing else yet.
  - Launcher icon: adaptive icon with a simple fox-orange background and a foreground vector (any simple shape is fine).
- **Files:** `app/src/main/java/dev/foxlauncher/HomeActivity.kt`, `FoxApp.kt`, `core/ui/theme/*`, `res/values/themes.xml`,
  `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_*.xml`, manifest.
- **Verify:** build; on device `./gradlew installDebug`, open app, tap button, choose Fox in the dialog, press Home.
- **Device checklist:** wallpaper is visible through the window; rotating the device does not flash/recreate; Home key
  while in another app returns to Fox.
- **Done when:** the checklist passes on one device (record it in STATUS.md) or, without a device, the build is green and
  the manifest matches PLATFORM-NOTES H exactly.

## 0.4 Base libraries and DI

- **Why:** later tasks assume Hilt, Room, DataStore, WorkManager and Coil are already wired.
- **Do:**
  - `@HiltAndroidApp FoxApp` implementing `Configuration.Provider` for Hilt-WorkManager (`HiltWorkerFactory`), and
    `ImageLoaderFactory` for Coil (disk cache 64 MB, memory cache 15% of app heap).
  - `core/common/Dispatchers.kt`: `@Qualifier` annotations `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` and a
    Hilt module providing them. `core/common/Log.kt`: object `FoxLog` wrapping `android.util.Log` with tag `FoxLauncher`,
    `d/i/w/e` no-ops for `d` in release (`BuildConfig.DEBUG`).
  - `core/data/db/FoxDatabase` (Room, version 1, empty entity list is not allowed → include a placeholder `KeyValue(key, value)`
    entity that Phase 2 can keep or drop with a migration). `exportSchema = true`.
  - `core/data/settings/FoxSettings` around `DataStore<Preferences>` (`preferencesDataStore(name = "settings")`) exposing
    typed `Flow`s and `suspend set` functions; start with `isFirstRun: Boolean`.
  - `@AndroidEntryPoint HomeActivity`; a `HomeViewModel` injected with `FoxSettings` proving the graph compiles.
- **Verify:** `./gradlew assembleDebug` (KSP + Hilt succeed); app starts (no `Hilt` runtime error) on device if available.
- **Done when:** graph compiles and `app/schemas/dev.foxlauncher.core.data.db.FoxDatabase/1.json` is committed.

## 0.5 Test infrastructure

- **Why:** every later phase requires unit tests; set up once.
- **Do:** `testImplementation` junit4, kotlin-test, coroutines-test, turbine (add to catalog); `androidTestImplementation`
  compose ui-test-junit4, androidx.test runner/rules; `debugImplementation` ui-test-manifest. Set
  `testInstrumentationRunner = "dagger.hilt.android.testing.HiltTestRunner"`? Not yet — use the default AndroidJUnitRunner
  until an instrumented test needs Hilt. Add `core/common/MainDispatcherRule.kt` (test source) that installs
  `UnconfinedTestDispatcher` via `Dispatchers.setMain`. Add one real unit test (`FoxSettingsDefaultsTest` can be pure if
  defaults are constants; otherwise test a tiny pure helper you create such as `Collation.compareLabels`) and one Compose
  UI test asserting the "Set as default launcher" button exists when role is not held (inject the state, do not call `RoleManager`).
- **Verify:** `./gradlew :app:testDebugUnitTest` runs ≥1 test and passes. `./gradlew :app:compileDebugAndroidTestKotlin` succeeds.
- **Done when:** both pass; report at `app/build/reports/tests/testDebugUnitTest/index.html`.

## 0.6 CI workflow

> **Superseded by ADR-13.** The GitHub Actions workflow below was built and then replaced by a local
> pre-commit gate (`scripts/ci.sh` + `.githooks/pre-commit`). Do not recreate `.github/workflows/ci.yml`
> without a new ADR.

- **Why:** future sessions without a device still get an independent green/red signal.
- **Do:** `.github/workflows/ci.yml`: on push/PR; `ubuntu-latest`; `actions/checkout`, `actions/setup-java` (temurin 17),
  `android-actions/setup-android` (or rely on the preinstalled SDK on the runner and set `ANDROID_HOME`),
  `gradle/actions/setup-gradle` with caching, then `./gradlew check assembleDebug --no-daemon`. Upload
  `app/build/outputs/apk/debug/*.apk` and lint/test reports as artifacts.
- **Verify:** `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"` (use `uv run --with pyyaml` on this
  host) parses; if a GitHub remote exists, the first run is green.
- **Done when:** the file exists and parses; note in STATUS.md whether a remote run was observed.

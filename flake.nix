{
  description = "Fox Launcher dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      platformVersion = "37";
      buildToolsVersion = "37.0.0";

      composeSdk = extra: pkgs.androidenv.composeAndroidPackages ({
        platformVersions = [ platformVersion ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = false;
        includeNDK = false;
        includeSources = false;
      } // extra);

      android = composeSdk { };

      # Emulator SDK: the default one plus the emulator and x86_64 Google APIs images for API 29 (minSdk),
      # 33 (the POCO F3; last level where boost can kill other apps, ADR-5) and 37 (target). System images
      # come per platform version, and the image for 37 is keyed "37.0". About 5 GB, so only `.#emulator` has it.
      androidEmu = composeSdk {
        platformVersions = [ "29" "33" "37.0" ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
      };

      jdk = pkgs.jdk17;

      mkDevShell = android:
        let sdk = "${android.androidsdk}/libexec/android-sdk";
        in pkgs.mkShell {
          packages = [
            jdk
            android.androidsdk
            pkgs.gradle
            pkgs.android-tools
          ];

          ANDROID_HOME = sdk;
          ANDROID_SDK_ROOT = sdk;
          JAVA_HOME = jdk;

          # NixOS ships a patched aapt2; AGP would otherwise unpack a non-runnable one from Maven.
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";

          # ADR-13: CI is a local pre-commit hook, so point git at the tracked hooks dir.
          shellHook = ''
            if [ -d .git ] && [ "$(git config --get core.hooksPath || true)" != ".githooks" ]; then
              git config core.hooksPath .githooks
              echo "fox: enabled the pre-commit gate (.githooks)"
            fi
          '';
        };
    in
    {
      devShells.${system} = {
        default = mkDevShell android;
        # `nix develop .#emulator`: everything in the default shell, plus `emulator` and `avdmanager` on PATH.
        emulator = mkDevShell androidEmu;
      };

      formatter.${system} = pkgs.nixpkgs-fmt;
    };
}
